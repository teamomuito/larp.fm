package io.github.teamomuito.larpfm.core

import org.junit.Assert.assertEquals
import org.junit.Test

class LarpTest {
    // 12:00 UTC.
    private val original = Scrobble(Track("Iron Maiden", "Aces High", durationMs = 271_000), 1_700_049_600)

    @Test
    fun `copies are an hour apart after the original`() {
        val copies = Larp.copies(original, 3)

        // 13:00 and 14:00.
        assertEquals(listOf(1_700_053_200L, 1_700_056_800L), copies.map { it.timestampSec })
        copies.forEach { assertEquals(original.track, it.track) }
    }

    @Test
    fun `once means no copies`() {
        assertEquals(emptyList<Scrobble>(), Larp.copies(original, 1))
        assertEquals(emptyList<Scrobble>(), Larp.copies(original, 0))
    }

    @Test
    fun `no more than ten plays in total`() {
        assertEquals(Larp.MAX_TIMES - 1, Larp.copies(original, 50).size)
    }
}
