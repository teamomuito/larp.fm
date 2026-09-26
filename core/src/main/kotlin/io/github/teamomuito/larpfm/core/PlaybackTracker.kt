package io.github.teamomuito.larpfm.core

import kotlin.math.abs

interface Clock {
    /** Monotonic time, used to measure how long a track has played. */
    fun elapsedMs(): Long

    /** Wall-clock time, used for scrobble timestamps. */
    fun epochMs(): Long
}

sealed interface TrackerEvent {
    data class NowPlaying(val track: Track) : TrackerEvent
    data class ScrobbleReady(val scrobble: Scrobble) : TrackerEvent
}

/** Where the player says it is: [ms] into the track as of [atElapsedMs] on the [Clock.elapsedMs] timeline. */
data class Position(val ms: Long, val atElapsedMs: Long, val speed: Float = 1f)

/**
 * Follows one media player's metadata and play/pause state, and decides when the current track
 * should be sent as "now playing" and when it has played long enough to be scrobbled.
 *
 * Only time spent playing counts; pauses don't. Each play is scrobbled at most once. When
 * [rescrobble] is on, a play that has been scrobbled ends when the song is paused and resumed or
 * jumps to another point (skipped back, looped, seeked), and a new play of the same song starts.
 * [thresholdPercent] and [rescrobble] are read on every check, so a changed setting applies to
 * the current track.
 */
class PlaybackTracker(
    private val clock: Clock,
    private val thresholdPercent: () -> Int = { ScrobbleRules.DEFAULT_PERCENT },
    private val rescrobble: () -> Boolean = { false },
) {
    var track: Track? = null
        private set
    var isPlaying: Boolean = false
        private set

    private var playedMs = 0L
    private var playingSinceMs = 0L
    private var startedAtEpochMs: Long? = null
    private var scrobbled = false
    private var lastPosition: Position? = null

    fun onMetadata(newTrack: Track?): List<TrackerEvent> {
        val current = track
        if (newTrack != null && current != null && newTrack.isSameSongAs(current)) {
            val durationMs = if (newTrack.durationMs > 0) newTrack.durationMs else current.durationMs
            track = newTrack.copy(durationMs = durationMs)
            // A newly reported duration can lower the threshold below what has already played.
            return listOfNotNull(checkThreshold())
        }

        val events = mutableListOf<TrackerEvent>()
        checkThreshold()?.let(events::add)
        track = newTrack
        playedMs = 0
        scrobbled = false
        startedAtEpochMs = null
        lastPosition = null
        if (isPlaying) {
            playingSinceMs = clock.elapsedMs()
            if (newTrack != null) {
                startedAtEpochMs = clock.epochMs()
                events += TrackerEvent.NowPlaying(newTrack)
            }
        }
        return events
    }

    /** [position] is where the player says it is, or null if it doesn't say. */
    fun onPlaybackState(playing: Boolean, position: Position? = null): List<TrackerEvent> {
        val jumped = position != null && jumped(position)
        lastPosition = position
        if (playing == isPlaying) {
            if (!playing || !jumped) return emptyList()
            // Skipped back, looped or seeked while playing.
            val events = listOfNotNull(checkThreshold())
            restartIfScrobbled()
            return events
        }
        val now = clock.elapsedMs()
        if (playing) {
            isPlaying = true
            playingSinceMs = now
            val current = track ?: return emptyList()
            restartIfScrobbled()
            if (startedAtEpochMs == null) startedAtEpochMs = clock.epochMs()
            return listOf(TrackerEvent.NowPlaying(current))
        }
        playedMs += now - playingSinceMs
        isPlaying = false
        return listOfNotNull(checkThreshold())
    }

    /** Whether the player is somewhere other than where it would have got to by just playing on. */
    private fun jumped(to: Position): Boolean {
        val from = lastPosition ?: return false
        val expectedMs = from.ms + if (isPlaying) ((to.atElapsedMs - from.atElapsedMs) * from.speed).toLong() else 0
        return abs(to.ms - expectedMs) > JUMP_TOLERANCE_MS
    }

    /** With [rescrobble] on, ends a play that has been scrobbled and starts a new play of the same song. */
    private fun restartIfScrobbled() {
        val previousStartMs = startedAtEpochMs
        if (!scrobbled || previousStartMs == null || !rescrobble()) return
        scrobbled = false
        playedMs = 0
        playingSinceMs = clock.elapsedMs()
        // Last.fm drops a scrobble with the same track and timestamp as one it already has.
        startedAtEpochMs = maxOf(clock.epochMs(), (previousStartMs / 1000 + 1) * 1000)
    }

    /** Scrobbles the current play if it has played long enough and hasn't been scrobbled yet. */
    fun checkThreshold(): TrackerEvent.ScrobbleReady? {
        val current = track ?: return null
        val startedAt = startedAtEpochMs ?: return null
        if (scrobbled) return null
        val threshold = ScrobbleRules.thresholdMs(current.durationMs, thresholdPercent()) ?: return null
        if (totalPlayedMs() < threshold) return null
        scrobbled = true
        return TrackerEvent.ScrobbleReady(Scrobble(current, startedAt / 1000))
    }

    /** How much longer the current track must play to qualify, or null if there's nothing to wait for. */
    fun msUntilScrobble(): Long? {
        val current = track ?: return null
        if (!isPlaying || scrobbled) return null
        val threshold = ScrobbleRules.thresholdMs(current.durationMs, thresholdPercent()) ?: return null
        return (threshold - totalPlayedMs()).coerceAtLeast(0)
    }

    /** The player went away: scrobble what qualifies and stop tracking. */
    fun finish(): TrackerEvent.ScrobbleReady? {
        val event = checkThreshold()
        if (isPlaying) playedMs += clock.elapsedMs() - playingSinceMs
        isPlaying = false
        track = null
        return event
    }

    fun totalPlayedMs(): Long =
        playedMs + if (isPlaying && track != null) clock.elapsedMs() - playingSinceMs else 0

    private companion object {
        /** Players' reported positions drift a little; anything further off is a skip or seek. */
        const val JUMP_TOLERANCE_MS = 2_000L
    }
}
