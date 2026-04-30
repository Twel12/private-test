/*
 *  Copyright MURENA SAS 2026
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */
package com.hegocre.nextcloudpasswords.services.autofill

import android.content.Context
import androidx.core.net.toUri
import com.hegocre.nextcloudpasswords.data.password.CustomField
import com.hegocre.nextcloudpasswords.data.password.Password
import foundation.e.auto_fill.PasswordQuery
import foundation.e.auto_fill.PasswordSaveRequest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.internal.publicsuffix.PublicSuffixDatabase

class NCPAutofillMatcher(private val context: Context) {

    fun candidates(request: PasswordQuery): List<String> {
        return buildList {
            request.webDomain?.takeIf { it.isNotBlank() }?.let { domain ->
                add(domain)
                add("https://$domain")
                add(domain.substringBefore('/'))
                add(domain.substringBefore('.'))
            }
            request.origin?.takeIf { it.isNotBlank() }?.let(::add)
            request.packageName?.takeIf { it.isNotBlank() }?.let { packageName ->
                addPackageCandidates(packageName)
            }
            request.usernameHint?.takeIf { it.isNotBlank() }?.let(::add)
        }.normalized()
    }

    fun candidates(request: PasswordSaveRequest): List<String> {
        return buildList {
            request.webDomain?.takeIf { it.isNotBlank() }?.let(::add)
            request.origin?.takeIf { it.isNotBlank() }?.let(::add)
            request.packageName?.takeIf { it.isNotBlank() }?.let { packageName ->
                addPackageCandidates(packageName)
            }
        }.normalized()
    }

    fun matches(password: Password, candidates: List<String>): Boolean {
        return candidates.any { candidate -> password.matchesCandidate(candidate) }
    }

    fun packageNames(password: Password): Set<String> {
        return NCPAutofillMetadata.packageNames(password.customFields)
    }

    fun hasPackage(password: Password, packageName: String): Boolean {
        return packageName in packageNames(password) ||
            password.url.contains(androidUri(packageName), ignoreCase = true)
    }

    fun androidUri(packageName: String): String = "android://$packageName"

    private fun MutableList<String>.addPackageCandidates(packageName: String) {
        add(packageName)
        add(androidUri(packageName))
        add(packageName.substringAfterLast('.'))
        applicationLabel(packageName)?.let(::add)
    }

    private fun List<String>.normalized(): List<String> {
        return map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun Password.matchesCandidate(candidate: String): Boolean {
        val normalizedCandidate = candidate.lowercase()

        return listOf(label, username, url).any {
            it.lowercase().contains(normalizedCandidate)
        } ||
            packageNames(this).any { it.equals(candidate, ignoreCase = true) } ||
            matchesEffectiveDomain(candidate) ||
            matches(candidate, strictUrlMatching = false)
    }

    private fun Password.matchesEffectiveDomain(candidate: String): Boolean {
        val candidateDomain = candidate.effectiveDomainOrNull()
        return candidateDomain != null && candidateDomain == url.effectiveDomainOrNull()
    }

    private fun applicationLabel(packageName: String): String? {
        return runCatching {
            @Suppress("DEPRECATION")
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        }.getOrNull()
    }

    private fun String.effectiveDomainOrNull(): String? {
        return runCatching {
            val host = toUri().host ?: "https://$this".toUri().host ?: return null
            PublicSuffixDatabase.get().getEffectiveTldPlusOne(host) ?: host
        }.getOrNull()
    }
}

object NCPAutofillMetadata {
    private const val ANDROID_APPS_FIELD_LABEL = "Android apps"

    fun packageNames(customFieldsJson: String): Set<String> {
        return customFields(customFieldsJson)
            .firstOrNull { it.label == ANDROID_APPS_FIELD_LABEL }
            ?.value
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()
    }

    fun withPackage(customFieldsJson: String, packageName: String): String {
        val fields = customFields(customFieldsJson).toMutableList()
        val existingIndex = fields.indexOfFirst { it.label == ANDROID_APPS_FIELD_LABEL }
        val packages = if (existingIndex >= 0) {
            fields[existingIndex].value.lineSequence().map { it.trim() }.toMutableSet()
        } else {
            mutableSetOf()
        }
        packages.add(packageName)

        val appField = CustomField(
            label = ANDROID_APPS_FIELD_LABEL,
            type = CustomField.TYPE_TEXT,
            value = packages.filter { it.isNotBlank() }.sorted().joinToString("\n")
        )

        if (existingIndex >= 0) {
            fields[existingIndex] = appField
        } else {
            fields.add(appField)
        }
        return Json.encodeToString(fields)
    }

    private fun customFields(customFieldsJson: String): List<CustomField> {
        if (customFieldsJson.isBlank()) return emptyList()
        return try {
            Json.decodeFromString<List<CustomField>>(customFieldsJson)
        } catch (_: SerializationException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }
}
