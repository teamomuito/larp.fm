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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.teamomuito.larpfm.R
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
    val firstArtistOnly by viewModel.firstArtistOnly.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val lastFmCheck by viewModel.lastFmCheck.collectAsStateWithLifecycle()
    val apiLog by viewModel.apiLog.collectAsStateWithLifecycle()
    val site = account.service.title

    Scaffold(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header(account, onSignOut = viewModel::signOut) }

            if (!hasAccess) {
                item { NotificationAccessCard() }
            }

            item {
                NowPlayingCard(
                    nowPlaying = nowPlaying,
                    enabled = enabled,
                    onEnabledChange = viewModel::setScrobblingEnabled,
                )
            }

            if (pendingCount > 0 || lastError != null) {
                item { QueueCard(site, pendingCount, lastError, onSendNow = viewModel::sendNow) }
            }

            item { SectionLabel("Settings") }
            item {
                SettingsCard(
                    thresholdPercent = thresholdPercent,
                    onThresholdChange = viewModel::setThresholdPercent,
                    sendAlbum = sendAlbum,
                    onSendAlbumChange = viewModel::setSendAlbum,
                    cleanAlbumTitles = cleanAlbumTitles,
                    onCleanAlbumTitlesChange = viewModel::setCleanAlbumTitles,
                    firstArtistOnly = firstArtistOnly,
                    onFirstArtistOnlyChange = viewModel::setFirstArtistOnly,
                )
            }

            item { SectionLabel("Apps") }
            item { AppsCard(apps, onEnabledChange = viewModel::setAppEnabled) }

            item { SectionLabel("Check $site") }
            item { CheckCard(site, account.username, lastFmCheck, apiLog, onCheck = viewModel::checkLastFm) }

            item { SectionLabel("Recent scrobbles") }
            if (recent.isEmpty()) {
                item { GlassCard(Modifier.fillMaxWidth()) { Text("Nothing scrobbled yet.", style = MaterialTheme.typography.bodyMedium) } }
            }
            items(recent, key = { "scrobble:" + it.id }) { entry -> ScrobbleRow(entry) }
        }
    }
}

