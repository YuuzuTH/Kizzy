/*
 * Copyright (C) 2026 Yuzu夕
 * Kizzy is free software licensed under GPL-3.0.
 * AppUpdater.kt — in-app self-updater, part of Kizzy by Yuzu夕.
 */

package com.my.kizzy.domain.repository

import com.my.kizzy.domain.model.update.UpdateDownloadState
import kotlinx.coroutines.flow.Flow

/**
 * Downloads a release APK and hands it to the system installer, entirely in-app
 * (no browser). Emits [UpdateDownloadState] progress as it goes.
 */
interface AppUpdater {
    fun downloadAndInstall(downloadUrl: String, versionName: String): Flow<UpdateDownloadState>
}
