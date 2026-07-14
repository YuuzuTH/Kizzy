/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * MediaScreenViewModel.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_media_rpc

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.kizzy.data.rpc.AppRpcOverride
import com.my.kizzy.data.rpc.MediaRpcOverrides
import com.my.kizzy.data.utils.getInstalledApps
import com.my.kizzy.domain.use_case.upload_galleryImage.UploadGalleryImageUseCase
import com.my.kizzy.preference.Prefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MediaScreenViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val uploadGalleryImageUseCase: UploadGalleryImageUseCase,
) : ViewModel() {
    private val _state: MutableStateFlow<MediaAppsState> = MutableStateFlow(MediaAppsState())
    val state = _state.asStateFlow()

    init {
        getInstalledApps()
    }

    private fun getInstalledApps() {
        viewModelScope.launch(context = Dispatchers.Default) {
            val appList = getInstalledApps(
                context = context,
                isEnabled = Prefs::isMediaAppEnabled
            ).sortedBy { !it.isChecked }
            val enabledApps = appList.associate { it.pkg to it.isChecked }
            _state.update {
                MediaAppsState(
                    apps = appList,
                    isLoading = false,
                    enabledApps = enabledApps,
                    overrides = MediaRpcOverrides.all(),
                )
            }
        }

    }

    fun updateMediaAppEnabled(pkg: String) {
        viewModelScope.launch(Dispatchers.IO) {
            Prefs.saveMediaAppToPrefs(pkg)
            _state.update { currentState ->
                currentState.copy(
                    enabledApps = currentState.enabledApps.toMutableMap().apply {
                        this[pkg] = !(this[pkg] ?: false)
                    }
                )
            }
        }
    }

    /** Save (or clear, when the override is empty) the full per-app presence template. */
    fun setOverride(pkg: String, override: AppRpcOverride) {
        viewModelScope.launch(Dispatchers.IO) {
            MediaRpcOverrides.set(pkg, override)
            _state.update { it.copy(overrides = MediaRpcOverrides.all()) }
        }
    }

    /** Remove any customization for [pkg]. */
    fun clearOverride(pkg: String) {
        viewModelScope.launch(Dispatchers.IO) {
            MediaRpcOverrides.remove(pkg)
            _state.update { it.copy(overrides = MediaRpcOverrides.all()) }
        }
    }

    /** Remove every per-app customization at once. */
    fun clearAllOverrides() {
        viewModelScope.launch(Dispatchers.IO) {
            MediaRpcOverrides.clearAll()
            _state.update { it.copy(overrides = MediaRpcOverrides.all()) }
        }
    }

    /** Upload a device-picked image and return the asset id to store as an override image URL. */
    fun uploadImage(file: File, onResult: (String) -> Unit) {
        viewModelScope.launch {
            uploadGalleryImageUseCase(file)?.let {
                withContext(Dispatchers.Main) { onResult(it.drop(3)) }
            }
        }
    }
}
