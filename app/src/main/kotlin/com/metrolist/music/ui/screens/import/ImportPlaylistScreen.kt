/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.import

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.SpotifyClientIdKey
import com.metrolist.music.constants.SpotifyClientSecretKey
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.imports.PlaylistImporter
import com.metrolist.music.imports.SpotifyClient
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Analyzing : ImportUiState
    data class Resolving(val done: Int, val total: Int) : ImportUiState
    data object NeedsSpotifyCredentials : ImportUiState
    data class Preview(
        val fetched: PlaylistImporter.FetchedPlaylist,
        val items: List<ImportItemUi>,
        val sourceLabel: String,
    ) : ImportUiState

    data object Creating : ImportUiState
    data class Success(
        val playlistId: String,
        val imported: Int,
        val total: Int,
        val items: List<ImportItemUi>,
        val importedIndices: Set<Int>,
    ) : ImportUiState
    data object EmptyTracks : ImportUiState
    data class Error(val kind: PlaylistImporter.ImportException.Kind) : ImportUiState
}

private data class ImportItemUi(
    val song: SongItem?,
    val title: String,
    val subtitle: String,
    val album: String?,
    val duration: Int?,
    val thumbnail: String?,
    val matched: Boolean,
    val inLibrary: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPlaylistScreen(navController: NavController) {
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    var state by remember { mutableStateOf<ImportUiState>(ImportUiState.Idle) }
    var url by rememberSaveable { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(0) }
    var progressTotal by remember { mutableIntStateOf(0) }

    val (spotifyClientId, onSpotifyClientIdChange) = rememberPreference(SpotifyClientIdKey, "")
    val (spotifyClientSecret, onSpotifyClientSecretChange) =
        rememberPreference(SpotifyClientSecretKey, "")

    val startAnalyze: () -> Unit = {
        val detected = PlaylistImporter.detectSource(url)
        val needsCredentials =
            detected != null &&
                detected.first == PlaylistImporter.Source.SPOTIFY &&
                (spotifyClientId.isBlank() || spotifyClientSecret.isBlank())

        when {
            detected == null ->
                state = ImportUiState.Error(PlaylistImporter.ImportException.Kind.INVALID_URL)

            needsCredentials -> state = ImportUiState.NeedsSpotifyCredentials

            else -> {
                val source = detected.first
                val playlistId = detected.second
                coroutineScope.launch(Dispatchers.IO) {
                    state = ImportUiState.Analyzing
                    progress = 0
                    progressTotal = 0
                    try {
                        val fetched =
                            if (source == PlaylistImporter.Source.SPOTIFY) {
                                SpotifyClient(spotifyClientId, spotifyClientSecret).use { client ->
                                    client.playlist(playlistId) { loaded ->
                                        progress = loaded
                                    }
                                }
                            } else {
                                PlaylistImporter.fetchYouTubePlaylist(
                                    playlistId,
                                    source,
                                ) { loaded ->
                                    progress = loaded
                                }
                            }
                        progressTotal = fetched.trackCount

                        if (fetched.trackCount == 0) {
                            state = ImportUiState.EmptyTracks
                            return@launch
                        }

                        val resolved =
                            PlaylistImporter.resolve(
                                fetched = fetched,
                                inLibrary = { id -> database.getSongByIdBlocking(id) != null },
                                onProgress = { done, total ->
                                    progress = done
                                    progressTotal = total
                                },
                            )

                        val items =
                            resolved.map { resolvedTrack ->
                                val song = resolvedTrack.song
                                val subscript =
                                    song?.artists?.joinToString(", ") { it.name }
                                        ?: resolvedTrack.source.artists.joinToString(", ")
                                ImportItemUi(
                                    song = song,
                                    title = song?.title ?: resolvedTrack.source.title,
                                    subtitle = subscript,
                                    album = song?.album?.name ?: resolvedTrack.source.album,
                                    duration = song?.duration ?: resolvedTrack.source.duration,
                                    thumbnail = song?.thumbnail?.ifBlank { null },
                                    matched = song != null,
                                    inLibrary = resolvedTrack.inLibrary,
                                )
                            }

                        state =
                            ImportUiState.Preview(
                                fetched = fetched,
                                items = items,
                                sourceLabel =
                                    when (fetched.source) {
                                        PlaylistImporter.Source.YOUTUBE -> "YouTube"
                                        PlaylistImporter.Source.YOUTUBE_MUSIC -> "YouTube Music"
                                        PlaylistImporter.Source.SPOTIFY -> "Spotify"
                                    },
                            )
                    } catch (e: PlaylistImporter.ImportException) {
                        state = ImportUiState.Error(e.kind)
                    }
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            ),
    ) {
        when (val current = state) {
            ImportUiState.Idle -> IdleContent(
                url = url,
                onUrlChange = { url = it },
                onAnalyze = startAnalyze,
                onOpenCredentials = { state = ImportUiState.NeedsSpotifyCredentials },
            )

            ImportUiState.Analyzing -> StepProgressContent(
                stepTitle = stringResource(R.string.import_playlist_step_analyzing),
                activeStep = 0,
                done = progress,
                total = progressTotal,
            )

            is ImportUiState.Resolving -> StepProgressContent(
                stepTitle = stringResource(R.string.import_playlist_step_matching),
                activeStep = 1,
                done = current.done,
                total = current.total,
            )

            ImportUiState.NeedsSpotifyCredentials -> SpotifyCredentialsContent(
                clientId = spotifyClientId,
                onClientIdChange = onSpotifyClientIdChange,
                clientSecret = spotifyClientSecret,
                onClientSecretChange = onSpotifyClientSecretChange,
                onSave = startAnalyze,
                onBack = { state = ImportUiState.Idle },
                onOpenConsole = { uriHandler.openUri(SPOTIFY_CONSOLE_URL) },
            )

            is ImportUiState.Preview -> PreviewContent(
                state = current,
                onBack = { state = ImportUiState.Idle },
                onCreate = { name, selectedIndices ->
                    coroutineScope.launch(Dispatchers.IO) {
                        state = ImportUiState.Creating
                        progress = 0
                        progressTotal = selectedIndices.size
                        try {
                            val matched =
                                selectedIndices.sorted().mapNotNull { current.items[it].song }
                            val newPlaylist =
                                PlaylistEntity(
                                    name = name,
                                    browseId =
                                        if (current.fetched.source == PlaylistImporter.Source.YOUTUBE ||
                                            current.fetched.source == PlaylistImporter.Source.YOUTUBE_MUSIC
                                        ) {
                                            current.fetched.sourceId
                                        } else {
                                            null
                                        },
                                    thumbnailUrl = current.fetched.thumbnail,
                                )

                            database.withTransaction {
                                insert(newPlaylist)
                                matched.forEach { insert(it.toMediaMetadata()) }
                            }

                            val playlist = database.playlist(newPlaylist.id).firstOrNull()
                            if (playlist != null) {
                                database.addSongsToPlaylist(
                                    playlist,
                                    matched.map { it.id to null },
                                )
                            }
                            state =
                                ImportUiState.Success(
                                    playlistId = newPlaylist.id,
                                    imported = matched.size,
                                    total = current.items.size,
                                    items = current.items,
                                    importedIndices = selectedIndices,
                                )
                        } catch (e: Exception) {
                            state =
                                ImportUiState.Error(
                                    PlaylistImporter.ImportException.Kind.NETWORK,
                                )
                        }
                    }
                },
            )

            ImportUiState.Creating -> StepProgressContent(
                stepTitle = stringResource(R.string.import_playlist_step_creating),
                activeStep = 2,
                done = progress,
                total = progressTotal,
            )

            is ImportUiState.Success -> SuccessContent(
                state = current,
                onOpen = {
                    navController.popBackStack()
                    navController.navigate("local_playlist/${current.playlistId}")
                },
                onImportAnother = { state = ImportUiState.Idle },
            )

            ImportUiState.EmptyTracks -> MessageContent(
                text = stringResource(R.string.import_playlist_no_tracks),
                onDone = { state = ImportUiState.Idle },
            )

            is ImportUiState.Error -> ErrorContent(
                kind = current.kind,
                onRetry = { state = ImportUiState.Idle },
            )
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.import_playlist)) },
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

@Composable
private fun IdleContent(
    url: String,
    onUrlChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    onOpenCredentials: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.import_playlist_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.import_playlist_url)) },
            placeholder = { Text(stringResource(R.string.import_playlist_url_placeholder)) },
        )

        Button(
            onClick = onAnalyze,
            modifier = Modifier.fillMaxWidth(),
            enabled = url.isNotBlank(),
        ) {
            Icon(painterResource(R.drawable.playlist_add), contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.import_playlist_analyze))
        }

        Text(
            text = stringResource(R.string.import_playlist_supported),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
        )

        TextButton(onClick = onOpenCredentials) {
            Icon(painterResource(R.drawable.key), contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.import_playlist_manage_credentials))
        }
    }
}

