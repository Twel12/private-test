package com.hegocre.nextcloudpasswords.api.exceptions

import java.io.IOException

class SsoReauthenticationRequiredException(cause: Throwable) :
    IOException("SSO token is invalid, re-authentication required", cause)
