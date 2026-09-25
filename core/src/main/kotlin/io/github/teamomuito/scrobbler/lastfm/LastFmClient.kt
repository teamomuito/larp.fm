package io.github.teamomuito.scrobbler.lastfm

import io.github.teamomuito.scrobbler.core.Scrobble
import io.github.teamomuito.scrobbler.core.Track
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
                else -> "Ignored by Last.fm"
            }
        }

    companion object {
        const val DAILY_LIMIT_EXCEEDED = 5
        val ACCEPTED = ScrobbleResult(0, "")
    }
}

/** Blocking client for the parts of the Last.fm API a scrobbler needs. Call it off the main thread. */
class LastFmClient(
    private val apiKey: String,
    private val apiSecret: String,
    private val transport: HttpTransport,
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

    private fun MutableMap<String, String>.putTrack(track: Track, suffix: String) {
        put("artist$suffix", track.artist)
        put("track$suffix", track.title)
        track.album?.let { put("album$suffix", it) }
        track.albumArtist?.let { put("albumArtist$suffix", it) }
        if (track.durationMs > 0) put("duration$suffix", (track.durationMs / 1000).toString())
    }

    private fun call(params: Map<String, String>): JSONObject {
        val signed = params + ("api_key" to apiKey)
        val form = signed + ("api_sig" to LastFmSignature.sign(signed, apiSecret)) + ("format" to "json")
        val response = transport.post(API_URL, form)
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

        internal fun parseScrobbleResults(json: JSONObject, count: Int): List<ScrobbleResult> {
            val items = when (val raw = json.optJSONObject("scrobbles")?.opt("scrobble")) {
                // A single scrobble comes back as an object rather than a one-element array.
                is JSONObject -> listOf(raw)
                is JSONArray -> (0 until raw.length()).mapNotNull { raw.optJSONObject(it) }
                else -> emptyList()
            }
            // The request succeeded; if the details are missing, assume everything went through.
            if (items.size != count) return List(count) { ScrobbleResult.ACCEPTED }
            return items.map { item ->
                val ignored = item.optJSONObject("ignoredMessage")
                ScrobbleResult(
                    ignoredCode = ignored?.opt("code")?.toString()?.toIntOrNull() ?: 0,
                    ignoredMessage = ignored?.optString("#text").orEmpty(),
                )
            }
        }
    }
}
