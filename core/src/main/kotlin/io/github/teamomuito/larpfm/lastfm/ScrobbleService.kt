package io.github.teamomuito.larpfm.lastfm

/** A scrobbling site that speaks the Last.fm API. */
enum class ScrobbleService(
    val title: String,
    val apiUrl: String,
    /** Where the user approves the app in the browser. */
    val authUrl: String,
    /** The API account to use on this site, or null when the user brings their own. */
    val fixedApiKey: String?,
    val fixedApiSecret: String?,
) {
    LASTFM(
        title = "Last.fm",
        apiUrl = "https://ws.audioscrobbler.com/2.0/",
        authUrl = "https://www.last.fm/api/auth/",
        fixedApiKey = null,
        fixedApiSecret = null,
    ),

    /** Libre.fm runs GNU FM, which copies the Last.fm API. It doesn't register API keys, so any 32 characters will do. */
    LIBREFM(
        title = "Libre.fm",
        apiUrl = "https://libre.fm/2.0/",
        authUrl = "https://libre.fm/api/auth/",
        fixedApiKey = "6b90300abf55fb097990291270288e61",
        fixedApiSecret = "18932ad3261c74e5437b27a34ea90aef",
    ),
    ;

    companion object {
        fun of(name: String?): ScrobbleService = entries.firstOrNull { it.name == name } ?: LASTFM
    }
}
