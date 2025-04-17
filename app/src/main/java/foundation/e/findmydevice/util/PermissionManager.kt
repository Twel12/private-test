package foundation.e.findmydevice.util

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import foundation.e.findmydevice.activity.FindMyDeviceActivity

import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import foundation.e.findmydevice.R


data class PermissionResult(
    val allPermissionsGranted: Boolean,
    val deniedPermissions: List<String>
)

/**
 * PermissionManager
 *
 * This class is responsible for simplifying the process of checking and requesting runtime permissions
 * in the application.
 **/
class PermissionManager(private val activity: FindMyDeviceActivity) {

    companion object {
        const val TAG = "PermissionManager"
    }

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>>
    private var permissionCallback: ((PermissionResult) -> Unit)? = null

    init {
        registerForActivityResult()
    }

    private fun registerForActivityResult() {
        requestPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val deniedPermissions = permissions.filter { !it.value }.map { it.key }
            val result = PermissionResult(
                allPermissionsGranted = deniedPermissions.isEmpty(),
                deniedPermissions = deniedPermissions
            )

            permissionCallback?.invoke(result)

            if (deniedPermissions.isNotEmpty()) {
                Log.e(TAG, "Permission refused : $deniedPermissions")
                Toast.makeText(
                    activity,
                    activity.getString(R.string.check_permission) + " refused : $deniedPermissions",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun checkAndRequestPermissions(
        permissions: List<String>,
        callback: (PermissionResult) -> Unit
    ) {
        permissionCallback = callback
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isEmpty()) {
            callback(PermissionResult(true, emptyList()))
        } else {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    fun checkPermissions(permissions: List<String>): PermissionResult {
        val deniedPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        return PermissionResult(deniedPermissions.isEmpty(), deniedPermissions)
    }


    fun checkAndRequestPermissionsWithBackground(
        callback: (PermissionResult) -> Unit
    ) {
        val standardPermissions  = mutableListOf(
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            standardPermissions .add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            standardPermissions .add(android.Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        }

        // Ask for standard permission
        checkAndRequestPermissions(standardPermissions) { result ->
            if (result.allPermissionsGranted) {
                // Ask for permission ACCESS_BACKGROUND_LOCATION only
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    checkAndRequestPermissions(
                        listOf(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    ) { backgroundResult ->
                        callback(backgroundResult)
                    }
                } else {
                    // Versions Android < 10 (Q): pas de permission d'arrière-plan à demander séparément
                    callback(result)
                }
            } else {
                // Permissions standards refusées, callback immédiat
                callback(result)
            }
        }
    }
}
