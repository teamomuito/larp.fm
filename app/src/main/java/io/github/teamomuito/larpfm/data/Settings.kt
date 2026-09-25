package io.github.teamomuito.larpfm.data

import android.content.Context
import androidx.core.content.edit
import io.github.teamomuito.larpfm.BuildConfig
import io.github.teamomuito.larpfm.core.Larp
import io.github.teamomuito.larpfm.core.ScrobbleRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A signed-in Last.fm account. The session key only works with the API account it was created with. */
data class Account(
    val username: String,
    val sessionKey: String,
    val apiKey: String,
    val apiSecret: String,
)

data class AppSetting(val packageName: String, val enabled: Boolean)

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _account = MutableStateFlow(loadAccount())
    val account: StateFlow<Account?> = _account.asStateFlow()

    private val _scrobblingEnabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val scrobblingEnabled: StateFlow<Boolean> = _scrobblingEnabled.asStateFlow()

    private val _thresholdPercent = MutableStateFlow(prefs.getInt(KEY_THRESHOLD_PERCENT, ScrobbleRules.DEFAULT_PERCENT))

    /** How much of a track has to play before it's scrobbled. */
    val thresholdPercent: StateFlow<Int> = _thresholdPercent.asStateFlow()

    private val _sendAlbum = MutableStateFlow(prefs.getBoolean(KEY_SEND_ALBUM, true))

    /** When off, scrobbles carry only the artist and track, with no album. */
    val sendAlbum: StateFlow<Boolean> = _sendAlbum.asStateFlow()

    private val _cleanAlbumTitles = MutableStateFlow(prefs.getBoolean(KEY_CLEAN_ALBUM_TITLES, false))

    /** Strip anything in (parentheses) or [brackets] from album titles, e.g. "(2011 Remaster)". */
    val cleanAlbumTitles: StateFlow<Boolean> = _cleanAlbumTitles.asStateFlow()

    private val _autoLarp = MutableStateFlow(prefs.getInt(KEY_AUTO_LARP, 1))

    /** How many times each play is scrobbled automatically; 1 means just once. */
    val autoLarp: StateFlow<Int> = _autoLarp.asStateFlow()

    private val _apps = MutableStateFlow(loadApps())

    /** Every app that has played media since the app was installed, and whether it gets scrobbled. */
    val apps: StateFlow<List<AppSetting>> = _apps.asStateFlow()

    /** The API account used last, falling back to the one built into the APK. */
    val apiKey: String get() = prefs.getString(KEY_API_KEY, null) ?: BuildConfig.LASTFM_API_KEY
    val apiSecret: String get() = prefs.getString(KEY_API_SECRET, null) ?: BuildConfig.LASTFM_API_SECRET

    fun signIn(account: Account) {
        prefs.edit {
            putString(KEY_USERNAME, account.username)
            putString(KEY_SESSION_KEY, account.sessionKey)
            putString(KEY_API_KEY, account.apiKey)
            putString(KEY_API_SECRET, account.apiSecret)
        }
        _account.value = account
    }

    fun signOut() {
        prefs.edit {
            remove(KEY_USERNAME)
            remove(KEY_SESSION_KEY)
        }
        _account.value = null
    }

    fun setScrobblingEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, enabled) }
        _scrobblingEnabled.value = enabled
    }

    fun setThresholdPercent(percent: Int) {
        val value = percent.coerceIn(ScrobbleRules.MIN_PERCENT, ScrobbleRules.MAX_PERCENT)
        prefs.edit { putInt(KEY_THRESHOLD_PERCENT, value) }
        _thresholdPercent.value = value
    }

    fun setSendAlbum(send: Boolean) {
        prefs.edit { putBoolean(KEY_SEND_ALBUM, send) }
        _sendAlbum.value = send
    }

    fun setCleanAlbumTitles(clean: Boolean) {
        prefs.edit { putBoolean(KEY_CLEAN_ALBUM_TITLES, clean) }
        _cleanAlbumTitles.value = clean
    }

    fun setAutoLarp(times: Int) {
        val value = times.coerceIn(1, Larp.MAX_TIMES)
        prefs.edit { putInt(KEY_AUTO_LARP, value) }
        _autoLarp.value = value
    }

    fun isAppEnabled(packageName: String): Boolean =
        _apps.value.firstOrNull { it.packageName == packageName }?.enabled ?: (packageName !in DEFAULT_DISABLED)

    @Synchronized
    fun markAppSeen(packageName: String) {
        val seen = prefs.getStringSet(KEY_SEEN_APPS, null).orEmpty()
        if (packageName in seen) return
        prefs.edit {
            putStringSet(KEY_SEEN_APPS, seen + packageName)
            if (packageName in DEFAULT_DISABLED) {
                putStringSet(KEY_DISABLED_APPS, prefs.getStringSet(KEY_DISABLED_APPS, null).orEmpty() + packageName)
            }
        }
        _apps.value = loadApps()
    }

    @Synchronized
    fun setAppEnabled(packageName: String, enabled: Boolean) {
        val disabled = prefs.getStringSet(KEY_DISABLED_APPS, null).orEmpty()
        prefs.edit { putStringSet(KEY_DISABLED_APPS, if (enabled) disabled - packageName else disabled + packageName) }
        _apps.value = loadApps()
    }

    private fun loadAccount(): Account? {
        val sessionKey = prefs.getString(KEY_SESSION_KEY, null) ?: return null
        return Account(
            username = prefs.getString(KEY_USERNAME, null).orEmpty(),
            sessionKey = sessionKey,
            apiKey = apiKey,
            apiSecret = apiSecret,
        )
    }

    private fun loadApps(): List<AppSetting> {
        val disabled = prefs.getStringSet(KEY_DISABLED_APPS, null).orEmpty()
        return prefs.getStringSet(KEY_SEEN_APPS, null).orEmpty()
            .sorted()
            .map { AppSetting(it, it !in disabled) }
    }

    private companion object {
        const val KEY_USERNAME = "username"
        const val KEY_SESSION_KEY = "session_key"
        const val KEY_API_KEY = "api_key"
        const val KEY_API_SECRET = "api_secret"
        const val KEY_ENABLED = "scrobbling_enabled"
        const val KEY_THRESHOLD_PERCENT = "threshold_percent"
        const val KEY_SEND_ALBUM = "send_album"
        const val KEY_CLEAN_ALBUM_TITLES = "clean_album_titles"
        const val KEY_AUTO_LARP = "auto_larp"
        const val KEY_SEEN_APPS = "seen_apps"
        const val KEY_DISABLED_APPS = "disabled_apps"

        /** Apps that mostly play things that aren't music. They can still be switched on. */
        val DEFAULT_DISABLED = setOf(
            "com.google.android.youtube",
            "com.google.android.apps.youtube.kids",
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.netflix.mediaclient",
        )
    }
}
