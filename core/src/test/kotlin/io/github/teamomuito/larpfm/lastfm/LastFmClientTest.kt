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

    private fun client(code: Int = 200, body: String) = LastFmClient("key", "secret") { url, form ->
        assertEquals(LastFmClient.API_URL, url)
        requests += form
        HttpResponse(code, body)
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
