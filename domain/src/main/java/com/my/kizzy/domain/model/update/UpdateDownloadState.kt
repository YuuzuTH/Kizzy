/*
 * Copyright (C) 2026 Yuzu夕
 * Kizzy is free software licensed under GPL-3.0.
 * UpdateDownloadState.kt — in-app self-updater, part of Kizzy by Yuzu夕.
 */

package com.my.kizzy.domain.model.update

/**
 * State of the in-app self-update flow (download APK → install).
 */
sealed interface UpdateDownloadState {
    /** No download in progress. */
    data object Idle : UpdateDownloadState

    /** Downloading the APK. [progress] is 0f..1f. */
    data class Downloading(val progress: Float) : UpdateDownloadState

    /** APK downloaded; the system installer has been launched. */
    data object Installing : UpdateDownloadState

    /**
     * The user must grant "install unknown apps" for Kizzy first.
     * The settings screen has been opened; retry Update afterwards.
     */
    data object PermissionRequired : UpdateDownloadState

    /** Download/install failed. */
    data class Failed(val message: String) : UpdateDownloadState
}
