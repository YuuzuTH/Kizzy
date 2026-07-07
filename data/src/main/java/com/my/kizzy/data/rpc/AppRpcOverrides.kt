/*
 * Copyright (C) 2026 Yuzu夕
 * Kizzy is free software licensed under GPL-3.0.
 * AppRpcOverrides.kt — per-app custom name/image store, part of Kizzy by Yuzu夕.
 */

package com.my.kizzy.data.rpc

import com.my.kizzy.data.utils.toRpcImage
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

    /** Remove every per-app override. */
    fun clearAll() {
        Prefs.remove(Prefs.APP_RPC_OVERRIDES)
    }

    /**
     * Resolve the display name + image for [packageName] in a single read of the
     * stored overrides. Custom values win when set + non-blank; otherwise the
     * [defaultName] / [fallbackImage] are used.
     *
     * Kept for callers that only need name + image (e.g. building the notification icon).
     * For the full presence use [resolveFull].
     */
    fun resolve(
        packageName: String,
        defaultName: String,
        fallbackImage: RpcImage,
    ): Pair<String, RpcImage> {
        val override = of(packageName)
        val name = override?.name?.takeIf { it.isNotBlank() } ?: defaultName
        val image = override?.imageUrl?.toRpcImage() ?: fallbackImage
        return name to image
    }

    /**
     * Fully-resolved per-app presence: every override field merged with the App-Detection
     * defaults in a single read of stored overrides. [defaultName]/[fallbackImage] are the
     * app's real name/icon. A blank text field ⇒ null (Discord omits it); a blank image URL ⇒
     * the fallback for the large image and null for the small one.
     */
    fun resolveFull(
        packageName: String,
        defaultName: String,
        fallbackImage: RpcImage,
    ): ResolvedAppRpc {
        val o = of(packageName)
        fun str(v: String?) = v?.takeIf { it.isNotBlank() }
        fun img(v: String?) = v?.toRpcImage()
        return ResolvedAppRpc(
            name = str(o?.name) ?: defaultName,
            largeImage = img(o?.imageUrl) ?: fallbackImage,
            details = str(o?.details),
            state = str(o?.state),
            largeText = str(o?.largeText),
            smallImage = img(o?.smallImageUrl),
            smallText = str(o?.smallText),
            activityType = o?.activityType ?: 0,
            streamUrl = str(o?.streamUrl),
            button1Text = str(o?.button1Text),
            button1Url = str(o?.button1Url),
            button2Text = str(o?.button2Text),
            button2Url = str(o?.button2Url),
            showTimestamps = o?.showTimestamps ?: true,
        )
    }
}

/**
 * The App-Detection presence for one app after merging its override with the defaults.
 * All text fields are already trimmed to null when blank; [largeImage] is never null.
 */
data class ResolvedAppRpc(
    val name: String,
    val largeImage: RpcImage,
    val details: String?,
    val state: String?,
    val largeText: String?,
    val smallImage: RpcImage?,
    val smallText: String?,
    val activityType: Int,
    val streamUrl: String?,
    val button1Text: String?,
    val button1Url: String?,
    val button2Text: String?,
    val button2Url: String?,
    val showTimestamps: Boolean,
) {
    /** True when this app has at least one presence button configured. */
    val hasButtons: Boolean get() = !button1Text.isNullOrBlank() || !button2Text.isNullOrBlank()
}
