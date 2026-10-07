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
import com.hegocre.nextcloudpasswords.R
import com.hegocre.nextcloudpasswords.data.password.CustomField
import com.hegocre.nextcloudpasswords.data.password.Password
import foundation.e.autofill.PasswordQuery
import foundation.e.autofill.PasswordResolveRequest
import foundation.e.autofill.PasswordRequestSource
import foundation.e.autofill.PasswordSaveRequest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.internal.publicsuffix.PublicSuffixDatabase
import org.xmlpull.v1.XmlPullParser

class NCPAutofillMatcher(private val context: Context) {
    private val browserPackages: Set<String> by lazy { loadBrowserPackages() }

    fun candidates(request: PasswordQuery): List<String> {
        val hasWebContext = request.hasWebContext()
        val canUsePackageContext = !hasWebContext && !request.isKnownBrowserCredentialManagerRequest()
        return buildList {
            request.webDomain?.takeIf { it.isNotBlank() }?.let { domain ->
                add(domain)
                add("https://$domain")
                add(domain.substringBefore('/'))
            }
            request.origin?.takeIf { it.isNotBlank() }?.let(::add)
            if (canUsePackageContext) {
                request.packageName?.takeIf { it.isNotBlank() }?.let { packageName ->
                    addPackageCandidates(packageName)
                }
            }
            request.usernameHint?.takeIf { it.isNotBlank() }?.let(::add)
        }.normalized()
    }

    fun candidates(request: PasswordSaveRequest): List<String> {
        val hasWebContext = request.hasWebContext()
        val canUsePackageContext = !hasWebContext && !request.isKnownBrowserCredentialManagerRequest()
        return buildList {
            request.webDomain?.takeIf { it.isNotBlank() }?.let(::add)
            request.origin?.takeIf { it.isNotBlank() }?.let(::add)
            if (canUsePackageContext) {
                request.packageName?.takeIf { it.isNotBlank() }?.let { packageName ->
                    addPackageCandidates(packageName)
                }
            }
        }.normalized()
    }

