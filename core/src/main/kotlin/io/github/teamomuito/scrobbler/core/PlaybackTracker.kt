package io.github.teamomuito.scrobbler.core

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

/**
 * Follows one media player's metadata and play/pause state, and decides when the current track
 * should be sent as "now playing" and when it has played long enough to be scrobbled.
 *
 * Only time spent playing counts; pauses don't. Each track is scrobbled at most once.
 */
class PlaybackTracker(private val clock: Clock) {
    var track: Track? = null
        private set
    var isPlaying: Boolean = false
        private set

    private var playedMs = 0L
    private var playingSinceMs = 0L
    private var startedAtEpochMs: Long? = null
    private var scrobbled = false

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
        if (isPlaying) {
            playingSinceMs = clock.elapsedMs()
            if (newTrack != null) {
                startedAtEpochMs = clock.epochMs()
                events += TrackerEvent.NowPlaying(newTrack)
            }
        }
        return events
    }

    fun onPlaybackState(playing: Boolean): List<TrackerEvent> {
        if (playing == isPlaying) return emptyList()
        val now = clock.elapsedMs()
        if (playing) {
            isPlaying = true
            playingSinceMs = now
            val current = track ?: return emptyList()
            if (startedAtEpochMs == null) startedAtEpochMs = clock.epochMs()
            return listOf(TrackerEvent.NowPlaying(current))
        }
        playedMs += now - playingSinceMs
        isPlaying = false
        return listOfNotNull(checkThreshold())
    }

    /** Scrobbles the current track if it has played long enough and hasn't been scrobbled yet. */
    fun checkThreshold(): TrackerEvent.ScrobbleReady? {
        val current = track ?: return null
        val startedAt = startedAtEpochMs ?: return null
        if (scrobbled) return null
        val threshold = ScrobbleRules.thresholdMs(current.durationMs) ?: return null
        if (totalPlayedMs() < threshold) return null
        scrobbled = true
        return TrackerEvent.ScrobbleReady(Scrobble(current, startedAt / 1000))
    }

    /** How much longer the current track must play to qualify, or null if there's nothing to wait for. */
    fun msUntilScrobble(): Long? {
        val current = track ?: return null
        if (!isPlaying || scrobbled) return null
        val threshold = ScrobbleRules.thresholdMs(current.durationMs) ?: return null
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
}
