package io.github.teamomuito.larpfm.core

/** Extra copies of a scrobble, as if the track had been played on repeat. */
object Larp {
    /** A play can count at most this many times, the original included. */
    const val MAX_TIMES = 5

    /** Last.fm ignores scrobbles more than 14 days old; leave a day's margin for the backdating. */
    const val MAX_AGE_SEC = 13 * 24 * 60 * 60L

    /** Spacing for tracks whose length the player didn't report. */
    private const val UNKNOWN_LENGTH_SEC = 180L

    /**
     * The [copy]th extra copy (1-based) of [original]. Last.fm drops a scrobble that repeats one
     * it already has, so each copy is backdated one track length further than the last.
     */
    fun copy(original: Scrobble, copy: Int): Scrobble {
        require(copy in 1 until MAX_TIMES) { "copy must be between 1 and ${MAX_TIMES - 1}, was $copy" }
        val lengthSec = original.track.durationMs / 1000
        val stepSec = if (lengthSec > 0) maxOf(lengthSec, ScrobbleRules.MIN_TRACK_LENGTH_MS / 1000) else UNKNOWN_LENGTH_SEC
        return original.copy(timestampSec = original.timestampSec - copy * stepSec)
    }
}
