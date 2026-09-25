package io.github.teamomuito.larpfm.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleCleanerTest {
    private fun clean(title: String) = TitleCleaner.stripBrackets(title)

    @Test
    fun `removes parentheses and brackets with the space before them`() {
        assertEquals("Iron Maiden", clean("Iron Maiden (Remaster) [Special]"))
        assertEquals("Abbey Road", clean("Abbey Road (Remastered 2009)"))
        assertEquals("Rumours", clean("Rumours [Super Deluxe]"))
    }

    @Test
    fun `groups in the middle or at the start leave single spaces`() {
        assertEquals("Hello Goodbye", clean("Hello (Deluxe) Goodbye"))
        assertEquals("Morning Glory?", clean("(What's the Story) Morning Glory?"))
    }

    @Test
    fun `nested groups are removed whole`() {
        assertEquals("Album", clean("Album (Deluxe (2011 Remaster))"))
        assertEquals("Album", clean("Album [Live [Disc 1]]"))
    }

    @Test
    fun `titles without brackets are unchanged`() {
        assertEquals("The Number of the Beast", clean("The Number of the Beast"))
        assertEquals("Unclosed (paren", clean("Unclosed (paren"))
    }

    @Test
    fun `keeps the original when nothing would be left`() {
        assertEquals("[Untitled]", clean("[Untitled]"))
        assertEquals("(Remaster)", clean("  (Remaster) "))
    }
}
