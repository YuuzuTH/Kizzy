/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * MediaRpc.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_media_rpc

import com.my.kizzy.feature_rpc_base.stopRpcService

import android.annotation.SuppressLint
import android.content.Intent
import android.media.MediaMetadata
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.filled.PlaylistAddCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.my.kizzy.data.rpc.AppRpcOverride
import com.my.kizzy.data.rpc.TemplateKeys
import com.my.kizzy.data.rpc.TemplateProcessor
import com.my.kizzy.feature_rpc_base.AppUtils
import com.my.kizzy.feature_rpc_base.services.AppDetectionService
import com.my.kizzy.feature_rpc_base.services.CustomRpcService
import com.my.kizzy.feature_rpc_base.services.ExperimentalRpc
import com.my.kizzy.feature_rpc_base.services.MediaRpcService
import com.my.kizzy.preference.Prefs
import com.my.kizzy.preference.Prefs.MEDIA_RPC_ALBUM_NAME
import com.my.kizzy.preference.Prefs.MEDIA_RPC_APP_ICON
import com.my.kizzy.preference.Prefs.MEDIA_RPC_ARTIST_NAME
import com.my.kizzy.preference.Prefs.MEDIA_RPC_ENABLE_TIMESTAMPS
import com.my.kizzy.preference.Prefs.MEDIA_RPC_HIDE_ON_PAUSE
import com.my.kizzy.preference.Prefs.MEDIA_RPC_SHOW_PLAYBACK_STATE
import com.my.kizzy.preference.Prefs.MEDIA_RPC_SHOW_SONG_AS_TITLE
import com.my.kizzy.resources.R
import com.my.kizzy.ui.components.AppOverrideDialog
import com.my.kizzy.ui.components.AppsItem
import com.my.kizzy.ui.components.BackButton
import com.my.kizzy.ui.components.MediaOverrideHelpDialog
import com.my.kizzy.ui.components.SearchBar
import com.my.kizzy.ui.components.Subtitle
import com.my.kizzy.ui.components.SwitchBar
import com.my.kizzy.ui.components.preference.PreferenceSwitch
import com.my.kizzy.ui.components.preference.PreferencesHint
import java.io.File
import kotlinx.coroutines.launch

// Placeholders offered in the per-app override editor's text fields — every one of these is
// substituted per track by TemplateProcessor when the presence actually updates; see
// MediaRpcOverrides.resolveFull and GetCurrentPlayingMedia.
private val textCompletions = listOf(
    TemplateKeys.MEDIA_TITLE to R.string.completion_media_title,
    TemplateKeys.MEDIA_ARTIST to R.string.completion_media_artist,
    TemplateKeys.MEDIA_AUTHOR to R.string.completion_media_author,
    TemplateKeys.MEDIA_ALBUM to R.string.completion_media_album,
    TemplateKeys.APP_NAME to R.string.completion_app_name,
    TemplateKeys.MEDIA_PLAYBACK_STATE to R.string.completion_media_playback_state,
    TemplateKeys.MEDIA_POSITION to R.string.completion_media_position,
    TemplateKeys.MEDIA_DURATION to R.string.completion_media_duration,
    TemplateKeys.MEDIA_REMAINING to R.string.completion_media_remaining,
)

// Image fields accept these instead of a URL — resolved to the actual cover art / app icon /
// play-pause icon at update time (MediaRpcOverrides.resolveFull), so users can freely swap
// which image goes where instead of the fixed assignment the toggles above give them.
private val imageCompletions = listOf(
    TemplateKeys.IMAGE_COVER_ART to R.string.completion_cover_art,
    TemplateKeys.IMAGE_APP_ICON to R.string.completion_app_icon,
    TemplateKeys.IMAGE_PLAYBACK_ICON to R.string.completion_playback_icon,
)

// Button URLs get the url-encoded variants instead — meant for building a search-link button
// (e.g. a YouTube/Google search for the currently playing track) that still updates per song.
private val buttonUrlCompletions = listOf(
    TemplateKeys.MEDIA_TITLE_URLENCODED to R.string.completion_media_title_urlencoded,
    TemplateKeys.MEDIA_ARTIST_URLENCODED to R.string.completion_media_artist_urlencoded,
    TemplateKeys.APP_NAME to R.string.completion_app_name,
)

