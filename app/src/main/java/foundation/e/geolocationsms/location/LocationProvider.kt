package foundation.e.geolocationsms.location

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log

class LocationProvider(private val context: Context) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            Log.d("LocationProvider", "Location changed: Lat: ${location.latitude}, Lon: ${location.longitude}")
            onLocationReceived?.invoke(location)
            stopLocationUpdates()
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

    fun getCurrentLocation(onLocationReceived: (Location) -> Unit) {
        this.onLocationReceived = onLocationReceived
        Log.d("LocationProvider", "getCurrentLocation called")
        startLocationUpdates()

        // Timeout de 10 secondes pour arrêter les mises à jour si aucune localisation n'est reçue
        handler.postDelayed({
            Log.d("LocationProvider", "Timeout reached - stopping location updates")
            stopLocationUpdates()
            // Essayer de prendre la dernière localisation connue comme alternative
            val lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (lastKnownLocation != null) {
                Log.d("LocationProvider", "Using last known location: Lat: ${lastKnownLocation.latitude}, Lon: ${lastKnownLocation.longitude}")
                onLocationReceived(lastKnownLocation)
            } else {
                Log.e("LocationProvider", "No last known location available")
            }
        }, 10000) // 10 secondes
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
    }

    private fun stopLocationUpdates() {
        Log.d("LocationProvider", "Stopping location updates")
        locationManager.removeUpdates(locationListener)
        handler.removeCallbacksAndMessages(null)
    }
}