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


import okhttp3.MediaType
import okhttp3.Response

class BasicOkHttpRequest private constructor() : OkHttpRequestInterface {

    companion object {

        private var instance: BasicOkHttpRequest? = null

        fun getInstance(): BasicOkHttpRequest {
            synchronized(this) {
                if (instance == null) instance = BasicOkHttpRequest()
                return instance as BasicOkHttpRequest
            }
        }


    }

    private val okHttpRequest = OkHttpRequest.getInstance()

    override fun get(
        sUrl: String,
        sessionCode: String?,
        username: String?,
        password: String?
    ): Response = okHttpRequest.get(
        sUrl = sUrl,
        sessionCode = sessionCode,
        username = username,
        password = password
    )

    override fun post(
        sUrl: String,
        sessionCode: String?,
        body: String,
        mediaType: MediaType?,
        username: String?,
        password: String?
    ): Response = okHttpRequest.post(
        sUrl = sUrl,
        sessionCode = sessionCode,
        body = body,
        mediaType = mediaType,
        username = username,
        password = password
    )

    override fun patch(
        sUrl: String,
        sessionCode: String?,
        body: String,
        mediaType: MediaType?,
        username: String?,
        password: String?
    ): Response = okHttpRequest.patch(
        sUrl = sUrl,
        sessionCode = sessionCode,
        body = body,
        mediaType = mediaType,
        username = username,
        password = password
    )

    override fun delete(
        sUrl: String,
        sessionCode: String?,
        body: String,
        mediaType: MediaType?,
        username: String?,
        password: String?
    ): Response = okHttpRequest.delete(
        sUrl = sUrl,
        sessionCode = sessionCode,
        body = body,
        mediaType = mediaType,
        username = username,
        password = password
    )

    fun setAllowInsecureRequests(allowed: Boolean) {
        okHttpRequest.allowInsecureRequests = allowed
    }

    fun getAllowInsecureRequests() = okHttpRequest.allowInsecureRequests

}
