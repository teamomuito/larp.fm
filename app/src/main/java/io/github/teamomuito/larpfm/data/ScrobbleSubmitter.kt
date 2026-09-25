package io.github.teamomuito.larpfm.data

import android.util.Log
import io.github.teamomuito.larpfm.core.Track
import io.github.teamomuito.larpfm.lastfm.LastFmClient
import io.github.teamomuito.larpfm.lastfm.LastFmException
import io.github.teamomuito.larpfm.lastfm.UrlConnectionTransport
import java.io.IOException

/** Talks to Last.fm on behalf of the signed-in account. Every method blocks; call off the main thread. */
class ScrobbleSubmitter(
    private val settings: Settings,
    private val repository: ScrobbleRepository,
    userAgent: String,
) {
    enum class Outcome {
        /** Nothing left to send. */
        DONE,

        /** Try again later: offline, Last.fm is down, or the daily limit was hit. */
        RETRY,

        /** Can't send until the user signs in again. */
        STOP,
    }

    private val transport = UrlConnectionTransport(userAgent)

    fun client(apiKey: String, apiSecret: String) = LastFmClient(apiKey, apiSecret, transport)

    fun sendNowPlaying(track: Track) {
        val account = settings.account.value ?: return
        try {
            client(account.apiKey, account.apiSecret).updateNowPlaying(account.sessionKey, track)
        } catch (e: IOException) {
            Log.i(TAG, "Couldn't send now playing", e)
        } catch (e: LastFmException) {
            Log.i(TAG, "Couldn't send now playing", e)
        }
    }

    /** Sends every queued scrobble, in batches. */
    fun flush(): Outcome {
        val account = settings.account.value ?: return Outcome.STOP
        val client = client(account.apiKey, account.apiSecret)
        try {
            while (true) {
                val batch = repository.pending(LastFmClient.MAX_BATCH_SIZE)
                if (batch.isEmpty()) break
                val outcome = submit(client, account.sessionKey, batch)
                if (outcome != null) return outcome
            }
            repository.setLastError(null)
            return Outcome.DONE
        } finally {
            repository.refresh()
        }
    }

    /** Returns null to keep going, or the outcome that should end this flush. */
    private fun submit(client: LastFmClient, sessionKey: String, batch: List<ScrobbleEntry>): Outcome? {
        val results = try {
            client.scrobble(sessionKey, batch.map { it.scrobble })
        } catch (e: IOException) {
            repository.setLastError("Network error: ${e.message}")
            return Outcome.RETRY
        } catch (e: LastFmException) {
            repository.setLastError(e.message)
            return when {
                e.isRetryable -> Outcome.RETRY
                e.isAuthError -> Outcome.STOP
                // One bad scrobble fails the whole batch; send them one at a time to find it.
                batch.size > 1 -> batch.firstNotNullOfOrNull { submit(client, sessionKey, listOf(it)) }
                else -> {
                    repository.setStatus(batch.single().id, ScrobbleStatus.REJECTED, e.message)
                    null
                }
            }
        }

        var hitDailyLimit = false
        for ((entry, result) in batch.zip(results)) {
            when {
                result.accepted -> repository.setStatus(entry.id, ScrobbleStatus.SENT)
                result.isDailyLimit -> hitDailyLimit = true
                else -> repository.setStatus(entry.id, ScrobbleStatus.IGNORED, result.reason)
            }
        }
        if (hitDailyLimit) {
            repository.setLastError("Daily scrobble limit reached, will retry later")
            return Outcome.RETRY
        }
        return null
    }

    private companion object {
        const val TAG = "ScrobbleSubmitter"
    }
}
