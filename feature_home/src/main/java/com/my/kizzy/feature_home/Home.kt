/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * Home.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_home

import android.content.ComponentName
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.my.kizzy.domain.model.toVersion
import com.my.kizzy.domain.model.release.changelogBody
import com.my.kizzy.domain.model.release.isCritical
import com.my.kizzy.domain.model.update.UpdateDownloadState
import com.my.kizzy.domain.model.user.User
import com.my.kizzy.feature_home.feature.Features
import com.my.kizzy.feature_home.feature.HomeFeature
import com.my.kizzy.feature_home.feature.ToolTipContent
import com.my.kizzy.feature_rpc_base.services.KizzyTileService
import com.my.kizzy.feature_settings.SettingsDrawer
import com.my.kizzy.preference.Prefs
import com.my.kizzy.resources.R
import com.my.kizzy.ui.components.ChipSection
import com.my.kizzy.ui.components.CreditDialog
import com.my.kizzy.ui.components.UpdateDialog
import kotlinx.coroutines.launch

// Silent auto-check on launch is throttled to once per 24h. The manual "check for
// updates" toolbar tap ignores this and always checks immediately (see checkForUpdates()).
private const val AUTO_UPDATE_CHECK_THROTTLE_MS = 24L * 60L * 60L * 1000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Home(
    state: HomeScreenState,
    checkForUpdates: () -> Unit,
    downloadState: UpdateDownloadState = UpdateDownloadState.Idle,
    onDownloadUpdate: (downloadUrl: String, versionName: String) -> Unit = { _, _ -> },
    features: List<HomeFeature>,
    user: User?,
    navigateToProfile: () -> Unit,
    navigateToStyleAndAppearance: () -> Unit,
    navigateToLanguages: () -> Unit,
    navigateToAbout: () -> Unit,
    navigateToRpcSettings: () -> Unit,
    navigateToLogsScreen: () -> Unit,
) {
    val ctx = LocalContext.current
    var homeItems by remember {
        mutableStateOf(features)
    }
    var showUpdateDialog by remember {
        mutableStateOf(false)
    }
    var showCreditDialog by remember {
        mutableStateOf(!Prefs[Prefs.CREDIT_DIALOG_SHOWN, false])
    }
    // Whether the check currently in flight (or the one that just completed) was the
    // silent auto-check, as opposed to the user's manual toolbar tap. Only the silent
    // path is subject to the "already dismissed this version" auto-popup gate below —
    // a manual tap always shows the dialog/toast immediately, same as before this
    // feature existed.
    var isSilentUpdateCheck by remember {
        mutableStateOf(false)
    }
    // Toolbar badge: mirrors Prefs.PENDING_UPDATE_TAG so it survives process death/app
    // restart. Re-read on resume (below) and whenever a check completes (further down),
    // not just once at first composition.
    var pendingUpdateTag by remember {
        mutableStateOf(Prefs[Prefs.PENDING_UPDATE_TAG, ""])
    }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            rememberTopAppBarState(),
            canScroll = { true })
    val isCollapsed = scrollBehavior.state.collapsedFraction > 0.55f

    OnLifecycleEvent { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
            homeItems = features
            pendingUpdateTag = Prefs[Prefs.PENDING_UPDATE_TAG, ""]
        }
    }

    // Silent auto-check on launch, throttled to once per 24h. Runs once per Home
    // composition (LaunchedEffect key = Unit does not refire on recomposition) — it
    // will run again if the user navigates away from and back to Home, but the Prefs
    // timestamp check below still enforces the real 24h throttle across that.
    LaunchedEffect(Unit) {
        val now = System.currentTimeMillis()
        val lastCheck = Prefs[Prefs.LAST_UPDATE_CHECK_TIME, 0L]
        if (now - lastCheck > AUTO_UPDATE_CHECK_THROTTLE_MS) {
            Prefs[Prefs.LAST_UPDATE_CHECK_TIME] = now
            isSilentUpdateCheck = true
            checkForUpdates()
        }
    }

    // Reacts once per distinct completed check (silent or manual) — not on every
    // recomposition — to update the persisted badge/dismiss state and decide whether
    // the silent path should auto-pop the dialog. The manual path's dialog/toast is
    // otherwise driven straight off `showUpdateDialog` + `state` in the render code
    // below, same shape as before this feature existed.
    LaunchedEffect(state) {
        val completed = state as? HomeScreenState.LoadingCompleted ?: return@LaunchedEffect
        val needsUpdate = completed.release.toVersion()
            .whetherNeedUpdate(BuildConfig.VERSION_NAME.toVersion())
        val tagName = completed.release.tagName ?: ""

        Prefs[Prefs.PENDING_UPDATE_TAG] = if (needsUpdate) tagName else ""
        pendingUpdateTag = Prefs[Prefs.PENDING_UPDATE_TAG, ""]

        if (isSilentUpdateCheck) {
            isSilentUpdateCheck = false
            if (needsUpdate) {
                val alreadyDismissed = tagName.isNotEmpty() &&
                    tagName == Prefs[Prefs.LAST_DISMISSED_UPDATE_VERSION, ""]
                if (completed.release.isCritical() || !alreadyDismissed) {
                    showUpdateDialog = true
                }
            }
            // Silent path never toasts "no update available" and never forces the
            // dialog closed — it's not the silent check's place to interrupt the user.
        } else if (!needsUpdate) {
            // Manual path, no update available: keep the exact pre-existing toast
            // behavior. (When an update *is* available, showUpdateDialog is already
            // true from the toolbar tap handler, and the render code below picks it
            // up — nothing extra needed here.)
            Toast.makeText(
                ctx,
                ctx.getString(R.string.update_no_updates_available),
                Toast.LENGTH_SHORT
            ).show()
            showUpdateDialog = false
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                SettingsDrawer(
                    user = user,
                    showKizzyQuickieRequestItem = !KizzyTileService.tileAdded.value,
                    onRequestAddTile = {
                        val cn = ComponentName(ctx, KizzyTileService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val sbm = ctx.getSystemService(android.app.StatusBarManager::class.java)
                            sbm.requestAddTileService(
                                    cn,
                                    ctx.getString(R.string.qs_tile_label),
                                    android.graphics.drawable.Icon.createWithResource(ctx, R.drawable.ic_tile_play),
                                    {}
                            ) {}
                        }
                    },
                    navigateToProfile = navigateToProfile,
                    navigateToStyleAndAppearance = navigateToStyleAndAppearance,
                    navigateToLanguages = navigateToLanguages,
                    navigateToAbout = navigateToAbout,
                    navigateToRpcSettings = navigateToRpcSettings,
                    navigateToLogsScreen = navigateToLogsScreen
                )
            }
        }) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = {
                        Text(
                            text = stringResource(id = R.string.welcome) + ", ${user?.globalName ?: user?.username ?: ""}",
                            style = if (isCollapsed) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge,
                            maxLines = if (isCollapsed) 1 else Int.MAX_VALUE,
                            overflow = if (isCollapsed) androidx.compose.ui.text.style.TextOverflow.Ellipsis else androidx.compose.ui.text.style.TextOverflow.Clip,
                            modifier = Modifier.padding(end = if (isCollapsed) 0.dp else 12.dp)
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                        ) {
                            Icon(
                                Icons.Outlined.Menu, Icons.Outlined.Menu.name,
                            )
                        }
                    },
                    actions = {
                        val onUpdateIconClick: () -> Unit = {
                            // Manual tap: always immediate, always shows the
                            // dialog/toast — never gated by throttle or dismiss
                            // history. Reset isSilentUpdateCheck in case a silent
                            // auto-check is still in flight, so its result is
                            // handled by the manual (not silent) branch above.
                            isSilentUpdateCheck = false
                            Toast.makeText(
                                ctx,
                                ctx.getString(R.string.update_check_for_update),
                                Toast.LENGTH_SHORT
                            ).show()
                            checkForUpdates()
                            showUpdateDialog = true
                        }
                        if (pendingUpdateTag.isNotEmpty()) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        modifier = Modifier
                                            .offset(8.dp, (-14).dp)
                                            .size(8.dp)
                                            .clip(CircleShape),
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                    )
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Update,
                                    contentDescription = "Update",
                                    modifier = Modifier.clickable(onClick = onUpdateIconClick)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Update,
                                contentDescription = "Update",
                                modifier = Modifier.clickable(onClick = onUpdateIconClick)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { navigateToProfile() }) {
                            if (user != null) {
                                AsyncImage(
                                    model = user.getAvatarImage(),
                                    modifier = Modifier
                                        .size(52.dp)
                                        .border(
                                            2.dp,
                                            MaterialTheme.colorScheme.secondaryContainer,
                                            CircleShape,
                                        )
                                        .clip(CircleShape),
                                    placeholder = painterResource(R.drawable.error_avatar),
                                    contentDescription = user.username
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Person,
                                    contentDescription = Icons.Default.Person.name,
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier.padding(paddingValues),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    ChipSection()
                    Text(
                        text = stringResource(id = R.string.features),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(start = 15.dp)
                    )
                }
                item {
                    Features(homeItems) {
                        homeItems = homeItems.mapIndexed { j, item ->
                            if (it == j) {
                                item.copy(isChecked = !item.isChecked)
                            } else {
                                if (item.isChecked) {
                                    item.copy(isChecked = false)
                                } else {
                                    item
                                }
                            }
                        }
                    }
                }
            }
            if (showCreditDialog) {
                CreditDialog(
                    onAcknowledge = {
                        Prefs[Prefs.CREDIT_DIALOG_SHOWN] = true
                        showCreditDialog = false
                    }
                )
            }
            when (state) {
                is HomeScreenState.LoadingCompleted -> {
                    // The "no update available" toast + showUpdateDialog reset for the
                    // manual-tap-no-update case, and the badge/dismiss bookkeeping, now
                    // live in the LaunchedEffect(state) above (runs once per distinct
                    // completed check, not on every recomposition). This block only
                    // renders the dialog itself.
                    if (showUpdateDialog &&
                        state.release.toVersion()
                            .whetherNeedUpdate(BuildConfig.VERSION_NAME.toVersion())
                    ) {
                        with(state.release) {
                            val apkAsset = assets?.firstOrNull {
                                it?.name?.endsWith(".apk") == true
                            } ?: assets?.getOrNull(0)
                            val critical = isCritical()
                            UpdateDialog(
                                newVersionPublishDate = publishedAt ?: "",
                                newVersionSize = apkAsset?.size ?: 0,
                                newVersionLog = changelogBody(),
                                downloadState = downloadState,
                                isCritical = critical,
                                onUpdate = {
                                    val url = apkAsset?.browserDownloadUrl
                                    if (url != null) {
                                        onDownloadUpdate(
                                            url,
                                            (tagName ?: "").removePrefix("v")
                                        )
                                    }
                                },
                                onDismissRequest = {
                                    // Critical releases have nothing to dismiss: no
                                    // dismiss button and no back/outside dismiss (see
                                    // UpdateDialog), so in practice this callback is
                                    // unreachable while critical == true. Guarded here
                                    // too so a critical release can never be recorded
                                    // as "seen and dismissed".
                                    if (!critical) {
                                        Prefs[Prefs.LAST_DISMISSED_UPDATE_VERSION] =
                                            tagName ?: ""
                                    }
                                    showUpdateDialog = false
                                },
                            )
                        }
                    }
                }

                else -> {}
            }
        }
    }
}

