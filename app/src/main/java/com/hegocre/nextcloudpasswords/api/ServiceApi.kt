package com.hegocre.nextcloudpasswords.api

import android.util.Log
import com.hegocre.nextcloudpasswords.BuildConfig
import com.hegocre.nextcloudpasswords.api.exceptions.SsoReauthenticationRequiredException
import com.hegocre.nextcloudpasswords.data.password.GeneratedPassword
import com.hegocre.nextcloudpasswords.data.password.RequestedPassword
import com.hegocre.nextcloudpasswords.utils.Error
import com.hegocre.nextcloudpasswords.utils.OkHttpRequest
import com.hegocre.nextcloudpasswords.utils.OkHttpRequestInterface
import com.hegocre.nextcloudpasswords.utils.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.util.Locale
import javax.net.ssl.SSLHandshakeException

/**
 * Class with methods used to interact with the
 * [Service API](https://git.mdns.eu/nextcloud/passwords/-/wikis/Developers/Api/Service-Api).
 * This is a Singleton class and will have only one instance.
 *
 * @param server The [Server] where the requests will be made.
 */
class ServiceApi private constructor(private val server: Server) {

    /**
     * Sends a request to the api to obtain a generated password using user settings.
     *
     * @return A result with the password as aString if success, and an error code otherwise.
     */
    suspend fun password(
        strength: Int,
        includeDigits: Boolean,
        includeSymbols: Boolean,
        sessionCode: String?
    ): Result<String> {
        return try {
            val requestBody = Json.encodeToString(
                RequestedPassword(strength, includeDigits, includeSymbols)
            )

            val apiResponse = withContext(Dispatchers.IO) {
                OkHttpRequestInterface.getInstance().post(
                    sUrl = server.url + PASSWORD_URL,
                    sessionCode = sessionCode,
                    username = server.username,
                    password = server.password,
                    body = requestBody,
                    mediaType = OkHttpRequest.JSON
                )
            }

            val code = apiResponse.code
            val body = withContext(Dispatchers.IO) { apiResponse.body.string() }
            withContext(Dispatchers.IO) {
                apiResponse.close()
            }

            if (code != 200) {
                Log.d("SERVICE API", "Code response $code")
                return Result.Error(Error.API_BAD_RESPONSE)
            }

            withContext(Dispatchers.Default) {
                Result.Success(Json.decodeFromString<GeneratedPassword>(body).password)
            }
        } catch (e: SocketTimeoutException) {
            if (BuildConfig.DEBUG) {
                e.printStackTrace()
            }
            Result.Error(Error.API_TIMEOUT)
        } catch (e: SSLHandshakeException) {
            if (BuildConfig.DEBUG) {
                e.printStackTrace()
            }
            Result.Error(Error.SSL_HANDSHAKE_EXCEPTION)
        } catch (e: SsoReauthenticationRequiredException) {
            Timber.e(e)
            Result.Error(Error.SSO_REAUTHENTICATION_REQUIRED)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                e.printStackTrace()
            }
            Result.Error(Error.UNKNOWN)
        }
    }

    fun getFaviconUrl(url: String): String =
        server.url + String.format(
            Locale.getDefault(),
            FAVICON_URL,
            URLEncoder.encode(url, "utf-8"),
            256
        )

    fun getAvatarUrl(): String =
        server.url + String.format(
            Locale.getDefault(),
            AVATAR_URL,
            URLEncoder.encode(server.username, "utf-8"),
            256
        )

    suspend fun getFaviconBytes(domain: String, sessionCode: String?) = getRawBytes {
        OkHttpRequestInterface.getInstance().get(
            sUrl = getFaviconUrl(domain),
            sessionCode = sessionCode,
            username = server.username,
            password = server.password
        )
    }

    suspend fun getAvatarBytes(sessionCode: String?) = getRawBytes {
        OkHttpRequestInterface.getInstance().get(
            sUrl = getAvatarUrl(),
            sessionCode = sessionCode,
            username = server.username,
            password = server.password
        )
    }

    private suspend fun getRawBytes(response: suspend () -> Response): ByteArray? {
        return try {
            withContext(Dispatchers.IO) {
                response().use { response ->
                    if (response.code == 200) response.body.bytes() else null
                }
            }
        } catch (e: MalformedURLException) {
            Log.d(TAG, "getRawBytes: ", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.d(TAG, "getRawBytes: ", e)
            null
        } catch (e: SsoReauthenticationRequiredException) {
            throw e
        } catch (e: IOException) {
            Log.d(TAG, "getRawBytes: ", e)
            null
        } catch (e: IllegalStateException) {
            Log.d(TAG, "getRawBytes: ", e)
            null
        }
    }

    companion object {
        private const val FAVICON_URL = "/index.php/apps/passwords/api/1.0/service/favicon/%s/%d"
        private const val PASSWORD_URL = "/index.php/apps/passwords/api/1.0/service/password"
        private const val AVATAR_URL = "/index.php/apps/passwords/api/1.0/service/avatar/%s/%d"
        private const val TAG = "ServiceApi"

        private var instance: ServiceApi? = null

        /**
         * Get the instance of the [ServiceApi], and create it if null.
         *
         * @param server The [Server] where the requests will be made.
         * @return The instance of the api.
         */
        fun getInstance(server: Server): ServiceApi {
            synchronized(this) {
                var tempInstance = instance

                if (tempInstance == null || tempInstance.server != server) {
                    tempInstance = ServiceApi(server)
                    instance = tempInstance
                }

                return tempInstance
            }
        }
    }
}
