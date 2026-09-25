package io.github.teamomuito.larpfm.core

object ArtistNames {
    // Commas, "&" and ";" anywhere; "/" only with spaces around it, so "AC/DC" survives.
    private val SEPARATOR = Regex("""\s*[,&;]\s*|\s+/\s+|\s+(?:feat\.?|ft\.?|featuring|x)\s+""", RegexOption.IGNORE_CASE)

    /**
     * The first name in a multi-artist credit, e.g. "Artist A" from "Artist A, Artist B" or
     * "Artist A feat. Artist B". Returns [artist] unchanged if there's nothing before the first separator.
     */
    fun first(artist: String): String =
        artist.split(SEPARATOR, limit = 2).first().trim().ifEmpty { artist.trim() }
}