@Composable
fun OnLifecycleEvent(onEvent: (owner: LifecycleOwner, event: Lifecycle.Event) -> Unit) {
    val eventHandler = rememberUpdatedState(onEvent)
    val lifecycleOwner = rememberUpdatedState(LocalLifecycleOwner.current)

    DisposableEffect(lifecycleOwner.value) {
        val lifecycle = lifecycleOwner.value.lifecycle
        val observer = LifecycleEventObserver { owner, event ->
            eventHandler.value(owner, event)
        }

        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }
}

@Preview
@Composable
fun HomeScreenPreview() {
    Home(
        state = HomeScreenState.Loading,
        checkForUpdates = {},
        features = fakeFeatures,
        user = fakeUser,
        navigateToProfile = { },
        navigateToStyleAndAppearance = { },
        navigateToLanguages = { },
        navigateToAbout = { },
        navigateToRpcSettings = { }) {

    }
}

val fakeFeatures = listOf(
    HomeFeature(
        title = "App Detection",
        icon = R.drawable.ic_apps,
        shape = RoundedCornerShape(20.dp, 44.dp, 20.dp, 44.dp),
        tooltipText = ToolTipContent.APP_DETECTION_DOCS
    ), HomeFeature(
        title = "Media RPC",
        icon = R.drawable.ic_media_rpc,
        shape = RoundedCornerShape(44.dp, 20.dp, 44.dp, 20.dp),
        tooltipText = ToolTipContent.MEDIA_RPC_DOCS
    ), HomeFeature(
        title = "Custom RPC",
        icon = R.drawable.ic_rpc_placeholder,
        shape = RoundedCornerShape(44.dp, 20.dp, 44.dp, 20.dp),
        tooltipText = ToolTipContent.CUSTOM_RPC_DOCS
    ), HomeFeature(
        title = "Console RPC",
        icon = R.drawable.ic_console_games,
        shape = RoundedCornerShape(20.dp, 44.dp, 20.dp, 44.dp),
        tooltipText = ToolTipContent.CONSOLE_RPC_DOCS
    ),
    HomeFeature(
        title = "Experimental RPC",
        icon = R.drawable.ic_dev_rpc,
        shape = RoundedCornerShape(20.dp, 44.dp, 20.dp, 44.dp),
        tooltipText = ToolTipContent.EXPERIMENTAL_RPC_DOCS
    ),
    HomeFeature(
        title = "Coming Soon",
        icon = R.drawable.ic_info,
        shape = RoundedCornerShape(20.dp, 44.dp, 20.dp, 44.dp),
        showSwitch = false
    )
)

val fakeUser = User(
    accentColor = null,
    avatar = null,
    avatarDecoration = null,
    badges = null,
    banner = null,
    bannerColor = null,
    discriminator = "3050",
    id = null,
    publicFlags = null,
    username = "yzziK",
    special = null,
    verified = false,
    nitro = true,
    bio = "Hello 👋"
)