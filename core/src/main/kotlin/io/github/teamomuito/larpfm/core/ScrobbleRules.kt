package io.github.teamomuito.larpfm.core

/**
 * When a track counts as played. Last.fm's own rule is half the track or 4 minutes, whichever
 * comes first, for tracks longer than 30 seconds; the percentage here can be lowered from half,
 * down to 0%, which scrobbles a track as soon as it starts.
 */
object ScrobbleRules {
    const val MIN_TRACK_LENGTH_MS = 30_000L
    const val MAX_THRESHOLD_MS = 240_000L
    const val DEFAULT_PERCENT = 50
    const val MIN_PERCENT = 0
    const val MAX_PERCENT = 50

    /**
     * How long a track of [durationMs] must play before it can be scrobbled, or null if it is
     * too short to ever count. Tracks of unknown length need the full 4 minutes, unless [percent]
     * is 0.
     */
    fun thresholdMs(durationMs: Long, percent: Int = DEFAULT_PERCENT): Long? {
        val clamped = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)
        return when {
            durationMs <= 0 -> if (clamped == 0) 0 else MAX_THRESHOLD_MS
            durationMs <= MIN_TRACK_LENGTH_MS -> null
            else -> minOf(durationMs * clamped / 100, MAX_THRESHOLD_MS)
        }
    }
}
