package io.github.teamomuito.scrobbler.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        enableEdgeToEdge()
        setContent {
            ScrobblerTheme {
                val account by viewModel.account.collectAsStateWithLifecycle()
                val signedIn = account
                if (signedIn == null) {
                    LoginScreen(viewModel)
                } else {
                    HomeScreen(viewModel, signedIn)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may be coming back from granting notification access.
        viewModel.refreshNotificationAccess()
    }
}
