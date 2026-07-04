/*
 * Copyright (C) 2026 Yuzu夕
 * Kizzy is free software licensed under GPL-3.0.
 * AppRpcOverride.kt — per-app custom name/image, part of Kizzy by Yuzu夕.
 */

package com.my.kizzy.data.rpc

import kotlinx.serialization.Serializable

/**
 * A user-defined override for how a specific app/game appears in the Rich
 * Presence. Any field left null/blank falls back to the real app name / icon.
 */
@Serializable
data class AppRpcOverride(
    val name: String? = null,
    val imageUrl: String? = null,
) {
    val isEmpty: Boolean
        get() = name.isNullOrBlank() && imageUrl.isNullOrBlank()
}
