package com.hegocre.nextcloudpasswords.companion

object CallerResolver {
    /**
     * The calling package, or null when it lacks the companion permission or its UID maps to none or
     * to several packages (shared UID).
     */
    fun resolve(hasPermission: Boolean, packages: Array<String>?): String? =
        if (hasPermission) packages?.singleOrNull()?.takeIf { it.isNotBlank() } else null
}
