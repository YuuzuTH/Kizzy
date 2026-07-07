/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * AppsScreenViewModel.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_apps_rpc

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.kizzy.data.rpc.AppRpcOverride
import com.my.kizzy.data.rpc.AppRpcOverrides
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
class AppsScreenViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val uploadGalleryImageUseCase: UploadGalleryImageUseCase,
) : ViewModel() {
    private val _state: MutableStateFlow<AppsState> = MutableStateFlow(AppsState())
    val state = _state.asStateFlow()

    init {
        getInstalledApps()
    }

    fun getInstalledApps() {
        viewModelScope.launch(context = Dispatchers.Default) {
            val appList = getInstalledApps(
                context = context,
                isEnabled = Prefs::isAppEnabled
            ).sortedBy { !it.isChecked }
            val enabledApps = appList.associate { it.pkg to it.isChecked }
            _state.update {
                AppsState(
                    apps = appList,
                    isLoading = false,
                    enabledApps = enabledApps,
                    overrides = AppRpcOverrides.all()
                )
            }
        }
    }

    /** Save (or clear, when the override is empty) the full per-app presence customization. */
    fun setOverride(pkg: String, override: AppRpcOverride) {
        viewModelScope.launch(Dispatchers.IO) {
            AppRpcOverrides.set(pkg, override)
            _state.update { it.copy(overrides = AppRpcOverrides.all()) }
        }
    }

    /** Remove any customization for [pkg]. */
    fun clearOverride(pkg: String) {
        viewModelScope.launch(Dispatchers.IO) {
            AppRpcOverrides.remove(pkg)
            _state.update { it.copy(overrides = AppRpcOverrides.all()) }
        }
    }

    /** Remove every per-app customization at once. */
    fun clearAllOverrides() {
        viewModelScope.launch(Dispatchers.IO) {
            AppRpcOverrides.clearAll()
            _state.update { it.copy(overrides = AppRpcOverrides.all()) }
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

    fun updateAppEnabled(pkg: String) {
        viewModelScope.launch(Dispatchers.IO) {
            Prefs.saveToPrefs(pkg)
            _state.update { currentState ->
                currentState.copy(
                    enabledApps = currentState.enabledApps.toMutableMap().apply {
                        this[pkg] = !(this[pkg] ?: false)
                    },
                )
            }
        }
    }
}