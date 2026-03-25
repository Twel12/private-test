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
import com.nextcloud.android.sso.model.SingleSignOnAccount
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Response
import java.io.IOException
import java.net.MalformedURLException

interface OkHttpRequestInterface {

    @Throws(
        MalformedURLException::class,
        IllegalArgumentException::class,
        IOException::class,
        IllegalStateException::class
    )
    fun get(
        sUrl: String,
        sessionCode: String? = null,
        username: String? = null,
        password: String? = null
    ): Response

    @Throws(
        MalformedURLException::class,
        IllegalArgumentException::class,
        IOException::class,
        IllegalStateException::class
    )
    fun post(
        sUrl: String,
        sessionCode: String? = null,
        body: String,
        mediaType: MediaType?,
        username: String? = null,
        password: String? = null
    ): Response

    @Throws(
        MalformedURLException::class,
        IllegalArgumentException::class,
        IOException::class,
        IllegalStateException::class
    )
    fun patch(
        sUrl: String,
        sessionCode: String? = null,
        body: String,
        mediaType: MediaType?,
        username: String? = null,
        password: String? = null
    ): Response

    @Throws(
        MalformedURLException::class,
        IllegalArgumentException::class,
        IOException::class,
        IllegalStateException::class
    )
    fun delete(
        sUrl: String,
        sessionCode: String? = null,
        body: String,
        mediaType: MediaType?,
        username: String? = null,
        password: String? = null
    ): Response

    companion object {

        @Volatile
        private var instance: OkHttpRequestInterface = BasicOkHttpRequest.getInstance()

        val JSON = OkHttpRequest.JSON

        @Synchronized
        fun getInstance(): OkHttpRequestInterface = instance

        @Synchronized
        fun useBasic(insecureAllowed: Boolean?) {
            instance = BasicOkHttpRequest.getInstance().apply {
                if (insecureAllowed != null) {
                    setAllowInsecureRequests(insecureAllowed)
                }
            }
        }

        @Synchronized
        fun useSso(context: Context, account: SingleSignOnAccount) {
            instance = SsoOkHttpRequest(context.applicationContext, account)
        }
    }
}
