package foundation.e.backupappapi

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class BackupKey(val data: E2eeKeyWrapper) : Parcelable
