package io.github.teamomuito.larpfm.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistNamesTest {
    private fun first(artist: String) = ArtistNames.first(artist)

    @Test
    fun `keeps the first of several artists`() {
        assertEquals("Artist A", first("Artist A, Artist B"))
        assertEquals("Artist A", first("Artist A, Artist B & Artist C"))
        assertEquals("Artist A", first("Artist A & Artist B"))
        assertEquals("Artist A", first("Artist A; Artist B"))
        assertEquals("Artist A", first("Artist A / Artist B"))
    }

    @Test
    fun `featured artists and collabs`() {
        assertEquals("Artist A", first("Artist A feat. Artist B"))
        assertEquals("Artist A", first("Artist A Feat Artist B"))
        assertEquals("Artist A", first("Artist A ft. Artist B"))
        assertEquals("Artist A", first("Artist A featuring Artist B"))
        assertEquals("Artist A", first("Artist A x Artist B"))
        assertEquals("Artist A", first("Artist A X Artist B"))
    }

    @Test
    fun `single artists are unchanged`() {
        assertEquals("Iron Maiden", first("Iron Maiden"))
        assertEquals("AC/DC", first("AC/DC"))
        assertEquals("Lil Nas X", first("Lil Nas X"))
        assertEquals("Charli xcx", first("Charli xcx"))
        assertEquals("The xx", first("The xx"))
    }

    @Test
    fun `keeps the original when nothing comes before the separator`() {
        assertEquals(", Artist", first(", Artist"))
    }
}