@Composable
private fun StepProgressContent(
    stepTitle: String,
    activeStep: Int,
    done: Int,
    total: Int,
) {
    val stepShortLabels =
        listOf(
            stringResource(R.string.import_playlist_analyze),
            stringResource(R.string.import_playlist_step_match),
            stringResource(R.string.import_playlist_step_create),
        )

    Column(
        Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            stepShortLabels.forEachIndexed { index, label ->
                val isActive = index == activeStep
                val isDone = index < activeStep
                val circleColor =
                    when {
                        isDone -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        isActive -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                val markerColor =
                    if (isActive || isDone) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier =
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(circleColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isDone) {
                            Icon(
                                painter = painterResource(R.drawable.check),
                                contentDescription = null,
                                tint = markerColor,
                                modifier = Modifier.size(16.dp),
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = markerColor,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (isActive || isDone) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }
        }

        Text(text = stepTitle, style = MaterialTheme.typography.titleMedium)

        if (total > 0) {
            LinearProgressIndicator(
                progress = { (done.toFloat() / total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "$done / $total",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SpotifyCredentialsContent(
    clientId: String,
    onClientIdChange: (String) -> Unit,
    clientSecret: String,
    onClientSecretChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onOpenConsole: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Icon(
            painter = painterResource(R.drawable.music_note),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.import_playlist_credentials_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.import_playlist_credentials_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

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

        TextButton(onClick = onOpenConsole) {
            Text(stringResource(R.string.import_playlist_spotify_console))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.cancel))
            }
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f),
                enabled = clientId.isNotBlank() && clientSecret.isNotBlank(),
            ) {
                Text(stringResource(R.string.import_playlist_save_and_continue))
            }
        }
    }
}

@Composable
private fun PreviewContent(
    state: ImportUiState.Preview,
    onBack: () -> Unit,
    onCreate: (name: String, selectedIndices: Set<Int>) -> Unit,
) {
    var selected by remember(state) { mutableStateOf(state.items.indices.toSet()) }
    var name by remember(state) { mutableStateOf(state.fetched.name) }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.import_playlist_preview_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text =
                    "${state.sourceLabel} · ${state.items.size} ${stringResource(R.string.tracks)}" +
                        " · ${stringResource(R.string.import_playlist_selected, selected.size)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.import_playlist_name)) },
            )
        }

        LazyColumn(
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            itemsIndexed(state.items) { index, item ->
                ImportItemRow(
                    item = item,
                    checked = index in selected,
                    onCheckedChange = { checked ->
                        selected = if (checked) selected + index else selected - index
                    },
                )
            }
        }

        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        onCreate(
                            name.trim().ifBlank { state.fetched.name },
                            selected,
                        )
                    },
                    enabled = selected.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.import_playlist_import_n, selected.size))
                }
            }
        }
    }
}

