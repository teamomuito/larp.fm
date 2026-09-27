package io.github.teamomuito.larpfm.data

import io.github.teamomuito.larpfm.core.Larp
import io.github.teamomuito.larpfm.core.Scrobble
import io.github.teamomuito.larpfm.core.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class NowPlaying(val track: Track, val packageName: String)

/** One raw reply from Last.fm, kept so problems can be diagnosed from the app. */
data class ApiLogEntry(val timeMs: Long, val method: String, val httpCode: Int, val body: String)

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

    private val _apiLog = MutableStateFlow<List<ApiLogEntry>>(emptyList())

    /** The latest replies from Last.fm, newest first. */
    val apiLog: StateFlow<List<ApiLogEntry>> = _apiLog.asStateFlow()

    fun logResponse(method: String, httpCode: Int, body: String) {
        val entry = ApiLogEntry(System.currentTimeMillis(), method, httpCode, body.take(LOG_BODY_LENGTH))
        _apiLog.update { (listOf(entry) + it).take(LOG_SIZE) }
    }

    /** Queues [scrobble], counted [times] times in total: now, then once an hour after (auto-LARP). */
    fun enqueue(scrobble: Scrobble, packageName: String, times: Int = 1) {
        db.insert(scrobble, packageName, Larp.copies(scrobble, times))
        refresh()
    }

    /** What can be sent now. On a site with a [dailyLimit], copies wait while [Larp.DAILY_BUDGET] is used up. */
    fun pending(limit: Int, dailyLimit: Boolean): List<ScrobbleEntry> {
        val now = nowSec()
        val copiesAllowed = !dailyLimit || db.countSentSince(now - DAY_SEC) < Larp.DAILY_BUDGET
        return db.pending(limit, now, copiesAllowed)
    }

    /**
     * How long until waiting auto-LARP copies should be looked at again, or null if there are
     * none. Copies that are already due but held back (daily budget) are retried in an hour.
     */
    fun msUntilNextCopy(): Long? {
        val next = db.nextCopySec() ?: return null
        val wait = next - nowSec()
        return if (wait > 0) wait * 1000 else Larp.INTERVAL_SEC * 1000
    }

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
        db.cancelOrphanedCopies()
        db.prune(keep = HISTORY_SIZE)
        _recent.value = db.recent(RECENT_SIZE)
        _pendingCount.value = db.countPending(nowSec())
    }

    private fun nowSec() = System.currentTimeMillis() / 1000

    private companion object {
        const val HISTORY_SIZE = 500
        const val RECENT_SIZE = 50
        const val LOG_SIZE = 5
        const val LOG_BODY_LENGTH = 600
        const val DAY_SEC = 24 * 60 * 60L
    }
}
