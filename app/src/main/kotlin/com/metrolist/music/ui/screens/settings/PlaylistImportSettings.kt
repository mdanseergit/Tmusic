/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import android.widget.Toast
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.SpotifyClientIdKey
import com.metrolist.music.constants.SpotifyClientSecretKey
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistImportSettings(navController: NavController) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    val (clientId, onClientIdChange) = rememberPreference(SpotifyClientIdKey, "")
    val (clientSecret, onClientSecretChange) = rememberPreference(SpotifyClientSecretKey, "")

    Column(
        Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top),
            ),
        )

        Material3SettingsGroup(
            title = stringResource(R.string.settings_spotify_credentials),
            items =
                listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.token),
                        title = { Text(stringResource(R.string.import_playlist_credentials_title)) },
                        description = {
                            Text(stringResource(R.string.settings_spotify_credentials_summary))
                        },
                        onClick = {},
                    ),
                ),
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = clientId,
                onValueChange = onClientIdChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.import_playlist_client_id)) },
            )
            OutlinedTextField(
                value = clientSecret,
                onValueChange = onClientSecretChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.import_playlist_client_secret)) },
                visualTransformation = PasswordVisualTransformation(),
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.import_playlist_credentials_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TextButton(onClick = { uriHandler.openUri(SPOTIFY_CONSOLE_URL) }) {
            Text(stringResource(R.string.import_playlist_spotify_console))
        }

        TextButton(onClick = {
            if (clientId.isBlank() || clientSecret.isBlank()) return@TextButton
            Toast.makeText(
                context,
                R.string.settings_spotify_credentials_saved,
                Toast.LENGTH_SHORT,
            ).show()
            navController.navigateUp()
        }) {
            Text(stringResource(R.string.import_playlist_save_and_continue))
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.settings_playlist_import)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
    )
}

private const val SPOTIFY_CONSOLE_URL = "https://developer.spotify.com/dashboard"