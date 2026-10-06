package com.hegocre.nextcloudpasswords.companion

object CallerResolver {
    /** The calling package, or null when the UID maps to none or to several (shared UID). */
    fun resolve(packages: Array<String>?): String? = packages?.singleOrNull()?.takeIf { it.isNotBlank() }
}