    fun candidates(request: PasswordResolveRequest): List<String> {
        val hasWebContext = request.hasWebContext()
        val canUsePackageContext = !hasWebContext && !request.isKnownBrowserCredentialManagerRequest()
        return buildList {
            request.webDomain?.takeIf { it.isNotBlank() }?.let(::add)
            request.origin?.takeIf { it.isNotBlank() }?.let(::add)
            if (canUsePackageContext) {
                request.packageName?.takeIf { it.isNotBlank() }?.let { packageName ->
                    addPackageCandidates(packageName)
                }
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

    fun hasIdentityKey(password: Password, identityKey: String): Boolean {
        return NCPAutofillMetadata.identityKey(password.customFields) == identityKey
    }

    fun isKnownBrowserPackage(packageName: String?): Boolean {
        return packageName != null && packageName in browserPackages
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
            NCPAutofillMetadata.websiteUrls(customFields).any { customUrl ->
                customUrl.matchesUrlCandidate(candidate)
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
            val appInfo = context.packageManager.getApplicationInfoCompat(packageName)
            context.packageManager.getApplicationLabel(appInfo).toString()
        }.getOrNull()
    }

    private fun PasswordQuery.hasWebContext(): Boolean {
        return isWebOriginRequest || !webDomain.isNullOrBlank() || !origin.isNullOrBlank()
    }

    private fun PasswordQuery.isKnownBrowserCredentialManagerRequest(): Boolean {
        return source == PasswordRequestSource.CREDENTIAL_MANAGER &&
            isKnownBrowserPackage(packageName)
    }

    private fun PasswordSaveRequest.hasWebContext(): Boolean {
        return isWebOriginRequest || !webDomain.isNullOrBlank() || !origin.isNullOrBlank()
    }

    private fun PasswordSaveRequest.isKnownBrowserCredentialManagerRequest(): Boolean {
        return source == PasswordRequestSource.CREDENTIAL_MANAGER &&
            isKnownBrowserPackage(packageName)
    }

    private fun PasswordResolveRequest.hasWebContext(): Boolean {
        return isWebOriginRequest || !webDomain.isNullOrBlank() || !origin.isNullOrBlank()
    }

    private fun PasswordResolveRequest.isKnownBrowserCredentialManagerRequest(): Boolean {
        return source == PasswordRequestSource.CREDENTIAL_MANAGER &&
            isKnownBrowserPackage(packageName)
    }

    private fun loadBrowserPackages(): Set<String> {
        return runCatching {
            context.resources.getXml(R.xml.service_configuration).use { parser ->
                buildSet {
                    while (parser.next() != XmlPullParser.END_DOCUMENT) {
                        if (
                            parser.eventType == XmlPullParser.START_TAG &&
                            parser.name == COMPATIBILITY_PACKAGE_TAG
                        ) {
                            parser.getAttributeValue(ANDROID_NAMESPACE, NAME_ATTRIBUTE)
                                ?.takeIf { it.isNotBlank() }
                                ?.let(::add)
                        }
                    }
                }
            }
        }.getOrDefault(emptySet())
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        const val COMPATIBILITY_PACKAGE_TAG = "compatibility-package"
        const val NAME_ATTRIBUTE = "name"
    }
}

object NCPAutofillMetadata {
    private const val ANDROID_APPS_FIELD_LABEL = "Android apps"
    private const val WEBSITE_FIELD_LABEL = "URL"
    private const val IDENTITY_KEY_FIELD_LABEL = "foundation.e.credential.key"
    private const val READ_ONLY_FIELD_LABEL = "foundation.e.credential.readonly"
    private const val READ_ONLY_VALUE = "true"
    private val WEBSITE_FIELD_KEYWORDS = listOf("website", "url")

    data class WebsiteAssociationUpdate(
        val url: String,
        val customFieldsJson: String
    )

    fun normalizeWebsite(website: String?): String? {
        val trimmedWebsite = website?.trim().orEmpty()
        if (trimmedWebsite.isBlank()) return null

        return if (
            trimmedWebsite.startsWith("http://") ||
            trimmedWebsite.startsWith("https://")
        ) {
            trimmedWebsite
        } else {
            "https://$trimmedWebsite"
        }
    }

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

    fun websiteUrls(customFieldsJson: String): List<String> {
        return customFields(customFieldsJson)
            .asSequence()
            .filter(::isWebsiteLikeField)
            .map { it.value.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
    }

    fun hasWebsiteAssociation(password: Password, website: String): Boolean {
        val normalizedWebsite = normalizeWebsite(website) ?: return false

        return password.matches(normalizedWebsite, strictUrlMatching = false) ||
            websiteUrls(password.customFields).any { customUrl ->
                customUrl.matchesUrlCandidate(normalizedWebsite)
            }
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

    fun identityKey(customFieldsJson: String): String? {
        return customFields(customFieldsJson)
            .firstOrNull { it.label == IDENTITY_KEY_FIELD_LABEL }
            ?.value
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    fun withIdentityKey(customFieldsJson: String, identityKey: String): String {
        val fields = customFields(customFieldsJson).toMutableList()
        // Stored trimmed because identityKey() trims on read; leaving them asymmetric would let a
        // padded key fail to match itself and duplicate on every save.
        val identityField = CustomField(
            label = IDENTITY_KEY_FIELD_LABEL,
            type = CustomField.TYPE_DATA,
            value = identityKey.trim()
        )

        val existingIndex = fields.indexOfFirst { it.label == IDENTITY_KEY_FIELD_LABEL }
        if (existingIndex >= 0) {
            fields[existingIndex] = identityField
        } else {
            fields.add(identityField)
        }
        return Json.encodeToString(fields)
    }

    fun isReadOnly(customFieldsJson: String): Boolean {
        return customFields(customFieldsJson).any {
            it.label == READ_ONLY_FIELD_LABEL && it.value == READ_ONLY_VALUE
        }
    }

    fun withReadOnly(customFieldsJson: String, readOnly: Boolean): String {
        val fields = customFields(customFieldsJson)
            .filterNot { it.label == READ_ONLY_FIELD_LABEL }
            .toMutableList()
        if (readOnly) {
            fields.add(
                CustomField(
                    label = READ_ONLY_FIELD_LABEL,
                    type = CustomField.TYPE_DATA,
                    value = READ_ONLY_VALUE
                )
            )
        }
        return Json.encodeToString(fields)
    }

    fun linkWebsite(
        currentUrl: String,
        customFieldsJson: String,
        website: String
    ): WebsiteAssociationUpdate {
        val normalizedWebsite = normalizeWebsite(website)
        val update = if (normalizedWebsite == null) {
            WebsiteAssociationUpdate(currentUrl, customFieldsJson)
        } else if (currentUrl.isBlank()) {
            WebsiteAssociationUpdate(
                url = normalizedWebsite,
                customFieldsJson = customFieldsJson
            )
        } else {
            val fields = customFields(customFieldsJson).toMutableList()
            val alreadyPresent = fields.any { field ->
                isWebsiteLikeField(field) && field.value.trim() == normalizedWebsite
            }
            if (!alreadyPresent) {
                fields.add(
                    CustomField(
                        label = WEBSITE_FIELD_LABEL,
                        type = CustomField.TYPE_URL,
                        value = normalizedWebsite
                    )
                )
            }

            WebsiteAssociationUpdate(
                url = currentUrl,
                customFieldsJson = Json.encodeToString(fields)
            )
        }

        return update
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

    private fun isWebsiteLikeField(field: CustomField): Boolean {
        return field.type.equals(CustomField.TYPE_URL, ignoreCase = true) ||
            WEBSITE_FIELD_KEYWORDS.any { keyword ->
                field.label.contains(keyword, ignoreCase = true)
            }
    }
}

private fun String.effectiveDomainOrNull(): String? {
    return runCatching {
        val host = toUri().host ?: "https://$this".toUri().host ?: return null
        PublicSuffixDatabase.get().getEffectiveTldPlusOne(host) ?: host
    }.getOrNull()
}

private fun String.matchesUrlCandidate(candidate: String): Boolean {
    return lowercase().contains(candidate.lowercase()) ||
        effectiveDomainOrNull() == candidate.effectiveDomainOrNull()
}
