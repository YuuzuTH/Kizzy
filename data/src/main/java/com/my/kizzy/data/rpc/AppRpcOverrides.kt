/*
 * Copyright (C) 2026 Yuzu夕
 * Kizzy is free software licensed under GPL-3.0.
 * AppRpcOverrides.kt — per-app custom name/image store, part of Kizzy by Yuzu夕.
 */

package com.my.kizzy.data.rpc

import com.my.kizzy.preference.Prefs
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persistence + resolution for per-app RPC overrides (custom name / image).
 * Stored as a JSON map of packageName -> [AppRpcOverride] in [Prefs].
 */
object AppRpcOverrides {
    private val json = Json { ignoreUnknownKeys = true }

    fun all(): Map<String, AppRpcOverride> =
        runCatching {
            json.decodeFromString<Map<String, AppRpcOverride>>(
                Prefs[Prefs.APP_RPC_OVERRIDES, "{}"]
            )
        }.getOrDefault(emptyMap())

    fun of(packageName: String): AppRpcOverride? = all()[packageName]

    /** Set or update an override; an empty one is removed so it never lingers. */
    fun set(packageName: String, override: AppRpcOverride) {
        val map = all().toMutableMap()
        if (override.isEmpty) map.remove(packageName) else map[packageName] = override
        Prefs[Prefs.APP_RPC_OVERRIDES] = json.encodeToString(map)
    }

    fun remove(packageName: String) {
        val map = all().toMutableMap()
        if (map.remove(packageName) != null) {
            Prefs[Prefs.APP_RPC_OVERRIDES] = json.encodeToString(map)
        }
    }

    /**
     * Resolve the display name + image for [packageName] in a single read of the
     * stored overrides. Custom values win when set + non-blank; otherwise the
     * [defaultName] / [fallbackImage] are used.
     */
    fun resolve(
        packageName: String,
        defaultName: String,
        fallbackImage: RpcImage,
    ): Pair<String, RpcImage> {
        val override = of(packageName)
        val name = override?.name?.takeIf { it.isNotBlank() } ?: defaultName
        val image = override?.imageUrl?.takeIf { it.isNotBlank() }
            ?.let { RpcImage.ExternalImage(it) } ?: fallbackImage
        return name to image
    }
}
