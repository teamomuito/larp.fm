package io.github.teamomuito.scrobbler.lastfm

import org.junit.Assert.assertEquals
import org.junit.Test

class LastFmSignatureTest {
    @Test
    fun `signs sorted params with the secret and skips format`() {
        val params = mapOf(
            "method" to "track.scrobble",
            "api_key" to "key123",
            "sk" to "sess",
            "artist[0]" to "Björk",
            "track[0]" to "Jóga",
            "timestamp[0]" to "1700000000",
            "artist[1]" to "A",
            "track[1]" to "B",
            "timestamp[1]" to "1700000300",
            "format" to "json",
        )

        // md5("api_keykey123artist[0]Björkartist[1]Amethodtrack.scrobble...track[1]Bsecret456")
        assertEquals("1315b8115fb74b05a1940aafd6385eab", LastFmSignature.sign(params, "secret456"))
    }
}
