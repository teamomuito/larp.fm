package io.github.teamomuito.scrobbler.core

data class Track(
    val artist: String,
    val title: String,
    val album: String? = null,
    val albumArtist: String? = null,
    /** Length of the track, or 0 when the player doesn't report it. */
    val durationMs: Long = 0,
) {
    /** Players often re-send metadata for the same song, e.g. to add artwork or the duration. */
    fun isSameSongAs(other: Track): Boolean = artist == other.artist && title == other.title
}

data class Scrobble(
    val track: Track,
    /** When the track started playing, in seconds since the Unix epoch. */
    val timestampSec: Long,
)