@Composable
private fun Header(account: Account, onSignOut: () -> Unit) {
    Row(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).glass(CircleShape), contentAlignment = Alignment.Center) {
            Image(painter = painterResource(R.drawable.ic_cat), contentDescription = null, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("larp.fm", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
            Text(
                "${account.username} · ${account.service.title}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onSignOut, modifier = Modifier.glass(CircleShape)) { Text("Sign out") }
    }
}

@Composable
private fun NotificationAccessCard() {
    val context = LocalContext.current
    GlassCard(Modifier.fillMaxWidth(), tint = MaterialTheme.colorScheme.errorContainer) {
        Text("Allow notification access", style = MaterialTheme.typography.titleMedium)
        Text(
            "Android only lets apps with notification access see what other apps are playing. " +
                "larp.fm doesn't read or store your notifications.",
            style = MaterialTheme.typography.bodyMedium,
        )
        FilledTonalButton(onClick = { context.openNotificationAccessSettings() }, colors = glassButtonColors()) {
            Text("Open settings")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Text(
                "If Android says the setting is restricted: open App info, tap the ⋮ menu, " +
                    "choose \"Allow restricted settings\", then try again.",
                style = MaterialTheme.typography.bodySmall,
            )
            FilledTonalButton(onClick = { context.openAppInfo() }, colors = glassButtonColors()) {
                Text("App info")
            }
        }
    }
}

@Composable
private fun NowPlayingCard(nowPlaying: NowPlaying?, enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            Spacer(Modifier.width(8.dp))
            Text(
                if (enabled) "Scrobbling" else "Scrobbling paused",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        Column {
            Text("Now playing", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (nowPlaying == null) {
                Text("Nothing right now", style = MaterialTheme.typography.titleLarge)
            } else {
                val appName = remember(nowPlaying.packageName) { context.appLabel(nowPlaying.packageName) }
                Text(
                    nowPlaying.track.title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(nowPlaying.track.artist, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("in $appName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun QueueCard(site: String, pendingCount: Int, lastError: String?, onSendNow: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        if (pendingCount > 0) {
            Text(
                if (pendingCount == 1) "1 scrobble waiting to be sent" else "$pendingCount scrobbles waiting to be sent",
                style = MaterialTheme.typography.titleSmall,
            )
        }
        if (lastError != null) {
            Text("$site: $lastError", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (pendingCount > 0) {
            FilledTonalButton(onClick = onSendNow, colors = glassButtonColors()) { Text("Send now") }
        }
    }
}

@Composable
private fun CheckCard(
    site: String,
    username: String,
    check: LastFmCheck,
    apiLog: List<ApiLogEntry>,
    onCheck: () -> Unit,
) {
    val context = LocalContext.current
    GlassCard(Modifier.fillMaxWidth()) {
        Text(
            "See what $site has actually recorded for $username.",
            style = MaterialTheme.typography.bodyMedium,
        )
        FilledTonalButton(onClick = onCheck, enabled = check != LastFmCheck.Loading, colors = glassButtonColors()) {
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
                    Text("$it scrobbles on $site", style = MaterialTheme.typography.titleSmall)
                }
                if (check.recent.tracks.isEmpty()) {
                    Text("$site has no recent tracks", style = MaterialTheme.typography.bodySmall)
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
            Text(
                "Latest replies from $site",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (entry in apiLog) {
                val time = DateUtils.formatDateTime(context, entry.timeMs, DateUtils.FORMAT_SHOW_TIME)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(16.dp))
                        .padding(12.dp),
                ) {
                    Text("$time · ${entry.method} · HTTP ${entry.httpCode}", style = MaterialTheme.typography.labelSmall)
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
    firstArtistOnly: Boolean,
    onFirstArtistOnlyChange: (Boolean) -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column {
            Text(
                if (thresholdPercent == 0) "Scrobble threshold: as soon as it starts" else "Scrobble threshold: $thresholdPercent%",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                if (thresholdPercent == 0) {
                    "A track counts as soon as it starts playing, even if you skip it right away."
                } else {
                    "A track counts once $thresholdPercent% of it has played, or 4 minutes, whichever comes first."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = thresholdPercent.toFloat(),
                onValueChange = { onThresholdChange(it.roundToInt()) },
                valueRange = ScrobbleRules.MIN_PERCENT.toFloat()..ScrobbleRules.MAX_PERCENT.toFloat(),
            )
        }
        SettingSwitch(
            title = "First artist only",
            description = "\"Artist A, Artist B\" or \"Artist A feat. Artist B\" is scrobbled as \"Artist A\"",
            checked = firstArtistOnly,
            onCheckedChange = onFirstArtistOnlyChange,
        )
        SettingSwitch(
            title = "Scrobble album",
            description = if (sendAlbum) "Album info is sent with each scrobble" else "Only artist and track are sent",
            checked = sendAlbum,
            onCheckedChange = onSendAlbumChange,
        )
        SettingSwitch(
            title = "Clean album titles",
            description = "Removes anything in ( ) or [ ], e.g. \"Iron Maiden (Remaster) [Special]\" becomes \"Iron Maiden\"",
            checked = cleanAlbumTitles,
            onCheckedChange = onCleanAlbumTitlesChange,
            enabled = sendAlbum,
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun AppsCard(apps: List<AppSetting>, onEnabledChange: (String, Boolean) -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        if (apps.isEmpty()) {
            Text(
                "Play something in a music app and it will show up here, so you can choose which apps get scrobbled.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        for (app in apps) {
            AppRow(app, onEnabledChange = { onEnabledChange(app.packageName, it) })
        }
    }
}

@Composable
private fun AppRow(app: AppSetting, onEnabledChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val label = remember(app.packageName) { context.appLabel(app.packageName) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (label != app.packageName) {
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Switch(checked = app.enabled, onCheckedChange = onEnabledChange)
    }
}

@Composable
private fun ScrobbleRow(entry: ScrobbleEntry) {
    val track = entry.scrobble.track
    val time = remember(entry.scrobble.timestampSec) {
        DateUtils.getRelativeTimeSpanString(
            entry.scrobble.timestampSec * 1000,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    }
    val (status, color) = when (entry.status) {
        ScrobbleStatus.SENT -> "sent" to MaterialTheme.colorScheme.primary
        ScrobbleStatus.PENDING -> "waiting to send" to MaterialTheme.colorScheme.outline
        ScrobbleStatus.IGNORED, ScrobbleStatus.REJECTED -> (entry.message ?: "not scrobbled") to MaterialTheme.colorScheme.error
    }
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "$time · $status",
                style = MaterialTheme.typography.bodySmall,
                color = if (entry.status == ScrobbleStatus.IGNORED || entry.status == ScrobbleStatus.REJECTED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(Modifier.size(10.dp).background(color, CircleShape))
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
