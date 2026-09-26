package io.github.teamomuito.larpfm.core

/** Auto-LARP: extra scrobbles of a play, queued an hour apart after the original. */
object Larp {
    /** A play can count at most this many times, the original included. */
    const val MAX_TIMES = 10

    /** Each copy is timestamped this long after the one before, and sent once that time comes. */
    const val INTERVAL_SEC = 60 * 60L

    /**
     * Copies stop going out once this many scrobbles were sent in the last 24 hours, leaving room
     * for real plays under Last.fm's limit of 2,800 a day.
     */
    const val DAILY_BUDGET = 2_300

    /** The extra copies that make [original] count [times] times: an hour after it, two hours after it, and so on. */
    fun copies(original: Scrobble, times: Int): List<Scrobble> =
        (1 until times.coerceIn(1, MAX_TIMES)).map {
            original.copy(timestampSec = original.timestampSec + it * INTERVAL_SEC)
        }
}
