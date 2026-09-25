package io.github.teamomuito.larpfm.core

import org.junit.Assert.assertEquals
import org.junit.Test

class LarpTest {
    private val original = Scrobble(Track("Artist", "Song", album = "Album", durationMs = 200_900), 1_700_000_000)

    @Test
    fun `copies are backdated one track length apart`() {
        val copies = (1 until Larp.MAX_TIMES).map { Larp.copy(original, it) }

        assertEquals(9, copies.size)
        assertEquals(listOf(1_699_999_800L, 1_699_999_600L, 1_699_999_400L), copies.take(3).map { it.timestampSec })
        assertEquals("the 9th copy is 9 track lengths back", 1_700_000_000L - 9 * 200, copies.last().timestampSec)
        copies.forEach { assertEquals(original.track, it.track) }
    }

    @Test
    fun `unknown length uses three minutes`() {
        val copy = Larp.copy(original.copy(track = original.track.copy(durationMs = 0)), 2)

        assertEquals(1_700_000_000L - 360, copy.timestampSec)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `no more than ten plays in total`() {
        Larp.copy(original, Larp.MAX_TIMES)
    }

    @Test
    fun `album can be dropped`() {
        val track = Track("Artist", "Song", album = "Album", albumArtist = "Various", durationMs = 1)

        assertEquals(Track("Artist", "Song", durationMs = 1), track.withoutAlbum())
    }
}
