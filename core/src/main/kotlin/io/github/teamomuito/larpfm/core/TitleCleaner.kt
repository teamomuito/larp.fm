package io.github.teamomuito.larpfm.core

object TitleCleaner {
    // Only groups with no brackets inside, so nested ones come out innermost first.
    private val BRACKETED = Regex("""\s*(\([^()]*\)|\[[^\[\]]*\])""")
    private val SPACES = Regex("""\s+""")

    /**
     * Removes everything in (parentheses) or [square brackets], such as "(2011 Remaster)" or
     * "[Deluxe Edition]". Returns [title] unchanged if nothing would be left.
     */
    fun stripBrackets(title: String): String {
        var result = title
        while (true) {
            val next = BRACKETED.replace(result, "")
            if (next == result) break
            result = next
        }
        return SPACES.replace(result, " ").trim().ifEmpty { title.trim() }
    }
}
