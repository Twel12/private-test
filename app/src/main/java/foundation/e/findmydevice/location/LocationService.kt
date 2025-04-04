package foundation.e.findmydevice.location

import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.util.Log

import foundation.e.findmydevice.util.NotificationHelper
import foundation.e.findmydevice.util.SmsSender

/**
 * LocationService
 *
 * This class is a Service responsible for obtaining the device's current location and
 * notifying the application when the location has been determined. It runs as a
 * foreground service to ensure continuous operation, even when the app is in the background.
 **/
class LocationService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1234
        private const val TAG = "LocationService"
        private const val STOP_SERVICE_DELAY = 15000L
        const val KEY_SENDER = "sender"
    }

    private lateinit var locationManager: LocationManager
    private val senders = mutableListOf<String>()
    private var locationReceived = false

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

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        setupForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand received")

        intent?.getStringArrayListExtra(KEY_SENDER)?.let {
            senders.clear()
            senders.addAll(it)
        }

        startLocationUpdatesWithTimeout()

        return START_STICKY
    }

    @Suppress("MissingPermission")
    private fun startLocationUpdatesWithTimeout() {
        Log.d(TAG, "Starting location updates")

        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!gpsEnabled && !networkEnabled) {
            Log.e(TAG, "No location providers enabled, sending fallback")
            fallbackToLastKnownOrSendNull()
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

        handler.postDelayed(::onLocationTimeout, STOP_SERVICE_DELAY)
    }

    private fun onLocationTimeout() {
        Log.w(TAG, "Timeout reached without receiving new location, using last known")
        fallbackToLastKnownOrSendNull()
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

        stopLocationUpdatesAndFinish()
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
        stopLocationUpdatesAndFinish()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
