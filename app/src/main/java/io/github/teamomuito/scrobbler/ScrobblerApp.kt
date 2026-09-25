package io.github.teamomuito.scrobbler

import android.app.Application
import android.content.Context
import io.github.teamomuito.scrobbler.data.ScrobbleDb
import io.github.teamomuito.scrobbler.data.ScrobbleRepository
import io.github.teamomuito.scrobbler.data.ScrobbleSubmitter
import io.github.teamomuito.scrobbler.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ScrobblerApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

/** The app's shared objects. The UI and the listener service run in the same process and share these. */
class AppGraph(context: Context) {
    /** For work that should outlive a screen, such as saving a scrobble. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settings = Settings(context)
    val repository = ScrobbleRepository(ScrobbleDb(context))
    val submitter = ScrobbleSubmitter(
        settings,
        repository,
        userAgent = "Scrobbler/${BuildConfig.VERSION_NAME} (Android; +https://github.com/teamomuito/code)",
    )

    init {
        scope.launch { repository.refresh() }
    }
}

val Context.graph: AppGraph get() = (applicationContext as ScrobblerApp).graph
