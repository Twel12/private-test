package foundation.e.geolocationsms.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import foundation.e.geolocationsms.R
import foundation.e.geolocationsms.activity.GeolocationSmsActivity

class LocationWorker : Service() {

    private val NOTIFICATION_ID = 1234
    private val CHANNEL_ID = "location_service_channel"

    private lateinit var locationManager: LocationManager

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            Log.d("LocationProvider", "Location changed: Lat: ${location.latitude}, Lon: ${location.longitude}")
            onLocationReceived?.invoke(location)
            stopLocationUpdates()
            stopLocationUpdatesAndFinish()
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            Log.d("LocationProvider", "Status changed: $provider, Status: $status")
        }

        override fun onProviderEnabled(provider: String) {
            Log.d("LocationProvider", "Provider enabled: $provider")
        }

        override fun onProviderDisabled(provider: String) {
            Log.d("LocationProvider", "Provider disabled: $provider")
        }
    }

    private var onLocationReceived: ((Location) -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())

    private fun stopLocationUpdates() {
        Log.d("LocationProvider", "Stopping location updates")
        locationManager.removeUpdates(locationListener)
        handler.removeCallbacksAndMessages(null)
    }

    override fun onCreate() {
        Log.d("LocationProvider", "onCreate")
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        startForegroundService()
        startLocationUpdates()
    }

    private fun startForegroundService() {
        Log.d("LocationProvider", "Starting foreground service")
        val notificationIntent = Intent(this, GeolocationSmsActivity::class.java) // Remplacez par votre Activity principale
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)


        createNotificationChannel(CHANNEL_ID)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Location Service")
            .setContentText("Tracking location in background")
            .setSmallIcon(R.drawable.ic_launcher_foreground) //K1ZFP TODO
            .setContentIntent(pendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel(channelId: String) {
        val serviceChannel = NotificationChannel(
            channelId,
            "Location Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    @Suppress("MissingPermission")
    private fun startLocationUpdates() {
        Log.d("LocationProvider", "Starting location updates")

        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        Log.d("LocationProvider", "GPS enabled: $isGpsEnabled, Network enabled: $isNetworkEnabled")

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
            Log.e("LocationProvider", "No provider enabled")
        }

        handler.postDelayed({ stopLocationUpdatesAndFinish() }, 10000) // timeout 10 sec
    }

    override fun onBind(intent: Intent): IBinder? {
        // This is not a bound service
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(locationListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun stopLocationUpdatesAndFinish() {
        Log.d("LocationProvider", "stopLocationUpdatesAndFinish")
        locationManager.removeUpdates(locationListener)
        handler.removeCallbacksAndMessages(null)
        stopForeground(true)
        stopSelf()
    }
}