/*
 * Copyright (C) 2026 MURENA SAS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.hegocre.nextcloudpasswords.utils

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.nextcloud.android.sso.aidl.NextcloudRequest
import com.nextcloud.android.sso.api.NextcloudAPI
import com.nextcloud.android.sso.model.SingleSignOnAccount
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL

class SsoOkHttpRequest(
    context: Context,
    private val ssoAccount: SingleSignOnAccount
) : OkHttpRequestInterface {

    companion object {
        private const val TAG = "SsoOkHttpRequest"
    }

    private val ssoApi = NextcloudAPI(context, ssoAccount, Gson())

    @Throws(
        MalformedURLException::class,
        IllegalArgumentException::class,
        IOException::class,
        IllegalStateException::class
    )
    override fun get(
        sUrl: String,
        sessionCode: String?,
        username: String?,
        password: String?
    ): Response = executeSso(method = "GET", sUrl = sUrl, sessionCode = sessionCode, body = null)

    @Throws(
        MalformedURLException::class,
        IllegalArgumentException::class,
        IOException::class,
        IllegalStateException::class
    )
    override fun post(
        sUrl: String,
        sessionCode: String?,
        body: String,
        mediaType: MediaType?,
        username: String?,
        password: String?
    ): Response = executeSso(
        method = "POST",
        sUrl = sUrl,
        sessionCode = sessionCode,
        body = body,
        mediaType = mediaType
    )

    @Throws(
        MalformedURLException::class,
        IllegalArgumentException::class,
        IOException::class,
        IllegalStateException::class
    )
    override fun patch(
        sUrl: String,
        sessionCode: String?,
        body: String,
        mediaType: MediaType?,
        username: String?,
        password: String?
    ): Response = executeSso(
        method = "PATCH",
        sUrl = sUrl,
        sessionCode = sessionCode,
        body = body,
        mediaType = mediaType
    )

    @Throws(
        MalformedURLException::class,
        IllegalArgumentException::class,
        IOException::class,
        IllegalStateException::class
    )
    override fun delete(
        sUrl: String,
        sessionCode: String?,
        body: String,
        mediaType: MediaType?,
        username: String?,
        password: String?
    ): Response = executeSso(
        method = "DELETE_PASSWORD", //special method in account manager which support http delete with body
        sUrl = sUrl,
        sessionCode = sessionCode,
        body = body,
        mediaType = mediaType
    )

    private fun executeSso(
        method: String,
        sUrl: String,
        sessionCode: String?,
        body: String?,
        mediaType: MediaType? = null,
        successStatusCode: Int = 200
    ): Response {
        val url = URL(sUrl)
        val requestPath = buildString {
            append(if (url.path.isNullOrBlank()) "/" else url.path)
            if (!url.query.isNullOrBlank()) {
                append('?').append(url.query)
            }
        }

        val requestBuilder = NextcloudRequest.Builder()
            .setMethod(method)
            .setUrl(requestPath)
            .setAccountName(ssoAccount.name)
            .setToken(ssoAccount.token)
            .setFollowRedirects(true)

        if (sessionCode != null) {
            requestBuilder.setHeader(
                mapOf(
                    "OCS-APIRequest" to listOf("true"),
                    "x-api-session" to listOf(sessionCode)
                )
            )
        } else {
            requestBuilder.setHeader(
                mapOf("OCS-APIRequest" to listOf("true"))
            )
        }

        if (body != null) {
            requestBuilder.setRequestBody(body)
            if (mediaType != null) {
                val existingHeaders = mutableMapOf<String, List<String>>()
                existingHeaders["OCS-APIRequest"] = listOf("true")
                existingHeaders["Content-Type"] = listOf(mediaType.toString())
                if (sessionCode != null) existingHeaders["x-api-session"] = listOf(sessionCode)
                requestBuilder.setHeader(existingHeaders)
            }
        }

        val ssoResponse = try {
            ssoApi.performNetworkRequestV2(requestBuilder.build())
        } catch (e: Exception) {
            // this intentional `performNetworkRequestV2` can throw generic `Exception`
            // reset of app is not designed for handling generic exception
            Log.d(TAG, "error on performNetworkRequestV2", e)
            throw IOException("unexpected error ${e.localizedMessage}")
        }
        val bodyBytes = ssoResponse.body.use { it.readBytes() }

        val headersBuilder = Headers.Builder()
        var statusCode = successStatusCode
        var contentTypeHeader: String? = null
        for (header in ssoResponse.plainHeaders) {
            val headerName = header.name ?: continue
            val headerValue = header.value ?: ""
            headersBuilder.addUnsafeNonAscii(headerName, headerValue)
            if (headerName.equals("status", ignoreCase = true)) {
                statusCode = headerValue.toIntOrNull() ?: statusCode
            }
            if (headerName.equals("content-type", ignoreCase = true)) {
                contentTypeHeader = headerValue
            }
        }

        val responseMediaType = mediaType
            ?: contentTypeHeader?.toMediaTypeOrNull()
            ?: "application/octet-stream".toMediaTypeOrNull()

        val responseBody = bodyBytes.toResponseBody(responseMediaType)
        return Response.Builder()
            .request(Request.Builder().url(sUrl).build())
            .protocol(Protocol.HTTP_1_1)
            .code(statusCode)
            .message("")
            .headers(headersBuilder.build())
            .body(responseBody)
            .build()
    }
}
