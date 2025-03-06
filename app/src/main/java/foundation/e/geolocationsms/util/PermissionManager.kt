package foundation.e.geolocationsms.util

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import foundation.e.geolocationsms.activity.GeolocationSmsActivity

import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import foundation.e.geolocationsms.R

/**
 * PermissionManager
 *
 * This class is responsible for simplifying the process of checking and requesting runtime permissions
 * in the application.
 **/
class PermissionManager(private val activity: GeolocationSmsActivity) {

    companion object {
        const val TAG = "PermissionManager"
     }

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>>
    private var permissionCallback: ((granted: Boolean) -> Unit)? = null

    init {
        registerForActivityResult()
    }

    private fun registerForActivityResult() {
        requestPermissionLauncher =
            activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                val allGranted = permissions.all { it.value }
                permissionCallback?.invoke(allGranted)
                if (!allGranted) {
                    Log.e(TAG, "Permission error")
                    Toast.makeText(activity, activity.getString(R.string.check_permission), Toast.LENGTH_SHORT).show()
                }
            }
    }

    fun checkAndRequestPermissions(
        permissions: List<String>,
        callback: (granted: Boolean) -> Unit
    ) {
        permissionCallback = callback
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isEmpty()) {
            callback(true) // All permissions already granted
        } else {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    fun checkPermissions(permissions: List<String>): Boolean{
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        return permissionsToRequest.isEmpty()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun checkAndRequestPermissionsWithBackground(callback: (granted: Boolean) -> Unit){
        val permissions = mutableListOf(
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.FOREGROUND_SERVICE_LOCATION,
            android.Manifest.permission.POST_NOTIFICATIONS
        )

        if (checkPermissions(permissions)){
            checkAndRequestPermissions(listOf(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)) { granted ->
                if (!granted) {
                    Log.e(TAG, "Permission error (2)")
                    Toast.makeText(activity, activity.getString(R.string.check_permission), Toast.LENGTH_SHORT).show()
                    callback(false)
                } else{
                    callback(true)
                }
            }
        } else {
            checkAndRequestPermissions(permissions) { granted ->
                if (!granted) {
                    Log.e(TAG, "Permission error")
                    Toast.makeText(activity, activity.getString(R.string.check_permission), Toast.LENGTH_SHORT).show()
                    callback(false)
                } else{
                    checkAndRequestPermissions(listOf(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                        granted ->
                        if (!granted) {
                            Log.e(TAG, "Permission error (2)")
                            Toast.makeText(activity,
                                activity.getString(R.string.check_permission), Toast.LENGTH_SHORT).show()
                            callback(false)
                        } else{
                            callback(true)
                        }
                    }
                }
            }
        }
    }

}
