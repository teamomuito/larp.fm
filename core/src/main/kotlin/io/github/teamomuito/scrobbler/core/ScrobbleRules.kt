package io.github.teamomuito.scrobbler.core

/**
 * Last.fm's scrobbling rules: a track must be longer than 30 seconds, and it counts once it has
 * played for half its length or for 4 minutes, whichever comes first.
 */
object ScrobbleRules {
    const val MIN_TRACK_LENGTH_MS = 30_000L
    const val MAX_THRESHOLD_MS = 240_000L

    /**
     * How long [durationMs] worth of track must play before it can be scrobbled, or null if it is
     * too short to ever count. Tracks of unknown length need the full 4 minutes.
     */
    fun thresholdMs(durationMs: Long): Long? = when {
        durationMs <= 0 -> MAX_THRESHOLD_MS
        durationMs <= MIN_TRACK_LENGTH_MS -> null
        else -> minOf(durationMs / 2, MAX_THRESHOLD_MS)
    }
}
