package io.github.teamomuito.larpfm.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackTest {
    @Test
    fun `album can be dropped`() {
        val track = Track("Artist", "Song", album = "Album", albumArtist = "Various", durationMs = 1)

        assertEquals(Track("Artist", "Song", durationMs = 1), track.withoutAlbum())
    }
}
