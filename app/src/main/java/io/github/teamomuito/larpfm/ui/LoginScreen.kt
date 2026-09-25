package io.github.teamomuito.larpfm.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.teamomuito.larpfm.R

private const val API_ACCOUNT_URL = "https://www.last.fm/api/account/create"

@Composable
fun LoginScreen(viewModel: MainViewModel) {
    val state by viewModel.loginState.collectAsStateWithLifecycle()
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var apiKey by rememberSaveable { mutableStateOf(viewModel.savedApiKey) }
    var apiSecret by rememberSaveable { mutableStateOf(viewModel.savedApiSecret) }
    var showApiFields by rememberSaveable { mutableStateOf(apiKey.isBlank() || apiSecret.isBlank()) }
    var usePassword by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val loading = state == LoginState.Loading
    val hasApiKey = apiKey.isNotBlank() && apiSecret.isNotBlank()
    val canSignInWithPassword = !loading && hasApiKey && username.isNotBlank() && password.isNotEmpty()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_cat),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
            )
            Text("larp.fm", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Sign in to Last.fm to scrobble what you play in any music app.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { uriHandler.openUri(viewModel.webSignInUrl(apiKey.trim(), apiSecret.trim())) },
                enabled = !loading && hasApiKey,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (loading && !usePassword) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Sign in with Last.fm")
                }
            }
            Text(
                "Opens Last.fm in your browser. Tap Allow and you'll land back here, signed in.",
                style = MaterialTheme.typography.bodySmall,
            )

            (state as? LoginState.Error)?.let {
                Text(it.message, color = MaterialTheme.colorScheme.error)
            }

            if (usePassword) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username or email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = { viewModel.signIn(username.trim(), password, apiKey.trim(), apiSecret.trim()) },
                    enabled = canSignInWithPassword,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Sign in with password")
                    }
                }
                Text(
                    "Your password goes straight to Last.fm and isn't stored.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                TextButton(onClick = { usePassword = true }) {
                    Text("Sign in with password instead")
                }
            }

            if (showApiFields) {
                Text("Last.fm API account", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Scrobbling needs an API key. Creating one is free and takes a minute; " +
                        "the application name and description can be anything.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { uriHandler.openUri(API_ACCOUNT_URL) }) {
                    Text("Create an API account")
                }
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiSecret,
                    onValueChange = { apiSecret = it },
                    label = { Text("Shared secret") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                TextButton(onClick = { showApiFields = true }) {
                    Text("Use a different API key")
                }
            }
        }
    }
}
