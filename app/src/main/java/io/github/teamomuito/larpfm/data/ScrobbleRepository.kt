package io.github.teamomuito.larpfm.data

import io.github.teamomuito.larpfm.core.Larp
import io.github.teamomuito.larpfm.core.Scrobble
import io.github.teamomuito.larpfm.core.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class NowPlaying(val track: Track, val packageName: String)

/** Scrobble storage plus the live state the UI shows. The database calls block; use a background thread. */
class ScrobbleRepository(private val db: ScrobbleDb) {
    private val _recent = MutableStateFlow<List<ScrobbleEntry>>(emptyList())
    val recent: StateFlow<List<ScrobbleEntry>> = _recent.asStateFlow()

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)

    /** Why the last attempt to send scrobbles failed, if it did. */
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /** Queues [scrobble], counted [times] times in total (auto-LARP). */
    fun enqueue(scrobble: Scrobble, packageName: String, times: Int = 1) {
        val id = db.insert(scrobble, packageName)
        if (times > 1) db.addLarpCopies(id, times - 1)
        refresh()
    }

    /** Queues one more copy of a play. Returns false once it already counts [Larp.MAX_TIMES] times. */
    fun larp(id: Long): Boolean {
        val added = db.addLarpCopies(id, 1)
        refresh()
        return added > 0
    }

    fun pending(limit: Int): List<ScrobbleEntry> = db.pending(limit)

    fun setStatus(id: Long, status: ScrobbleStatus, message: String? = null) = db.setStatus(id, status, message)

    fun setLastError(message: String?) {
        _lastError.value = message
    }

    fun setNowPlaying(nowPlaying: NowPlaying) {
        _nowPlaying.value = nowPlaying
    }

    fun clearNowPlaying(packageName: String) {
        _nowPlaying.update { if (it?.packageName == packageName) null else it }
    }

    fun refresh() {
        db.prune(keep = HISTORY_SIZE)
        _recent.value = db.recent(RECENT_SIZE)
        _pendingCount.value = db.countPending()
    }

    private companion object {
        const val HISTORY_SIZE = 500
        const val RECENT_SIZE = 50
    }
}
