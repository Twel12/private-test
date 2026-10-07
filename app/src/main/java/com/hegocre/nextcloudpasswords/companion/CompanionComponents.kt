package com.hegocre.nextcloudpasswords.companion

import android.content.Context

object CompanionComponents {
    @Volatile
    private var vault: CompanionVault? = null

    /** One vault per process, so the service and the request screen share its per-owner locks. */
    fun vault(context: Context): CompanionVault = vault ?: synchronized(this) {
        vault ?: CompanionVault(
            ApiCompanionStore(context.applicationContext),
            ApiSessionGate(context.applicationContext),
        ).also { vault = it }
    }
}
