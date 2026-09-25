package io.github.teamomuito.larpfm.lastfm

import io.github.teamomuito.larpfm.core.Scrobble
import io.github.teamomuito.larpfm.core.Track
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

data class Session(val username: String, val key: String)

/** Last.fm's verdict on one scrobble; [ignoredCode] is 0 when it was accepted. */
data class ScrobbleResult(val ignoredCode: Int, val ignoredMessage: String) {
    val accepted: Boolean get() = ignoredCode == 0

    /** Last.fm will take it tomorrow; keep it queued. */
    val isDailyLimit: Boolean get() = ignoredCode == DAILY_LIMIT_EXCEEDED

    val reason: String
        get() = ignoredMessage.ifBlank {
            when (ignoredCode) {
                1 -> "Artist ignored"
                2 -> "Track ignored"
                3 -> "Timestamp too old"
                4 -> "Timestamp too new"
                DAILY_LIMIT_EXCEEDED -> "Daily scrobble limit exceeded"
                NOT_ACCEPTED -> "Last.fm didn't accept it"
                else -> "Ignored by Last.fm"
            }
        }

    companion object {
        const val DAILY_LIMIT_EXCEEDED = 5

        /** Last.fm's totals said it wasn't accepted, without giving a reason. */
        const val NOT_ACCEPTED = -1
        val ACCEPTED = ScrobbleResult(0, "")
    }
}

/** A track in a user's Last.fm history, as Last.fm itself reports it. */
data class RecentTrack(
    val artist: String,
    val title: String,
    val nowPlaying: Boolean,
    /** When it was scrobbled; null for the track that's playing now. */
    val timestampSec: Long?,
)

data class RecentTracks(
    /** The user's total scrobble count. */
    val total: Long?,
    val tracks: List<RecentTrack>,
)

