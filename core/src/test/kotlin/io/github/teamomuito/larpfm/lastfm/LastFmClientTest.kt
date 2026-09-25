package io.github.teamomuito.larpfm.lastfm

import io.github.teamomuito.larpfm.core.Scrobble
import io.github.teamomuito.larpfm.core.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LastFmClientTest {
    private val requests = mutableListOf<Map<String, String>>()
    private val logged = mutableListOf<String>()

    private fun client(code: Int = 200, body: String) = LastFmClient(
        apiKey = "key",
        apiSecret = "secret",
        transport = { url, form ->
            assertEquals(LastFmClient.API_URL, url)
            requests += form
            HttpResponse(code, body)
        },
        onResponse = { method, _ -> logged += method },
    )

    @Test
    fun `a reply without scrobble details is not counted as sent`() {
        try {
            client(body = """{"status":"ok"}""").scrobble("sk", listOf(Scrobble(Track("A", "B"), 1)))
            fail("expected an exception")
        } catch (e: LastFmException) {
            assertTrue("so the scrobble stays queued and is retried", e.isRetryable)
        }
    }

    @Test
    fun `totals decide when per-scrobble details are missing`() {
        val accepted = client(body = """{"scrobbles":{"@attr":{"accepted":2,"ignored":0}}}""")
            .scrobble("sk", listOf(Scrobble(Track("A", "B"), 1), Scrobble(Track("C", "D"), 2)))
        val rejected = client(body = """{"scrobbles":{"@attr":{"accepted":0,"ignored":1}}}""")
            .scrobble("sk", listOf(Scrobble(Track("A", "B"), 1)))

        assertEquals(listOf(true, true), accepted.map { it.accepted })
        assertEquals("Last.fm didn't accept it", rejected.single().reason)
    }

    @Test
    fun `totals of zero accepted win over details that look accepted`() {
        val body = """
            {"scrobbles":{"scrobble":{"ignoredMessage":{"code":"0","#text":""}},"@attr":{"accepted":0,"ignored":1}}}
        """.trimIndent()

        val result = client(body = body).scrobble("sk", listOf(Scrobble(Track("A", "B"), 1))).single()

        assertFalse(result.accepted)
    }

    @Test
    fun `recent tracks are read without signing`() {
        val body = """
            {"recenttracks":{"track":[
              {"artist":{"mbid":"","#text":"Now Artist"},"name":"Now Song","@attr":{"nowplaying":"true"}},
              {"artist":{"mbid":"","#text":"Old Artist"},"name":"Old Song","date":{"uts":"1700000000","#text":"14 Nov 2023"}}
            ],"@attr":{"user":"rj","total":"1234","page":"1"}}}
        """.trimIndent()

        val recent = client(body = body).getRecentTracks("rj", limit = 5)

        assertEquals(1234L, recent.total)
        assertEquals(
            listOf(
                RecentTrack("Now Artist", "Now Song", nowPlaying = true, timestampSec = null),
                RecentTrack("Old Artist", "Old Song", nowPlaying = false, timestampSec = 1_700_000_000),
            ),
            recent.tracks,
        )
        val form = requests.single()
        assertEquals("user.getRecentTracks", form["method"])
        assertEquals("rj", form["user"])
        assertFalse("api_sig" in form)
    }

    @Test
    fun `responses are reported, except sign-in which carries the session key`() {
        client(body = """{"session":{"name":"RJ","key":"abc123"}}""").getMobileSession("rj", "pw")
        client(body = """{"nowplaying":{}}""").updateNowPlaying("sk", Track("A", "B"))

        assertEquals(listOf("track.updateNowPlaying"), logged)
    }

    @Test
    fun `mobile session returns the session key`() {
        val session = client(body = """{"session":{"name":"RJ","key":"abc123","subscriber":0}}""")
            .getMobileSession("rj", "hunter2")

        assertEquals(Session("RJ", "abc123"), session)
        val form = requests.single()
        assertEquals("auth.getMobileSession", form["method"])
        assertEquals("key", form["api_key"])
        assertEquals("json", form["format"])
        assertEquals(LastFmSignature.sign(form - "api_sig" - "format", "secret"), form["api_sig"])
    }

    @Test
    fun `api errors become LastFmException`() {
        try {
            client(code = 403, body = """{"error":4,"message":"Authentication Failed"}""")
                .getMobileSession("rj", "wrong")
            fail("expected an exception")
        } catch (e: LastFmException) {
            assertEquals(4, e.code)
            assertEquals("Authentication Failed", e.message)
            assertTrue(e.isAuthError)
            assertFalse(e.isRetryable)
        }
    }

    @Test
    fun `non json responses are retryable`() {
        try {
            client(code = 503, body = "<html>Service Unavailable</html>").updateNowPlaying("sk", Track("A", "B"))
            fail("expected an exception")
        } catch (e: LastFmException) {
            assertTrue(e.isRetryable)
        }
    }

    @Test
    fun `now playing sends track fields`() {
        client(body = """{"nowplaying":{}}""")
            .updateNowPlaying("sk", Track("Artist", "Title", "Album", "Album Artist", durationMs = 215_900))

        val form = requests.single()
        assertEquals("track.updateNowPlaying", form["method"])
        assertEquals("Artist", form["artist"])
        assertEquals("Title", form["track"])
        assertEquals("Album", form["album"])
        assertEquals("Album Artist", form["albumArtist"])
        assertEquals("215", form["duration"])
        assertEquals("sk", form["sk"])
    }

    @Test
    fun `scrobble batch uses indexed params and parses each result`() {
        val body = """
            {"scrobbles":{"scrobble":[
              {"artist":{"#text":"A"},"ignoredMessage":{"code":"0","#text":""}},
              {"artist":{"#text":"C"},"ignoredMessage":{"code":"3","#text":""}}
            ],"@attr":{"accepted":1,"ignored":1}}}
        """.trimIndent()
        val results = client(body = body).scrobble(
            "sk",
            listOf(
                Scrobble(Track("A", "B"), 1_700_000_000),
                Scrobble(Track("C", "D", album = "E"), 1_700_000_300),
            ),
        )

        assertEquals(listOf(true, false), results.map { it.accepted })
        assertEquals("Timestamp too old", results[1].reason)
        val form = requests.single()
        assertEquals("A", form["artist[0]"])
        assertEquals("D", form["track[1]"])
        assertEquals("E", form["album[1]"])
        assertEquals("1700000300", form["timestamp[1]"])
        assertFalse("album[0]" in form)
    }

    @Test
    fun `single scrobble result comes back as an object`() {
        val body = """
            {"scrobbles":{"scrobble":{"ignoredMessage":{"code":"5","#text":""}},"@attr":{"accepted":0,"ignored":1}}}
        """.trimIndent()
        val results = client(body = body).scrobble("sk", listOf(Scrobble(Track("A", "B"), 1)))

        assertTrue(results.single().isDailyLimit)
    }
}
