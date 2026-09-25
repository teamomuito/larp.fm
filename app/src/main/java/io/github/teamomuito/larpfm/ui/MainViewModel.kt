package io.github.teamomuito.larpfm.ui

import android.app.Application
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.teamomuito.larpfm.data.Account
import io.github.teamomuito.larpfm.data.ScrobbleEntry
import io.github.teamomuito.larpfm.graph
import io.github.teamomuito.larpfm.lastfm.LastFmException
import io.github.teamomuito.larpfm.work.FlushWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface LoginState {
    data object Idle : LoginState
    data object Loading : LoginState
    data class Error(val message: String) : LoginState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = application.graph
    private val settings = graph.settings
    private val repository = graph.repository

    val account = settings.account
    val scrobblingEnabled = settings.scrobblingEnabled
    val thresholdPercent = settings.thresholdPercent
    val sendAlbum = settings.sendAlbum
    val autoLarp = settings.autoLarp
    val apps = settings.apps
    val nowPlaying = repository.nowPlaying
    val recent = repository.recent
    val pendingCount = repository.pendingCount
    val lastError = repository.lastError

    val savedApiKey: String get() = settings.apiKey
    val savedApiSecret: String get() = settings.apiSecret

    private val _hasNotificationAccess = MutableStateFlow(false)
    val hasNotificationAccess = _hasNotificationAccess.asStateFlow()

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState = _loginState.asStateFlow()

    init {
        refreshNotificationAccess()
    }

    fun refreshNotificationAccess() {
        val context = getApplication<Application>()
        _hasNotificationAccess.value =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
    }

    fun signIn(username: String, password: String, apiKey: String, apiSecret: String) {
        if (_loginState.value == LoginState.Loading) return
        _loginState.value = LoginState.Loading
        viewModelScope.launch {
            _loginState.value = try {
                val session = withContext(Dispatchers.IO) {
                    graph.submitter.client(apiKey, apiSecret).getMobileSession(username, password)
                }
                settings.signIn(Account(session.username, session.key, apiKey, apiSecret))
                FlushWorker.enqueue(getApplication<Application>())
                LoginState.Idle
            } catch (e: LastFmException) {
                LoginState.Error(
                    when (e.code) {
                        LastFmException.AUTHENTICATION_FAILED -> "Wrong username or password."
                        LastFmException.INVALID_API_KEY -> "That API key isn't valid."
                        LastFmException.INVALID_SIGNATURE -> "That shared secret doesn't match the API key."
                        else -> e.message ?: "Last.fm error ${e.code}"
                    },
                )
            } catch (e: IOException) {
                LoginState.Error("Couldn't reach Last.fm. Check your connection.")
            }
        }
    }

    fun signOut() {
        settings.signOut()
        repository.setLastError(null)
    }

    fun setScrobblingEnabled(enabled: Boolean) = settings.setScrobblingEnabled(enabled)

    fun setThresholdPercent(percent: Int) = settings.setThresholdPercent(percent)

    fun setSendAlbum(send: Boolean) = settings.setSendAlbum(send)

    fun setAutoLarp(times: Int) = settings.setAutoLarp(times)

    fun setAppEnabled(packageName: String, enabled: Boolean) = settings.setAppEnabled(packageName, enabled)

    fun sendNow() = FlushWorker.enqueue(getApplication<Application>())

    /** Scrobbles a play one more time. */
    fun larp(entry: ScrobbleEntry) {
        val context = getApplication<Application>()
        graph.scope.launch {
            if (repository.larp(entry.id)) FlushWorker.enqueue(context)
        }
    }
}
