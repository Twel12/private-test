package com.hegocre.nextcloudpasswords.api

import com.hegocre.nextcloudpasswords.BuildConfig
import com.hegocre.nextcloudpasswords.api.exceptions.HttpStatusException
import com.hegocre.nextcloudpasswords.api.exceptions.SsoReauthenticationRequiredException
import com.hegocre.nextcloudpasswords.api.exceptions.twoFactorErrorCodeOrNull
import com.hegocre.nextcloudpasswords.data.serversettings.ServerSettings
import com.hegocre.nextcloudpasswords.utils.AppPasswordRequest
import com.hegocre.nextcloudpasswords.utils.Error
import com.hegocre.nextcloudpasswords.utils.OkHttpRequestInterface as OkHttpRequest
import com.hegocre.nextcloudpasswords.utils.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException

class SettingsApi private constructor(private val server: Server) {

    /**
     * Sends a request to the api to obtain required user settings. No session is required to send this request.
     *
     * @return A result with the [ServerSettings] object if success, and an error code otherwise.
     */
    suspend fun get(): Result<ServerSettings> {
        return try {
            val apiResponse = withContext(Dispatchers.IO) {
                OkHttpRequest.getInstance().post(
                    sUrl = server.url + GET_URL,
                    body = ServerSettings.getRequestBody(),
                    mediaType = OkHttpRequest.JSON,
                    username = server.username,
                    password = server.password,
                )
            }

            val code = apiResponse.code
            val body = withContext(Dispatchers.IO) { apiResponse.body.string() }

            withContext(Dispatchers.IO) {
                apiResponse.close()
            }

            if (code == 200) {
                Result.Success(Json.decodeFromString(body))
            } else {
                Result.Error(Error.API_BAD_RESPONSE)
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
        } catch (e: HttpStatusException) {
            if (e.statusCode == HttpURLConnection.HTTP_SEE_OTHER) {
                Result.Error(Error.TWO_FACTOR_APP_PASSWORD_REQUIRED)
            } else {
                Result.Error(Error.API_BAD_RESPONSE)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mapUnexpectedError(e)
        }

    }

    private fun mapUnexpectedError(e: Exception): Result<ServerSettings> {
        val twoFactorError = e.twoFactorErrorCodeOrNull()
        return when {
            twoFactorError != null -> Result.Error(twoFactorError)
            AppPasswordRequest.isBlockingRequests() -> Result.Error(Error.API_BAD_RESPONSE)
            else -> {
                if (BuildConfig.DEBUG) e.printStackTrace()
                Result.Error(Error.UNKNOWN)
            }
        }
    }

    suspend fun getUserSetting(key: String, sessionCode: String? = null): Result<String?> {
        return try {
            val apiResponse = withContext(Dispatchers.IO) {
                OkHttpRequest.getInstance().post(
                    sUrl = server.url + GET_URL,
                    sessionCode = sessionCode,
                    body = Json.encodeToString(listOf(key)),
                    mediaType = OkHttpRequest.JSON,
                    username = server.username,
                    password = server.password,
                )
            }

            val code = apiResponse.code
            val body = withContext(Dispatchers.IO) {
                apiResponse.use { it.body.string() }
            }

            if (code == HttpURLConnection.HTTP_PRECON_FAILED) {
                return Result.Error(Error.API_SESSION_EXPIRED)
            }

            if (code != 200) {
                return Result.Error(Error.API_BAD_RESPONSE)
            }

            Result.Success(extractStringValue(body, key))
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
        } catch (e: HttpStatusException) {
            if (e.statusCode == HttpURLConnection.HTTP_PRECON_FAILED) {
                Result.Error(Error.API_SESSION_EXPIRED)
            } else {
                Result.Error(Error.API_BAD_RESPONSE)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) e.printStackTrace()
            Result.Error(Error.UNKNOWN)
        }
    }

    suspend fun setUserSetting(
        key: String,
        value: String,
        sessionCode: String? = null,
    ): Result<Unit> {
        return try {
            val body = Json.encodeToString(
                JsonObject(mapOf(key to JsonPrimitive(value)))
            )
            val apiResponse = withContext(Dispatchers.IO) {
                OkHttpRequest.getInstance().post(
                    sUrl = server.url + SET_URL,
                    sessionCode = sessionCode,
                    body = body,
                    mediaType = OkHttpRequest.JSON,
                    username = server.username,
                    password = server.password,
                )
            }

            val code = withContext(Dispatchers.IO) { apiResponse.use { it.code } }

            if (code == HttpURLConnection.HTTP_PRECON_FAILED) {
                return Result.Error(Error.API_SESSION_EXPIRED)
            }

            if (code !in 200..201) {
                return Result.Error(Error.API_BAD_RESPONSE)
            }

            Result.Success(Unit)
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
        } catch (e: HttpStatusException) {
            if (e.statusCode == HttpURLConnection.HTTP_PRECON_FAILED) {
                Result.Error(Error.API_SESSION_EXPIRED)
            } else {
                Result.Error(Error.API_BAD_RESPONSE)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) e.printStackTrace()
            Result.Error(Error.UNKNOWN)
        }
    }

    private fun extractStringValue(body: String, key: String): String? {
        val obj = Json.parseToJsonElement(body) as? JsonObject ?: return null
        val value = obj[key] ?: return null
        if (value is JsonNull) return null
        return (value as? JsonPrimitive)?.contentOrNull
    }

    companion object {
        private const val GET_URL = "/index.php/apps/passwords/api/1.0/settings/get"
        private const val SET_URL = "/index.php/apps/passwords/api/1.0/settings/set"

        private var instance: SettingsApi? = null

        /**
         * Get the instance of the [ServiceApi], and create it if null.
         *
         * @param server The [Server] where the requests will be made.
         * @return The instance of the api.
         */
        fun getInstance(server: Server): SettingsApi {
            synchronized(this) {
                var tempInstance = instance

                if (tempInstance == null || tempInstance.server != server) {
                    tempInstance = SettingsApi(server)
                    instance = tempInstance
                }

                return tempInstance
            }
        }
    }
}
