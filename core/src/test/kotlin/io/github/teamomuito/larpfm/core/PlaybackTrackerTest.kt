package io.github.teamomuito.larpfm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTrackerTest {
    private class FakeClock : Clock {
        var elapsed = 0L
        var epoch = 1_700_000_000_000L

        fun advance(ms: Long) {
            elapsed += ms
            epoch += ms
        }

        override fun elapsedMs() = elapsed
        override fun epochMs() = epoch
    }

    private val clock = FakeClock()
    private val tracker = PlaybackTracker(clock)
    private val song = Track("Artist", "Song", durationMs = 200_000)

    @Test
    fun `uses the configured threshold, read on every check`() {
        var percent = 10
        val custom = PlaybackTracker(clock, thresholdPercent = { percent })
        custom.onMetadata(song)
        custom.onPlaybackState(true)
        assertEquals(20_000L, custom.msUntilScrobble())

        percent = 1
        clock.advance(2_000)

        assertTrue(custom.checkThreshold() is TrackerEvent.ScrobbleReady)
    }

    @Test
    fun `sends now playing when playback starts`() {
        assertEquals(emptyList<TrackerEvent>(), tracker.onMetadata(song))
        assertEquals(listOf(TrackerEvent.NowPlaying(song)), tracker.onPlaybackState(true))
    }

    @Test
    fun `scrobbles after half the track with the start time`() {
        tracker.onMetadata(song)
        tracker.onPlaybackState(true)
        clock.advance(99_000)
        assertNull(tracker.checkThreshold())
        assertEquals(1_000L, tracker.msUntilScrobble())

        clock.advance(1_000)
        val event = tracker.checkThreshold()

        assertEquals(TrackerEvent.ScrobbleReady(Scrobble(song, 1_700_000_000)), event)
        assertNull("only scrobbles once", tracker.checkThreshold())
        assertNull(tracker.msUntilScrobble())
    }

    @Test
    fun `long tracks need four minutes`() {
        tracker.onMetadata(song.copy(durationMs = 3_600_000))
        tracker.onPlaybackState(true)

        assertEquals(ScrobbleRules.MAX_THRESHOLD_MS, tracker.msUntilScrobble())
    }

    @Test
    fun `short tracks are never scrobbled`() {
        tracker.onMetadata(song.copy(durationMs = 30_000))
        tracker.onPlaybackState(true)
        clock.advance(30_000)

        assertNull(tracker.msUntilScrobble())
        val next = Track("Other", "Song")
        assertEquals(listOf(TrackerEvent.NowPlaying(next)), tracker.onMetadata(next))
    }

    @Test
    fun `paused time does not count`() {
        tracker.onMetadata(song)
        tracker.onPlaybackState(true)
        clock.advance(60_000)
        tracker.onPlaybackState(false)
        clock.advance(600_000)

        assertEquals(60_000L, tracker.totalPlayedMs())
        assertNull(tracker.msUntilScrobble())

        tracker.onPlaybackState(true)
        clock.advance(40_000)
        val event = tracker.onPlaybackState(false).single()

        assertEquals(TrackerEvent.ScrobbleReady(Scrobble(song, 1_700_000_000)), event)
    }

    @Test
    fun `skipping early does not scrobble`() {
        val next = Track("Artist", "Next", durationMs = 180_000)
        tracker.onMetadata(song)
        tracker.onPlaybackState(true)
        clock.advance(20_000)

        assertEquals(listOf(TrackerEvent.NowPlaying(next)), tracker.onMetadata(next))
        assertEquals(0L, tracker.totalPlayedMs())
    }

    @Test
    fun `changing track scrobbles the previous one if it qualified`() {
        val next = Track("Artist", "Next", durationMs = 180_000)
        tracker.onMetadata(song)
        tracker.onPlaybackState(true)
        clock.advance(150_000)

        val events = tracker.onMetadata(next)

        assertEquals(
            listOf(
                TrackerEvent.ScrobbleReady(Scrobble(song, 1_700_000_000)),
                TrackerEvent.NowPlaying(next),
            ),
            events,
        )
    }

    @Test
    fun `repeated metadata for the same song keeps progress and picks up the duration`() {
        tracker.onMetadata(song.copy(durationMs = 0))
        tracker.onPlaybackState(true)
        clock.advance(120_000)
        assertEquals(120_000L, tracker.msUntilScrobble())

        val events = tracker.onMetadata(song.copy(album = "Album"))

        assertEquals(1, events.size)
        assertTrue(events.single() is TrackerEvent.ScrobbleReady)
        assertEquals("Album", tracker.track?.album)
    }

    @Test
    fun `metadata arriving after playback starts uses that moment as the start`() {
        tracker.onPlaybackState(true)
        clock.advance(5_000)

        assertEquals(listOf(TrackerEvent.NowPlaying(song)), tracker.onMetadata(song))
        assertEquals(0L, tracker.totalPlayedMs())
        assertEquals(100_000L, tracker.msUntilScrobble())
    }

    private var rescrobble = true
    private val restarting = PlaybackTracker(clock, rescrobble = { rescrobble })

    /** Where the player is right now, if it has played [ms] of the song. */
    private fun at(ms: Long, speed: Float = 1f) = Position(ms, clock.elapsed, speed)

    /** Plays [song] from the start until its first scrobble. */
    private fun playUntilScrobbled() {
        restarting.onMetadata(song)
        restarting.onPlaybackState(true, at(0))
        clock.advance(100_000)
        assertEquals(TrackerEvent.ScrobbleReady(Scrobble(song, 1_700_000_000)), restarting.checkThreshold())
    }

    @Test
    fun `pausing and resuming a scrobbled song starts a new play`() {
        playUntilScrobbled()
        restarting.onPlaybackState(false, at(100_000))
        clock.advance(5_000)

        assertEquals(listOf(TrackerEvent.NowPlaying(song)), restarting.onPlaybackState(true, at(100_000)))
        assertEquals(100_000L, restarting.msUntilScrobble())

        clock.advance(100_000)
        assertEquals(TrackerEvent.ScrobbleReady(Scrobble(song, 1_700_000_105)), restarting.checkThreshold())
    }

    @Test
    fun `pausing before the threshold keeps counting the same play`() {
        restarting.onMetadata(song)
        restarting.onPlaybackState(true, at(0))
        clock.advance(60_000)
        restarting.onPlaybackState(false, at(60_000))
        clock.advance(5_000)
        restarting.onPlaybackState(true, at(60_000))

        assertEquals(40_000L, restarting.msUntilScrobble())
        clock.advance(40_000)
        assertEquals(TrackerEvent.ScrobbleReady(Scrobble(song, 1_700_000_000)), restarting.checkThreshold())
    }

    @Test
    fun `skipping back to the start of a scrobbled song starts a new play`() {
        playUntilScrobbled()
        clock.advance(10_000)

        assertEquals(emptyList<TrackerEvent>(), restarting.onPlaybackState(true, at(0)))
        assertEquals(100_000L, restarting.msUntilScrobble())

        clock.advance(100_000)
        assertEquals(TrackerEvent.ScrobbleReady(Scrobble(song, 1_700_000_110)), restarting.checkThreshold())
    }

    @Test
    fun `seeking forward counts too`() {
        playUntilScrobbled()

        restarting.onPlaybackState(true, at(150_000))

        assertEquals(100_000L, restarting.msUntilScrobble())
    }

    @Test
    fun `a jump scrobbles a play that qualified but wasn't checked yet`() {
        restarting.onMetadata(song)
        restarting.onPlaybackState(true, at(0))
        clock.advance(100_500)

        val events = restarting.onPlaybackState(true, at(0))

        assertEquals(listOf(TrackerEvent.ScrobbleReady(Scrobble(song, 1_700_000_000))), events)
        assertEquals(100_000L, restarting.msUntilScrobble())
    }

    @Test
    fun `positions that keep up with playback are not jumps`() {
        playUntilScrobbled()
        clock.advance(10_000)
        restarting.onPlaybackState(true, at(111_000, speed = 2f))
        clock.advance(10_000)
        restarting.onPlaybackState(true, at(131_000, speed = 2f))

        assertNull(restarting.msUntilScrobble())
    }

    @Test
    fun `positions from the previous song are forgotten`() {
        val next = Track("Artist", "Next", durationMs = 180_000)
        playUntilScrobbled()
        restarting.onMetadata(next)
        clock.advance(95_000)
        assertTrue(restarting.checkThreshold() is TrackerEvent.ScrobbleReady)

        restarting.onPlaybackState(true, at(95_000))

        assertNull(restarting.msUntilScrobble())
    }

    @Test
    fun `a new play never shares a timestamp with the last one`() {
        val short = song.copy(durationMs = 31_000)
        val fast = PlaybackTracker(clock, thresholdPercent = { 1 }, rescrobble = { true })
        fast.onMetadata(short)
        fast.onPlaybackState(true)
        clock.advance(400)
        assertEquals(TrackerEvent.ScrobbleReady(Scrobble(short, 1_700_000_000)), fast.checkThreshold())
        fast.onPlaybackState(false)
        clock.advance(100)
        fast.onPlaybackState(true)
        clock.advance(400)

        assertEquals(TrackerEvent.ScrobbleReady(Scrobble(short, 1_700_000_001)), fast.checkThreshold())
    }

    @Test
    fun `with rescrobbling off each play counts once`() {
        rescrobble = false
        playUntilScrobbled()
        restarting.onPlaybackState(false, at(100_000))
        restarting.onPlaybackState(true, at(100_000))
        clock.advance(10_000)
        restarting.onPlaybackState(true, at(0))
        clock.advance(200_000)

        assertNull(restarting.msUntilScrobble())
        assertNull(restarting.checkThreshold())
    }

    @Test
    fun `finish scrobbles a qualifying track and stops tracking`() {
        tracker.onMetadata(song)
        tracker.onPlaybackState(true)
        clock.advance(100_000)

        assertTrue(tracker.finish() is TrackerEvent.ScrobbleReady)
        assertNull(tracker.track)
        assertNull(tracker.msUntilScrobble())
    }
}
