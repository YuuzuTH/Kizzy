/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * AppsRpc.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

@file:Suppress("DEPRECATION")

package com.my.kizzy.feature_apps_rpc

import com.my.kizzy.feature_rpc_base.stopRpcService

import android.annotation.SuppressLint
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppsOutage
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.my.kizzy.feature_rpc_base.AppUtils
import com.my.kizzy.feature_rpc_base.services.AppDetectionService
import com.my.kizzy.feature_rpc_base.services.CustomRpcService
import com.my.kizzy.feature_rpc_base.services.ExperimentalRpc
import com.my.kizzy.feature_rpc_base.services.MediaRpcService
import com.my.kizzy.data.rpc.AppRpcOverride
import com.my.kizzy.resources.R
import com.my.kizzy.ui.components.AppOverrideDialog
import com.my.kizzy.ui.components.AppsItem
import com.my.kizzy.ui.components.BackButton
import com.my.kizzy.ui.components.SearchBar
import com.my.kizzy.ui.components.SwitchBar
import com.my.kizzy.ui.components.preference.PreferencesHint
import java.io.File

@SuppressLint("MutableCollectionMutableState")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsRPC(
    state: AppsState,
    updateAppEnabled: (String) -> Unit,
    onBackPressed: () -> Unit,
    hasUsageAccess: Boolean,
    onSetOverride: (pkg: String, override: AppRpcOverride) -> Unit = { _, _ -> },
    onClearOverride: (pkg: String) -> Unit = { },
    onClearAllOverrides: () -> Unit = { },
    onUploadImage: (file: File, onResult: (String) -> Unit) -> Unit = { _, _ -> },
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState(),
        canScroll = { true })
    val ctx = LocalContext.current
    var serviceEnabled by remember { mutableStateOf(AppUtils.appDetectionRunning()) }
    var searchText by remember { mutableStateOf("") }
    var isSearchBarVisible by remember { mutableStateOf(false) }
    var editingPkg by remember { mutableStateOf<String?>(null) }
    var showOnlyCustomized by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(id = R.string.main_appDetection),
                            style = MaterialTheme.typography.headlineLarge,
                        )
                    }
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
                        IconButton(onClick = { isSearchBarVisible = !isSearchBarVisible }) {
                            Icon(imageVector = Icons.Outlined.Search, contentDescription = "Search")
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
        ) {
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn {
                    item {
                        AnimatedVisibility(
                            visible = !hasUsageAccess
                        ) {
                            PreferencesHint(
                                title = stringResource(id = R.string.usage_access),
                                description = stringResource(id = R.string.usage_access_desc),
                                icon = Icons.Default.AppsOutage,
                            ) {
                                when (hasUsageAccess) {
                                    false -> ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                    else -> Unit
                                }
                            }
                        }
                    }
                    item {
                        SwitchBar(
                            title = stringResource(id = R.string.enable_appsRpc),
                            isChecked = serviceEnabled,
                            enabled = hasUsageAccess
                        ) {
                            serviceEnabled = !serviceEnabled
                            when (serviceEnabled) {
                                true -> {
                                    ctx.stopRpcService(MediaRpcService::class.java)
                                    ctx.stopRpcService(CustomRpcService::class.java)
                                    ctx.stopRpcService(ExperimentalRpc::class.java)
                                    ctx.startService(Intent(ctx, AppDetectionService::class.java))
                                }

                                false -> ctx.stopRpcService(AppDetectionService::class.java)
                            }
                        }
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
                    items(
                        state.apps.size,
                        key = { idx -> state.apps[idx].pkg }
                    ) { i ->
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
                                onClick = { updateAppEnabled(state.apps[i].pkg) },
                                onEditClick = { editingPkg = state.apps[i].pkg },
                                isCustomized = state.overrides.containsKey(state.apps[i].pkg)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(0.dp))
                        }
                    }
                }
            }

            editingPkg?.let { pkg ->
                val override = state.overrides[pkg] ?: AppRpcOverride()
                val appName = state.apps.firstOrNull { it.pkg == pkg }?.name ?: pkg
                AppOverrideDialog(
                    appName = appName,
                    initial = override,
                    onSave = { newOverride ->
                        onSetOverride(pkg, newOverride)
                        editingPkg = null
                    },
                    onClear = {
                        onClearOverride(pkg)
                        editingPkg = null
                    },
                    onDismissRequest = { editingPkg = null },
                    onUploadImage = onUploadImage,
                )
            }
        }
    }
}