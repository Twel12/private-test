package com.hegocre.nextcloudpasswords.utils

object Error {
    const val API_TIMEOUT = 10000
    const val API_NO_CSE = 10001
    const val API_BAD_RESPONSE = 10002
    const val API_NO_SESSION = 10003
    const val API_SESSION_EXPIRED = 10004

    const val SSL_HANDSHAKE_EXCEPTION = 20000

    const val SSO_REAUTHENTICATION_REQUIRED = 30000
    const val TWO_FACTOR_APP_PASSWORD_REQUIRED = 30001

    const val UNKNOWN = -1
}
