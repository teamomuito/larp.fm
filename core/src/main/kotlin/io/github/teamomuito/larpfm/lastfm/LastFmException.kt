package io.github.teamomuito.larpfm.lastfm

/** An error returned by the Last.fm API. Codes are listed at https://www.last.fm/api/errorcodes. */
class LastFmException(val code: Int, message: String) : Exception(message) {

    /** The request may succeed if it is sent again later. */
    val isRetryable: Boolean get() = code in RETRYABLE_CODES

    /** The session or API credentials are no good; the user has to sign in again. */
    val isAuthError: Boolean get() = code in AUTH_CODES

    companion object {
        /** Used when the response wasn't a Last.fm API response at all, e.g. an HTML error page. */
        const val UNEXPECTED_RESPONSE = -1

        const val AUTHENTICATION_FAILED = 4
        const val INVALID_SESSION_KEY = 9
        const val INVALID_API_KEY = 10
        const val INVALID_SIGNATURE = 13
        const val UNAUTHORIZED_TOKEN = 14
        const val TOKEN_EXPIRED = 15
        const val SUSPENDED_API_KEY = 26

        private val RETRYABLE_CODES = setOf(UNEXPECTED_RESPONSE, 8, 11, 16, 29)
        private val AUTH_CODES = setOf(
            AUTHENTICATION_FAILED,
            INVALID_SESSION_KEY,
            INVALID_API_KEY,
            INVALID_SIGNATURE,
            SUSPENDED_API_KEY,
        )
    }
}