@SuppressLint("MutableCollectionMutableState")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaRPC(
    onBackPressed: () -> Unit,
    state: MediaAppsState,
    hasNotificationAccess: Boolean,
    updateMediaAppEnabled: (String) -> Unit,
    onSetOverride: (pkg: String, override: AppRpcOverride) -> Unit = { _, _ -> },
    onClearOverride: (pkg: String) -> Unit = { },
    onClearAllOverrides: () -> Unit = { },
    onUploadImage: (file: File, onResult: (String) -> Unit) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    var mediaRpcRunning by remember { mutableStateOf(AppUtils.mediaRpcRunning()) }
    var isArtistEnabled by remember { mutableStateOf(Prefs[MEDIA_RPC_ARTIST_NAME, false]) }
    var isAlbumEnabled by remember { mutableStateOf(Prefs[MEDIA_RPC_ALBUM_NAME, false]) }
    var isAppIconEnabled by remember { mutableStateOf(Prefs[MEDIA_RPC_APP_ICON, false]) }
    var isTimestampsEnabled by remember { mutableStateOf(Prefs[MEDIA_RPC_ENABLE_TIMESTAMPS, false]) }
    var hideOnPause by remember { mutableStateOf(Prefs[MEDIA_RPC_HIDE_ON_PAUSE, false]) }
    var isShowPlaybackState by remember { mutableStateOf(Prefs[MEDIA_RPC_SHOW_PLAYBACK_STATE, false]) }
    var showSongAsTitle by remember { mutableStateOf(Prefs[Prefs.MEDIA_RPC_SHOW_SONG_AS_TITLE, false]) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState(),
        canScroll = { true })
    var searchText by remember { mutableStateOf("") }
    var isSearchBarVisible by remember { mutableStateOf(false) }
    var editingPkg by remember { mutableStateOf<String?>(null) }
    var showOnlyCustomized by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val currentState = rememberUpdatedState(state)

    // A fixed sample track the Preview tab resolves templates against — there's no real
    // playback while editing, so "{{media_title}}" etc. would otherwise show up literally
    // unresolved with no idea what it'll actually look like once a song is playing.
    val samplePlaying = stringResource(id = R.string.playback_state_playing)
    val sampleMetadata = remember {
        MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, "Sample Song")
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "Sample Artist")
            .putString(MediaMetadata.METADATA_KEY_AUTHOR, "Sample Artist")
            .build()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.main_mediaRpc),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                },
                navigationIcon = { BackButton { onBackPressed() } },
                actions = {
                    if (isSearchBarVisible) {
                        Column(
                            modifier = Modifier.padding(10.dp)
                        ) {
                            SearchBar(
                                text = searchText,
                                onTextChanged = { searchText = it },
                                onClose = { isSearchBarVisible = false },
                                placeholder = stringResource(id = R.string.search_placeholder)
                            )
                        }
                    } else {
                        IconButton(onClick = { showHelp = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                                contentDescription = stringResource(id = R.string.media_override_help_title),
                            )
                        }
                        IconButton(onClick = { isSearchBarVisible = !isSearchBarVisible }) {
                            Icon(imageVector = Icons.Outlined.Search, contentDescription = "Search")
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) {
        Column(modifier = Modifier.padding(it)) {
            AnimatedVisibility(
                visible = !hasNotificationAccess
            ) {
                PreferencesHint(
                    title = stringResource(id = R.string.permission_required),
                    description = stringResource(id = R.string.request_for_notification_access),
                    icon = Icons.Default.Warning,
                ) {
                    if (!hasNotificationAccess) {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                }
            }
            SwitchBar(
                title = stringResource(id = R.string.enable_mediaRpc),
                isChecked = mediaRpcRunning,
                enabled = hasNotificationAccess
            ) {
                mediaRpcRunning = !mediaRpcRunning
                when (mediaRpcRunning) {
                    true -> {
                        context.stopRpcService(AppDetectionService::class.java)
                        context.stopRpcService(CustomRpcService::class.java)
                        context.stopRpcService(ExperimentalRpc::class.java)
                        context.startService(Intent(context, MediaRpcService::class.java))
                    }

                    false -> context.stopRpcService(MediaRpcService::class.java)
                }
            }
            LazyColumn {
                item {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.enable_artist_name),
                        icon = Icons.Default.Audiotrack,
                        isChecked = isArtistEnabled,
                    ) {
                        isArtistEnabled = !isArtistEnabled
                        Prefs[MEDIA_RPC_ARTIST_NAME] = isArtistEnabled
                    }
                }
                item {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.enable_album_name),
                        icon = Icons.Default.Album,
                        isChecked = isAlbumEnabled
                    ) {
                        isAlbumEnabled = !isAlbumEnabled
                        Prefs[MEDIA_RPC_ALBUM_NAME] = isAlbumEnabled
                    }
                }
                item {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.show_app_icon),
                        icon = Icons.Default.Apps,
                        isChecked = isAppIconEnabled,
                    ) {
                        isAppIconEnabled = !isAppIconEnabled
                        Prefs[MEDIA_RPC_APP_ICON] = isAppIconEnabled
                        if (isAppIconEnabled) {
                            isShowPlaybackState = false
                            Prefs[MEDIA_RPC_SHOW_PLAYBACK_STATE] = false
                        }
                    }
                }
                item {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.enable_timestamps),
                        icon = Icons.Default.Timer,
                        isChecked = isTimestampsEnabled,
                    ) {
                        isTimestampsEnabled = !isTimestampsEnabled
                        Prefs[MEDIA_RPC_ENABLE_TIMESTAMPS] = isTimestampsEnabled
                    }
                }
                item {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.show_playback_state),
                        icon = Icons.Default.PlayCircle,
                        isChecked = isShowPlaybackState,
                    ) {
                        isShowPlaybackState = !isShowPlaybackState
                        Prefs[MEDIA_RPC_SHOW_PLAYBACK_STATE] = isShowPlaybackState
                        if (isShowPlaybackState) {
                            isAppIconEnabled = false
                            Prefs[MEDIA_RPC_APP_ICON] = false
                        }
                    }
                }
                item {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.hide_on_pause),
                        icon = Icons.Default.PauseCircle,
                        isChecked = hideOnPause,
                    ) {
                        hideOnPause = !hideOnPause
                        Prefs[MEDIA_RPC_HIDE_ON_PAUSE] = hideOnPause
                    }
                }
                item {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.show_song_as_title),
                        icon = Icons.Default.PlaylistAddCircle,
                        isChecked = showSongAsTitle,
                    ) {
                        showSongAsTitle = !showSongAsTitle
                        Prefs[MEDIA_RPC_SHOW_SONG_AS_TITLE] = showSongAsTitle
                    }
                }
                item {
                    Subtitle(
                        text = "Apps",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(15.dp, 8.dp)
                    )
                }
                if (state.overrides.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = showOnlyCustomized,
                                onClick = { showOnlyCustomized = !showOnlyCustomized },
                                label = {
                                    Text(
                                        stringResource(
                                            id = R.string.apps_configured_summary,
                                            state.overrides.size
                                        )
                                    )
                                },
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(onClick = onClearAllOverrides) {
                                Text(stringResource(id = R.string.apps_clear_all))
                            }
                        }
                    }
                }
                if (state.isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    items(state.apps.size, key = { idx -> state.apps[idx].pkg }) { i ->
                        val matchesSearch = searchText.isEmpty() ||
                            state.apps[i].name.contains(searchText, ignoreCase = true) ||
                            state.apps[i].pkg.contains(searchText, ignoreCase = true)
                        val matchesFilter =
                            !showOnlyCustomized || state.overrides.containsKey(state.apps[i].pkg)
                        if (matchesSearch && matchesFilter) {
                            AppsItem(
                                name = state.apps[i].name,
                                pkg = state.apps[i].pkg,
                                isChecked = state.enabledApps[state.apps[i].pkg] ?: false,
                                onClick = { updateMediaAppEnabled(state.apps[i].pkg) },
                                onEditClick = { editingPkg = state.apps[i].pkg },
                                isCustomized = state.overrides.containsKey(state.apps[i].pkg),
                            )
                        } else {
                            Spacer(modifier = Modifier.height(0.dp))
                        }
                    }
                }
            }

            if (showHelp) {
                MediaOverrideHelpDialog(onDismissRequest = { showHelp = false })
            }

            editingPkg?.let { pkg ->
                val override = state.overrides[pkg] ?: AppRpcOverride()
                val appName = state.apps.firstOrNull { it.pkg == pkg }?.name ?: pkg
                val otherOverrides = state.overrides
                    .filterKeys { it != pkg }
                    .mapNotNull { (otherPkg, otherOverride) ->
                        state.apps.firstOrNull { it.pkg == otherPkg }?.name?.let { it to otherOverride }
                    }
                // Resolves a field's raw template text against the sample track above — same
                // engine (TemplateProcessor) the real presence update uses, just fed fixed
                // sample values instead of whatever's actually playing.
                val previewProcessor = remember(appName, pkg, samplePlaying) {
                    TemplateProcessor(
                        mediaMetadata = sampleMetadata,
                        mediaPlayerAppName = appName,
                        mediaPlayerPackageName = pkg,
                        album = "Sample Album",
                        playbackStateText = samplePlaying,
                        positionMs = 65_000L,
                        durationMs = 240_000L,
                    )
                }
                AppOverrideDialog(
                    appName = appName,
                    initial = override,
                    onSave = { newOverride ->
                        onSetOverride(pkg, newOverride)
                        editingPkg = null
                    },
                    onClear = {
                        val cleared = state.overrides[pkg]
                        onClearOverride(pkg)
                        editingPkg = null
                        if (cleared != null) {
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.app_override_reset_snackbar_message, appName),
                                    actionLabel = context.getString(R.string.app_override_reset_snackbar_undo),
                                    duration = SnackbarDuration.Short,
                                )
                                if (result == SnackbarResult.ActionPerformed &&
                                    currentState.value.overrides[pkg] == null
                                ) {
                                    onSetOverride(pkg, cleared)
                                }
                            }
                        }
                    },
                    onDismissRequest = { editingPkg = null },
                    onUploadImage = onUploadImage,
                    otherOverrides = otherOverrides,
                    textCompletions = textCompletions,
                    imageCompletions = imageCompletions,
                    buttonUrlCompletions = buttonUrlCompletions,
                    previewTransform = { raw -> previewProcessor.process(raw) ?: raw },
                )
            }
        }
    }
}
