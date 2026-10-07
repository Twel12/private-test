package com.hegocre.nextcloudpasswords.companion

object CallerResolver {
    fun resolve(hasPermission: Boolean, packages: Array<String>?): String? =
        if (hasPermission) packages?.singleOrNull()?.takeIf { it.isNotBlank() } else null
}
