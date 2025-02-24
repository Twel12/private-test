package foundation.e.geolocationsms

import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class PermissionManager(private val activity: GeolocationSmsActivity) {

    private var permissionRequestLauncher: ActivityResultLauncher<Array<String>>? = null
    private var onPermissionResult: ((Boolean) -> Unit)? = null

    init {
        permissionRequestLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allPermissionsGranted = permissions.entries.all { it.value }
            onPermissionResult?.invoke(allPermissionsGranted)
        }
    }

    fun checkAndRequestPermissions(permissions: Array<String>, onResult: (Boolean) -> Unit) {
        val requiredPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }

        if (requiredPermissions.isEmpty()) {
            onResult(true)
        } else {
            onPermissionResult = onResult
            permissionRequestLauncher?.launch(requiredPermissions.toTypedArray())
        }
    }
}