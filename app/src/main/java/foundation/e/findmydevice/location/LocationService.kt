package foundation.e.findmydevice.location

import android.app.Service
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import foundation.e.findmydevice.util.NotificationHelper
import foundation.e.findmydevice.util.SmsSender

/**
 * LocationService
 *
 * This class is a Service responsible for obtaining the device's current location and
 * notifying the application when the location has been determined. It runs as a
 * foreground service to ensure continuous operation, even when the app is in the background.
 * There is a timeout at the end of which we consider that if we have not received a location we abandon
 * the request and start a new one, by x2 the timeout.
 * Of course there is a limit to this process, if the limit is reached we consider that
 * there is more chance of finding the location.
 **/
class LocationService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1234
        private const val TAG = "LocationService"
        private const val MIN_TIMEOUT = 15000L
        const val KEY_SENDER = "sender"
        private const val MAX_RETRIES = 4
    }

    private lateinit var locationManager: LocationManager
    private val senders = mutableListOf<String>()
    private var locationReceived = false
    private var retryCount: Int = 1
    private var stopServiceDelay: Long = MIN_TIMEOUT
    private var wakeLock: PowerManager.WakeLock? = null

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!locationReceived) {
                locationReceived = true
                Log.d(TAG, "Location received: $location")
                handleLocationReceived(location)
            }
        }

        override fun onProviderDisabled(provider: String) {
            Log.w(TAG, "Provider disabled: $provider")
        }

        override fun onProviderEnabled(provider: String) {
            Log.d(TAG, "Provider enabled: $provider")
        }
    }

    private val handler by lazy { Handler(Looper.getMainLooper()) }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:LocationServiceWakelock"
        )
        wakeLock?.acquire(MIN_TIMEOUT * 2 * MAX_RETRIES) // MIN_TIMEOUT*2*MAX_RETRIES > First+Second+third+fourth retry
        try {
            locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        } catch (e: Exception){
            Log.e(TAG, "ERROR in LocationService onCreate : $e")
        }
        setupForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand received")
        intent?.getStringArrayListExtra(KEY_SENDER)?.let {
            senders.clear()
            senders.addAll(it)
        }

        retryCount = 1
        stopServiceDelay = MIN_TIMEOUT

        locationReceived = false
        startLocationUpdatesWithTimeout()
        return START_STICKY
    }

    @Suppress("MissingPermission")
    private fun startLocationUpdatesWithTimeout() {
        handler.removeCallbacksAndMessages(null)
        locationManager.removeUpdates(locationListener)

        Log.d(TAG, "Starting location updates. Timeout = $stopServiceDelay ms (try $retryCount)")
        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (!gpsEnabled && !networkEnabled) {
            Log.e(TAG, "No location providers enabled, sending fallback")
            fallbackToLastKnownOrSendNull()
            stopLocationUpdatesAndFinish()
            return
        }
        if (gpsEnabled) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 0L, 0f, locationListener
            )
        }
        if (networkEnabled) {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, 0L, 0f, locationListener
            )
        }
        handler.postDelayed(::onLocationTimeout, stopServiceDelay)
    }

    private fun onLocationTimeout() {
        Log.w(TAG, "Timeout reached (${stopServiceDelay} ms) without receiving new location.")
        if (retryCount < MAX_RETRIES) {
            retryCount += 1
            stopServiceDelay = stopServiceDelay * 2
            Log.w(TAG, "Retrying in same service instance: attempt $retryCount, timeout $stopServiceDelay")
            startLocationUpdatesWithTimeout()
        } else {
            Log.w(TAG, "Max retries reached, using fallback to last known or null.")
            fallbackToLastKnownOrSendNull()
            stopLocationUpdatesAndFinish()
        }
    }

    @Suppress("MissingPermission")
    private fun fallbackToLastKnownOrSendNull() {
        val recentLocation = listOfNotNull(
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER),
            locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        ).maxByOrNull { it.time }

        if (recentLocation != null) {
            Log.d(TAG, "Found last known location: ${recentLocation.latitude}, ${recentLocation.longitude}")
            handleLocationReceived(recentLocation)
        } else {
            Log.w(TAG, "No last known location found, sending null coordinates")
            sendLocationToAll(null, null)
        }
    }

    private fun handleLocationReceived(location: Location) {
        sendLocationToAll(location.latitude, location.longitude)
        stopLocationUpdatesAndFinish()
    }

    private fun sendLocationToAll(latitude: Double?, longitude: Double?) {
        Log.d(TAG, "Sending location to senders: $latitude, $longitude")
        val senderUtil = SmsSender(this)
        senders.forEach { sender ->
            senderUtil.sendSms(sender, latitude, longitude)
        }
    }

    private fun setupForegroundNotification() {
        val notificationBuilder = NotificationHelper()
        notificationBuilder.createNotificationChannel(this)
        val notification = notificationBuilder.createNotification(this)
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun stopLocationUpdatesAndFinish() {
        Log.d(TAG, "Stopping location updates and finishing service")
        locationManager.removeUpdates(locationListener)
        handler.removeCallbacksAndMessages(null)
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed, cleaning resources")

        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null

        stopLocationUpdatesAndFinish()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
