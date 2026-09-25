package io.github.teamomuito.scrobbler.service

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.util.Log
import io.github.teamomuito.scrobbler.core.Clock
import io.github.teamomuito.scrobbler.core.PlaybackTracker
import io.github.teamomuito.scrobbler.core.Scrobble
import io.github.teamomuito.scrobbler.core.Track
import io.github.teamomuito.scrobbler.core.TrackerEvent
import io.github.teamomuito.scrobbler.data.NowPlaying
import io.github.teamomuito.scrobbler.graph
import io.github.teamomuito.scrobbler.work.FlushWorker
import kotlinx.coroutines.launch

/**
 * Watches every app's media session. Android only hands those out to apps with notification
 * access, which is why this is a notification listener; the notifications themselves are ignored.
 */
class ScrobbleListenerService : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    private val players = mutableMapOf<MediaSession.Token, Player>()
    private var sessionManager: MediaSessionManager? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updatePlayers(controllers.orEmpty())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val manager = getSystemService(MediaSessionManager::class.java)
        val component = ComponentName(this, ScrobbleListenerService::class.java)
        try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
            sessionManager = manager
            updatePlayers(manager.getActiveSessions(component))
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification access was revoked", e)
        }
    }

    override fun onListenerDisconnected() {
        releaseAll()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        releaseAll()
        super.onDestroy()
    }

    private fun releaseAll() {
        sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        sessionManager = null
        players.values.forEach { it.release() }
        players.clear()
    }

    private fun updatePlayers(controllers: List<MediaController>) {
        val active = controllers.associateBy { it.sessionToken }
        for (token in players.keys - active.keys) {
            players.remove(token)?.release()
        }
        for ((token, controller) in active) {
            if (token !in players) {
                graph.settings.markAppSeen(controller.packageName)
                players[token] = Player(controller)
            }
        }
    }

    /** One media session, e.g. a music app, and the progress of whatever it's playing. */
    private inner class Player(private val controller: MediaController) : MediaController.Callback() {
        private val appPackage: String = controller.packageName
        private val tracker = PlaybackTracker(SystemClocks)
        private val checkThreshold = Runnable { handle(listOfNotNull(tracker.checkThreshold())) }

        init {
            controller.registerCallback(this, handler)
            handle(tracker.onMetadata(controller.metadata?.toTrack()))
            handle(tracker.onPlaybackState(controller.playbackState.isPlaying()))
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            handle(tracker.onMetadata(metadata?.toTrack()))
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            handle(tracker.onPlaybackState(state.isPlaying()))
        }

        override fun onSessionDestroyed() {
            players.remove(controller.sessionToken)
            release()
        }

        fun release() {
            controller.unregisterCallback(this)
            handle(listOfNotNull(tracker.finish()))
        }

        private fun handle(events: List<TrackerEvent>) {
            for (event in events) {
                when (event) {
                    is TrackerEvent.NowPlaying -> nowPlaying(event.track)
                    is TrackerEvent.ScrobbleReady -> scrobble(event.scrobble)
                }
            }
            if (!tracker.isPlaying || tracker.track == null) graph.repository.clearNowPlaying(appPackage)

            // Check back when the track should have played long enough to count.
            handler.removeCallbacks(checkThreshold)
            tracker.msUntilScrobble()?.let { handler.postDelayed(checkThreshold, it + 1_000) }
        }

        private fun nowPlaying(track: Track) {
            if (!shouldScrobble()) return
            graph.repository.setNowPlaying(NowPlaying(track, appPackage))
            graph.scope.launch { graph.submitter.sendNowPlaying(track) }
        }

        private fun scrobble(scrobble: Scrobble) {
            if (!shouldScrobble()) return
            graph.scope.launch {
                graph.repository.enqueue(scrobble, appPackage)
                FlushWorker.enqueue(applicationContext)
            }
        }

        private fun shouldScrobble(): Boolean {
            val settings = graph.settings
            return settings.account.value != null &&
                settings.scrobblingEnabled.value &&
                settings.isAppEnabled(appPackage)
        }
    }

    private object SystemClocks : Clock {
        override fun elapsedMs() = SystemClock.elapsedRealtime()
        override fun epochMs() = System.currentTimeMillis()
    }

    private companion object {
        const val TAG = "ScrobbleListener"
    }
}

private fun PlaybackState?.isPlaying() = this?.state == PlaybackState.STATE_PLAYING

private fun MediaMetadata.toTrack(): Track? {
    fun text(key: String) = getString(key)?.trim()?.takeIf { it.isNotEmpty() }

    val title = text(MediaMetadata.METADATA_KEY_TITLE) ?: text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: return null
    val albumArtist = text(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
    val artist = text(MediaMetadata.METADATA_KEY_ARTIST) ?: albumArtist ?: return null
    return Track(
        artist = artist,
        title = title,
        album = text(MediaMetadata.METADATA_KEY_ALBUM),
        albumArtist = albumArtist,
        durationMs = getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0),
    )
}
