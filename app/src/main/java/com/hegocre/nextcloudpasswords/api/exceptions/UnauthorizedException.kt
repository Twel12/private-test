package com.hegocre.nextcloudpasswords.api.exceptions

/**
 * Thrown when the server rejects the account credentials (HTTP 401). The Account Manager token is
 * no longer valid, so the account has to be re-authenticated before Passwords can be used again.
 */
class UnauthorizedException : Exception()