/** Blocking client for the parts of the Last.fm API a scrobbler needs. Call it off the main thread. */
class LastFmClient(
    private val apiKey: String,
    private val apiSecret: String,
    private val transport: HttpTransport,
    /** Sees every raw response, for troubleshooting. Not called for sign-in, whose response holds the session key. */
    private val onResponse: (method: String, response: HttpResponse) -> Unit = { _, _ -> },
) {
    /** Signs in with a username (or email) and password. The password is not kept. */
    @Throws(IOException::class, LastFmException::class)
    fun getMobileSession(username: String, password: String): Session {
        val response = call(
            mapOf(
                "method" to "auth.getMobileSession",
                "username" to username,
                "password" to password,
            ),
        )
        val session = response.optJSONObject("session")
            ?: throw LastFmException(LastFmException.UNEXPECTED_RESPONSE, "Last.fm returned no session")
        val key = session.optString("key")
        if (key.isEmpty()) throw LastFmException(LastFmException.UNEXPECTED_RESPONSE, "Last.fm returned no session key")
        return Session(session.optString("name").ifEmpty { username }, key)
    }

    @Throws(IOException::class, LastFmException::class)
    fun updateNowPlaying(sessionKey: String, track: Track) {
        val params = mutableMapOf("method" to "track.updateNowPlaying", "sk" to sessionKey)
        params.putTrack(track, suffix = "")
        call(params)
    }

    /** Submits up to [MAX_BATCH_SIZE] scrobbles and returns Last.fm's verdict on each, in order. */
    @Throws(IOException::class, LastFmException::class)
    fun scrobble(sessionKey: String, scrobbles: List<Scrobble>): List<ScrobbleResult> {
        require(scrobbles.size in 1..MAX_BATCH_SIZE) { "Can send 1 to $MAX_BATCH_SIZE scrobbles, got ${scrobbles.size}" }
        val params = mutableMapOf("method" to "track.scrobble", "sk" to sessionKey)
        scrobbles.forEachIndexed { i, scrobble ->
            params.putTrack(scrobble.track, suffix = "[$i]")
            params["timestamp[$i]"] = scrobble.timestampSec.toString()
        }
        return parseScrobbleResults(call(params), scrobbles.size)
    }

    /** What Last.fm has actually recorded for [username], newest first. */
    @Throws(IOException::class, LastFmException::class)
    fun getRecentTracks(username: String, limit: Int): RecentTracks {
        val response = call(
            mapOf("method" to "user.getRecentTracks", "user" to username, "limit" to limit.toString()),
            signed = false,
        )
        return parseRecentTracks(response)
    }

    private fun MutableMap<String, String>.putTrack(track: Track, suffix: String) {
        put("artist$suffix", track.artist)
        put("track$suffix", track.title)
        track.album?.let { put("album$suffix", it) }
        track.albumArtist?.let { put("albumArtist$suffix", it) }
        if (track.durationMs > 0) put("duration$suffix", (track.durationMs / 1000).toString())
    }

    private fun call(params: Map<String, String>, signed: Boolean = true): JSONObject {
        val withKey = params + ("api_key" to apiKey)
        val form = (if (signed) withKey + ("api_sig" to LastFmSignature.sign(withKey, apiSecret)) else withKey) +
            ("format" to "json")
        val method = params["method"].orEmpty()
        val response = transport.post(API_URL, form)
        if (method != "auth.getMobileSession") onResponse(method, response)
        val json = try {
            JSONObject(response.body)
        } catch (e: JSONException) {
            throw LastFmException(LastFmException.UNEXPECTED_RESPONSE, "Unexpected response from Last.fm (HTTP ${response.code})")
        }
        if (json.has("error")) {
            throw LastFmException(json.optInt("error"), json.optString("message").ifEmpty { "Last.fm error" })
        }
        if (response.code !in 200..299) {
            throw LastFmException(LastFmException.UNEXPECTED_RESPONSE, "Unexpected response from Last.fm (HTTP ${response.code})")
        }
        return json
    }

    companion object {
        const val API_URL = "https://ws.audioscrobbler.com/2.0/"
        const val MAX_BATCH_SIZE = 50

        /** Last.fm returns a single item as an object rather than a one-element array. */
        private fun JSONObject.objects(name: String): List<JSONObject> = when (val raw = opt(name)) {
            is JSONObject -> listOf(raw)
            is JSONArray -> (0 until raw.length()).mapNotNull { raw.optJSONObject(it) }
            else -> emptyList()
        }

        private fun JSONObject.int(name: String): Int? = opt(name)?.toString()?.toIntOrNull()

        internal fun parseScrobbleResults(json: JSONObject, count: Int): List<ScrobbleResult> {
            // Only count scrobbles as sent when Last.fm says so; anything else is retried.
            val scrobbles = json.optJSONObject("scrobbles")
                ?: throw LastFmException(LastFmException.UNEXPECTED_RESPONSE, "Last.fm didn't confirm the scrobbles")
            val acceptedTotal = scrobbles.optJSONObject("@attr")?.int("accepted")
            val items = scrobbles.objects("scrobble")
            if (items.size != count) {
                return when (acceptedTotal) {
                    count -> List(count) { ScrobbleResult.ACCEPTED }
                    0 -> List(count) { ScrobbleResult(ScrobbleResult.NOT_ACCEPTED, "") }
                    else -> throw LastFmException(LastFmException.UNEXPECTED_RESPONSE, "Last.fm didn't confirm the scrobbles")
                }
            }
            val results = items.map { item ->
                val ignored = item.optJSONObject("ignoredMessage")
                ScrobbleResult(
                    ignoredCode = ignored?.int("code") ?: 0,
                    ignoredMessage = ignored?.optString("#text").orEmpty(),
                )
            }
            // The totals win if they say nothing went through.
            if (acceptedTotal == 0 && results.any { it.accepted }) {
                return results.map { if (it.accepted) ScrobbleResult(ScrobbleResult.NOT_ACCEPTED, "") else it }
            }
            return results
        }

        internal fun parseRecentTracks(json: JSONObject): RecentTracks {
            val recent = json.optJSONObject("recenttracks")
                ?: throw LastFmException(LastFmException.UNEXPECTED_RESPONSE, "Last.fm returned no recent tracks")
            val tracks = recent.objects("track").map { item ->
                RecentTrack(
                    artist = item.optJSONObject("artist")?.optString("#text").orEmpty(),
                    title = item.optString("name"),
                    nowPlaying = item.optJSONObject("@attr")?.optString("nowplaying") == "true",
                    timestampSec = item.optJSONObject("date")?.optString("uts")?.toLongOrNull(),
                )
            }
            val total = recent.optJSONObject("@attr")?.optString("total")?.toLongOrNull()
            return RecentTracks(total, tracks)
        }
    }
}