@Composable
private fun ImportItemRow(
    item: ImportItemUi,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (item.thumbnail != null) {
                AsyncImage(
                    model = item.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painterResource(R.drawable.music_note),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    item.subtitle.ifBlank {
                        stringResource(R.string.unknown)
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!item.matched) {
                Badge(
                    text = stringResource(R.string.import_playlist_no_match),
                    background = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                )
            } else if (item.inLibrary) {
                Badge(
                    text = stringResource(R.string.import_playlist_in_library),
                    background = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }

        if (item.matched) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
}

@Composable
private fun Badge(
    text: String,
    background: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
) {
    Surface(color = background, shape = RoundedCornerShape(4.dp)) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = content,
        )
    }
}

@Composable
private fun SuccessContent(
    state: ImportUiState.Success,
    onOpen: () -> Unit,
    onImportAnother: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painterResource(R.drawable.check),
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.import_playlist_ready),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.import_playlist_ready_summary, state.imported, state.total),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(state.items) { index, item ->
                SuccessItemRow(
                    item = item,
                    imported = index in state.importedIndices,
                )
            }
        }

        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onImportAnother, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.import_playlist_another))
                }
                Button(onClick = onOpen, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.import_playlist_open))
                }
            }
        }
    }
}

@Composable
private fun SuccessItemRow(
    item: ImportItemUi,
    imported: Boolean,
) {
    val iconColor =
        if (imported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter =
                painterResource(
                    if (imported) R.drawable.check else R.drawable.info,
                ),
            contentDescription =
                stringResource(
                    if (imported) R.string.import_playlist_ready_imported else R.string.import_playlist_ready_skipped,
                ),
            tint = iconColor,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MessageContent(
    text: String,
    onDone: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(
            painterResource(R.drawable.info),
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onDone) {
            Text(stringResource(R.string.import_playlist_try_again))
        }
    }
}

@Composable
private fun ErrorContent(
    kind: PlaylistImporter.ImportException.Kind,
    onRetry: () -> Unit,
) {
    val message =
        when (kind) {
            PlaylistImporter.ImportException.Kind.INVALID_URL -> R.string.import_playlist_error_invalid_url
            PlaylistImporter.ImportException.Kind.UNSUPPORTED_SERVICE -> R.string.import_playlist_error_unsupported
            PlaylistImporter.ImportException.Kind.INVALID_PLAYLIST -> R.string.import_playlist_error_not_found
            PlaylistImporter.ImportException.Kind.NETWORK -> R.string.import_playlist_error_network
            PlaylistImporter.ImportException.Kind.RATE_LIMIT -> R.string.import_playlist_error_rate_limit
            PlaylistImporter.ImportException.Kind.NO_SPOTIFY_CREDENTIALS -> R.string.import_playlist_error_credentials
            PlaylistImporter.ImportException.Kind.SPOTIFY_AUTH -> R.string.import_playlist_error_spotify_auth
        }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(
            painterResource(R.drawable.error),
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            text = stringResource(message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onRetry) {
            Text(stringResource(R.string.import_playlist_try_again))
        }
    }
}

private const val SPOTIFY_CONSOLE_URL = "https://developer.spotify.com/dashboard"