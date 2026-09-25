package io.github.teamomuito.larpfm.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.teamomuito.larpfm.R
import io.github.teamomuito.larpfm.core.Larp
import io.github.teamomuito.larpfm.core.ScrobbleRules
import io.github.teamomuito.larpfm.data.Account
import io.github.teamomuito.larpfm.data.ApiLogEntry
import io.github.teamomuito.larpfm.data.AppSetting
import io.github.teamomuito.larpfm.data.NowPlaying
import io.github.teamomuito.larpfm.data.ScrobbleEntry
import io.github.teamomuito.larpfm.data.ScrobbleStatus
import io.github.teamomuito.larpfm.service.ScrobbleListenerService
import kotlin.math.roundToInt

@Composable
fun HomeScreen(viewModel: MainViewModel, account: Account) {
    val hasAccess by viewModel.hasNotificationAccess.collectAsStateWithLifecycle()
    val enabled by viewModel.scrobblingEnabled.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val lastError by viewModel.lastError.collectAsStateWithLifecycle()
    val thresholdPercent by viewModel.thresholdPercent.collectAsStateWithLifecycle()
    val sendAlbum by viewModel.sendAlbum.collectAsStateWithLifecycle()
    val cleanAlbumTitles by viewModel.cleanAlbumTitles.collectAsStateWithLifecycle()
    val autoLarp by viewModel.autoLarp.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val lastFmCheck by viewModel.lastFmCheck.collectAsStateWithLifecycle()
    val apiLog by viewModel.apiLog.collectAsStateWithLifecycle()

    Scaffold { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 16.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.ic_cat),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("larp.fm", style = MaterialTheme.typography.headlineMedium)
                        Text("Signed in as ${account.username}", style = MaterialTheme.typography.bodyMedium)
                    }
                    TextButton(onClick = viewModel::signOut) { Text("Sign out") }
                }
            }

            if (!hasAccess) {
                item { NotificationAccessCard() }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Scrobbling", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (enabled) "On" else "Paused",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(checked = enabled, onCheckedChange = viewModel::setScrobblingEnabled)
                    }
                }
            }

            item { NowPlayingCard(nowPlaying) }

            if (pendingCount > 0 || lastError != null) {
                item { QueueCard(pendingCount, lastError, onSendNow = viewModel::sendNow) }
            }

            item { LastFmCheckCard(account.username, lastFmCheck, apiLog, onCheck = viewModel::checkLastFm) }

            item {
                SettingsCard(
                    thresholdPercent = thresholdPercent,
                    onThresholdChange = viewModel::setThresholdPercent,
                    sendAlbum = sendAlbum,
                    onSendAlbumChange = viewModel::setSendAlbum,
                    cleanAlbumTitles = cleanAlbumTitles,
                    onCleanAlbumTitlesChange = viewModel::setCleanAlbumTitles,
                    autoLarp = autoLarp,
                    onAutoLarpChange = viewModel::setAutoLarp,
                )
            }

            item { SectionTitle("Apps") }
            if (apps.isEmpty()) {
                item {
                    Text(
                        "Play something in a music app and it will show up here, so you can choose which apps get scrobbled.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(apps, key = { "app:" + it.packageName }) { app ->
                AppRow(app, onEnabledChange = { viewModel.setAppEnabled(app.packageName, it) })
            }

            item { SectionTitle("Recent scrobbles") }
            if (recent.isEmpty()) {
                item { Text("Nothing scrobbled yet.", style = MaterialTheme.typography.bodyMedium) }
            }
            items(recent, key = { "scrobble:" + it.id }) { entry ->
                ScrobbleRow(entry, onLarp = { viewModel.larp(entry) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun NotificationAccessCard() {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Allow notification access",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                "Android only lets apps with notification access see what other apps are playing. " +
                    "larp.fm doesn't read or store your notifications.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            FilledTonalButton(onClick = { context.openNotificationAccessSettings() }) {
                Text("Open settings")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Text(
                    "If Android says the setting is restricted: open App info, tap the ⋮ menu, " +
                        "choose \"Allow restricted settings\", then try again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                OutlinedButton(onClick = { context.openAppInfo() }) {
                    Text("App info")
                }
            }
        }
    }
}

@Composable
private fun NowPlayingCard(nowPlaying: NowPlaying?) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Now playing", style = MaterialTheme.typography.labelMedium)
            if (nowPlaying == null) {
                Text("Nothing right now", style = MaterialTheme.typography.bodyLarge)
            } else {
                val appName = remember(nowPlaying.packageName) { context.appLabel(nowPlaying.packageName) }
                Text(nowPlaying.track.title, style = MaterialTheme.typography.titleMedium)
                Text(nowPlaying.track.artist, style = MaterialTheme.typography.bodyMedium)
                Text("in $appName", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun QueueCard(pendingCount: Int, lastError: String?, onSendNow: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (pendingCount > 0) {
                Text(
                    if (pendingCount == 1) "1 scrobble waiting to be sent" else "$pendingCount scrobbles waiting to be sent",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            if (lastError != null) {
                Text("Last.fm: $lastError", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (pendingCount > 0) {
                FilledTonalButton(onClick = onSendNow) { Text("Send now") }
            }
        }
    }
}

@Composable
private fun LastFmCheckCard(username: String, check: LastFmCheck, apiLog: List<ApiLogEntry>, onCheck: () -> Unit) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Check Last.fm", style = MaterialTheme.typography.titleSmall)
            Text(
                "See what Last.fm has actually recorded for $username.",
                style = MaterialTheme.typography.bodySmall,
            )
            FilledTonalButton(onClick = onCheck, enabled = check != LastFmCheck.Loading) {
                Text(if (check == LastFmCheck.Loading) "Checking…" else "Check now")
            }
            when (check) {
                LastFmCheck.Idle, LastFmCheck.Loading -> Unit
                is LastFmCheck.Error -> Text(
                    check.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                is LastFmCheck.Done -> {
                    check.recent.total?.let {
                        Text("$it scrobbles on Last.fm", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (check.recent.tracks.isEmpty()) {
                        Text("Last.fm has no recent tracks", style = MaterialTheme.typography.bodySmall)
                    }
                    for (track in check.recent.tracks) {
                        val time = if (track.nowPlaying) {
                            "now playing"
                        } else {
                            track.timestampSec?.let {
                                DateUtils.getRelativeTimeSpanString(
                                    it * 1000,
                                    System.currentTimeMillis(),
                                    DateUtils.MINUTE_IN_MILLIS,
                                ).toString()
                            }.orEmpty()
                        }
                        Text(
                            "${track.title} · ${track.artist} · $time",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (apiLog.isNotEmpty()) {
                HorizontalDivider()
                Text("Latest replies from Last.fm", style = MaterialTheme.typography.labelMedium)
                for (entry in apiLog) {
                    val time = DateUtils.formatDateTime(context, entry.timeMs, DateUtils.FORMAT_SHOW_TIME)
                    Text(
                        "$time · ${entry.method} · HTTP ${entry.httpCode}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        entry.body,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    thresholdPercent: Int,
    onThresholdChange: (Int) -> Unit,
    sendAlbum: Boolean,
    onSendAlbumChange: (Boolean) -> Unit,
    cleanAlbumTitles: Boolean,
    onCleanAlbumTitlesChange: (Boolean) -> Unit,
    autoLarp: Int,
    onAutoLarpChange: (Int) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Scrobble threshold: $thresholdPercent%", style = MaterialTheme.typography.titleSmall)
            Text(
                "A track counts once $thresholdPercent% of it has played, or 4 minutes, whichever comes first.",
                style = MaterialTheme.typography.bodySmall,
            )
            Slider(
                value = thresholdPercent.toFloat(),
                onValueChange = { onThresholdChange(it.roundToInt()) },
                valueRange = ScrobbleRules.MIN_PERCENT.toFloat()..ScrobbleRules.MAX_PERCENT.toFloat(),
            )

            HorizontalDivider()
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Scrobble album", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (sendAlbum) "Album info is sent with each scrobble" else "Only artist and track are sent",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = sendAlbum, onCheckedChange = onSendAlbumChange)
            }
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Clean album titles", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Removes anything in ( ) or [ ], e.g. \"Iron Maiden (Remaster) [Special]\" becomes \"Iron Maiden\"",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = cleanAlbumTitles,
                    onCheckedChange = onCleanAlbumTitlesChange,
                    enabled = sendAlbum,
                )
            }

            HorizontalDivider()
            Text(
                if (autoLarp == 1) "Auto-LARP: off" else "Auto-LARP: every play counts $autoLarp×",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "Scrobbles each play up to ${Larp.MAX_TIMES}× automatically. Copies are backdated one track " +
                    "length apart so Last.fm doesn't drop them as duplicates.",
                style = MaterialTheme.typography.bodySmall,
            )
            Slider(
                value = autoLarp.toFloat(),
                onValueChange = { onAutoLarpChange(it.roundToInt()) },
                valueRange = 1f..Larp.MAX_TIMES.toFloat(),
                steps = Larp.MAX_TIMES - 2,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun AppRow(app: AppSetting, onEnabledChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val label = remember(app.packageName) { context.appLabel(app.packageName) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (label != app.packageName) {
                Text(app.packageName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Switch(checked = app.enabled, onCheckedChange = onEnabledChange)
    }
}

@Composable
private fun ScrobbleRow(entry: ScrobbleEntry, onLarp: () -> Unit) {
    val track = entry.scrobble.track
    val time = remember(entry.scrobble.timestampSec) {
        DateUtils.getRelativeTimeSpanString(
            entry.scrobble.timestampSec * 1000,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    }
    val status = when (entry.status) {
        ScrobbleStatus.SENT -> "$time · sent"
        ScrobbleStatus.PENDING -> "$time · waiting to send"
        ScrobbleStatus.IGNORED, ScrobbleStatus.REJECTED -> "$time · ${entry.message ?: "not scrobbled"}"
    }
    // Only plays Last.fm took (or will take) can be copied, and it ignores anything over 14 days old.
    val canLarp = entry.timesScrobbled < Larp.MAX_TIMES &&
        (entry.status == ScrobbleStatus.SENT || entry.status == ScrobbleStatus.PENDING) &&
        System.currentTimeMillis() / 1000 - entry.scrobble.timestampSec < Larp.MAX_AGE_SEC
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = when (entry.status) {
                    ScrobbleStatus.SENT, ScrobbleStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                    ScrobbleStatus.IGNORED, ScrobbleStatus.REJECTED -> MaterialTheme.colorScheme.error
                },
            )
        }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = onLarp,
            enabled = canLarp,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
        ) {
            Text(if (entry.timesScrobbled > 1) "LARP ×${entry.timesScrobbled}" else "LARP")
        }
    }
}

private fun Context.openNotificationAccessSettings() {
    // Android 11+ can jump straight to this app's switch; some devices don't support it.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val component = ComponentName(this, ScrobbleListenerService::class.java)
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        try {
            startActivity(intent)
            return
        } catch (e: ActivityNotFoundException) {
            // Fall through to the list of all listeners.
        }
    }
    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
}

private fun Context.openAppInfo() {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
}

private fun Context.appLabel(packageName: String): String = try {
    @Suppress("DEPRECATION")
    val info = packageManager.getApplicationInfo(packageName, 0)
    packageManager.getApplicationLabel(info).toString()
} catch (e: PackageManager.NameNotFoundException) {
    packageName
}
