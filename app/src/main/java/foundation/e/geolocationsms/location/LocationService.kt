package foundation.e.geolocationsms.location

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

import foundation.e.geolocationsms.NotificationManagerUtils
import foundation.e.geolocationsms.SmsSender

/**
 * LocationService
 *
 * This class is a Service responsible for obtaining the device's current location and
 * notifying the application when the location has been determined. It runs as a
 * foreground service to ensure continuous operation, even when the app is in the background.
 **/
class LocationService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1234 // K1ZFP Check this
        private const val TAG = "LocationService"
        private const val STOP_SERVICE_DELAY = 10000L // 10 seconds
        const val KEY_SENDER = "sender"
    }

    private lateinit var locationManager: LocationManager
    private var senders = mutableListOf<String>()

    private val locationListener = LocationListener { location ->
        Log.d(TAG, "Location changed: $location")
        val latitude = location.latitude
        val longitude = location.longitude
        sendLocation(latitude, longitude)
        onLocationReceived?.invoke(location)
        stopLocationUpdatesAndFinish()
    }

    private fun sendLocation(latitude: Double, longitude: Double) {
        val body = "Latitude: $latitude, Longitude: $longitude"
        for (sender in senders) {
            SmsSender(this).sendSms(sender, body)
        }
    }

    private var onLocationReceived: ((Location) -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())

    private fun stopLocationUpdates() {
        Log.d(TAG, "Stopping location updates")
        locationManager.removeUpdates(locationListener)
        handler.removeCallbacksAndMessages(null)
    }

    override fun onCreate() {
        Log.d(TAG, "onCreate")
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val notificationBuilder = NotificationManagerUtils()
        notificationBuilder.createNotificationChannel(this)
        val notification= notificationBuilder.createNotification(this)
        startForeground(NOTIFICATION_ID, notification)
        startLocationUpdates()
    }

    @Suppress("MissingPermission")
    private fun startLocationUpdates() {
        Log.d(TAG, "Starting location updates")

        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        Log.d(TAG, "GPS enabled: $isGpsEnabled, Network enabled: $isNetworkEnabled")

        if (isGpsEnabled) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                0L,
                0f,
                locationListener
            )
        }

        if (isNetworkEnabled) {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                0L,
                0f,
                locationListener
            )
        }

        if (!isGpsEnabled && !isNetworkEnabled) {
            Log.e(TAG, "No provider enabled")
        }

        handler.postDelayed({ stopLocationUpdatesAndFinish() }, STOP_SERVICE_DELAY)
    }

    override fun onBind(intent: Intent): IBinder? {
        // This is not a bound service
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        senders.clear()
        val myStringArray: Array<String>? = intent?.getStringArrayExtra(KEY_SENDER)
        if (myStringArray != null) {
            val newSenders = ArrayList(myStringArray.toList())
            senders.addAll(newSenders)
        }
        return START_STICKY
    }

    private fun stopLocationUpdatesAndFinish() {
        Log.d(TAG, "stopLocationUpdatesAndFinish")
        stopLocationUpdates()
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }
}
