/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * AboutScreenViewModel.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_home

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.kizzy.domain.model.Resource
import com.my.kizzy.domain.model.release.Release
import com.my.kizzy.domain.model.update.UpdateDownloadState
import com.my.kizzy.domain.repository.AppUpdater
import com.my.kizzy.domain.use_case.check_for_update.CheckForUpdateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class HomeScreenViewModel @Inject constructor(
    private val checkForUpdateUseCase: CheckForUpdateUseCase,
    private val appUpdater: AppUpdater
): ViewModel() {
    private val _aboutScreenState: MutableStateFlow<HomeScreenState> = MutableStateFlow(
        HomeScreenState.Loading
    )
    val aboutScreenState: StateFlow<HomeScreenState> = _aboutScreenState

    private val _downloadState: MutableStateFlow<UpdateDownloadState> =
        MutableStateFlow(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    // Guards against the silent auto-check on launch and a manual toolbar tap racing
    // each other: only the most recently started check is allowed to land its result.
    private var updateCheckJob: Job? = null

    /** Download the release APK and hand it to the installer, all in-app. */
    fun downloadUpdate(downloadUrl: String, versionName: String) {
        if (_downloadState.value is UpdateDownloadState.Downloading) return
        appUpdater.downloadAndInstall(downloadUrl, versionName)
            .onEach { _downloadState.value = it }
            .launchIn(viewModelScope)
    }

    fun getLatestUpdate() {
        updateCheckJob?.cancel()
        updateCheckJob = checkForUpdateUseCase().onEach { result ->
            when(result){
                is Resource.Success -> {
                    _aboutScreenState.value =
                        HomeScreenState.LoadingCompleted(result.data ?: Release())
                }
                is Resource.Error -> {
                    _aboutScreenState.value =
                        HomeScreenState.Error(result.message ?: "An unexpected error occurred")
                }
                is Resource.Loading -> {
                    _aboutScreenState.value = HomeScreenState.Loading
                }
            }
        }.launchIn(viewModelScope)
    }
    fun setReleaseFromPrefs(release: Release){
        // Also cancel any in-flight network check so it can't land its result after
        // this and clobber the state we're about to set synchronously from cache.
        updateCheckJob?.cancel()
        _aboutScreenState.value = HomeScreenState.LoadingCompleted(release)
    }
}

@Stable
sealed interface HomeScreenState {
    @Stable
    object Loading: HomeScreenState
    @Stable
    class Error(val error: String?): HomeScreenState
    @Stable
    class LoadingCompleted(val release: Release): HomeScreenState
}