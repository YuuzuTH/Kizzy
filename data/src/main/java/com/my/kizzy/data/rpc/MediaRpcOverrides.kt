/*
 * Copyright (C) 2026 Yuzu夕
 * Kizzy is free software licensed under GPL-3.0.
 * MediaRpcOverrides.kt — per-media-app custom presence template, part of Kizzy by Yuzu夕.
 */

package com.my.kizzy.data.rpc

import com.my.kizzy.data.utils.toRpcImage
import com.my.kizzy.preference.Prefs
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persistence + resolution for per-media-app RPC overrides. Reuses [AppRpcOverride]'s exact
 * shape (name/image/details/state/buttons/activity type/status/party/…) — the mechanism ported
 * over from App Detection's per-app editor — but stores it under its own [Prefs] key rather
 * than sharing [AppRpcOverrides]. Kept separate on purpose: the same package can plausibly
 * appear in both App Detection and Media RPC (they're mutually exclusive *modes*, not mutually
 * exclusive *apps*), and the two contexts don't mean the same thing for the same override — a
 * static "Coding session" name set for Spotify under App Detection would be a surprising,
 * silent override of every song's title if it also applied to Media RPC.
 *
 * The key difference from [AppRpcOverrides]: every text field here is a *template*, processed
 * fresh against the current track via [TemplateProcessor] on every presence update — a static
 * override wouldn't be useful for something that changes every song. A blank/unset field falls
 * back to [default], which the caller has already computed from the existing toggle-driven
 * logic in `GetCurrentlyPlayingMedia` (song-as-title vs. app-as-title, artist/album toggles,
 * etc.) — so an override with only [AppRpcOverride.name] set behaves exactly like before for
 * every other field, same additive philosophy as App Detection's overrides.
 */
object MediaRpcOverrides {
    private val json = Json { ignoreUnknownKeys = true }

    fun all(): Map<String, AppRpcOverride> =
        runCatching {
            json.decodeFromString<Map<String, AppRpcOverride>>(
                Prefs[Prefs.MEDIA_RPC_OVERRIDES, "{}"]
            )
        }.getOrDefault(emptyMap())

    fun of(packageName: String): AppRpcOverride? = all()[packageName]

    fun set(packageName: String, override: AppRpcOverride) {
        val map = all().toMutableMap()
        if (override.isEmpty) map.remove(packageName) else map[packageName] = override
        Prefs[Prefs.MEDIA_RPC_OVERRIDES] = json.encodeToString(map)
    }

    fun remove(packageName: String) {
        val map = all().toMutableMap()
        if (map.remove(packageName) != null) {
            Prefs[Prefs.MEDIA_RPC_OVERRIDES] = json.encodeToString(map)
        }
    }

    fun clearAll() {
        Prefs.remove(Prefs.MEDIA_RPC_OVERRIDES)
    }

    /**
     * Fully-resolved per-track presence from [override] (already looked up by the caller via
     * [of] — taken as a parameter here rather than re-deriving it from a package name, so a
     * single presence update doesn't decode the whole overrides map from Prefs twice): every
     * override text field is run through [templateProcessor] (falling back to [default]'s
     * corresponding field when blank), image fields additionally accept the special tokens in
     * [TemplateKeys] ([coverArt]/[appIcon]/[playbackIcon] resolve those tokens; anything else is
     * treated as a literal URL/uploaded-asset id exactly like [AppRpcOverrides] does — [appIcon]
     * is a supplier, not a plain value, so its (non-trivial: it reads+decodes the saved-images
     * map) construction is skipped entirely unless an override actually references
     * {{app_icon}}). [globalActivityType]/[globalStatus]/[globalButtons] are the pre-override,
     * Settings-driven defaults Media RPC already computed before this override system existed —
     * used whenever the override leaves that field unset, same null-is-additive contract as
     * everywhere else in [AppRpcOverride].
     */
    fun resolveFull(
        override: AppRpcOverride?,
        default: CommonRpc,
        templateProcessor: TemplateProcessor,
        coverArt: RpcImage?,
        appIcon: () -> RpcImage,
        playbackIcon: RpcImage?,
        globalActivityType: Int,
        globalStatus: String,
        globalButtons: List<RpcButton>?,
    ): CommonRpc {
        val o = override
        if (o == null || o.isEmpty) {
            return default.copy(
                type = globalActivityType,
                status = globalStatus,
                buttons = globalButtons,
            )
        }

        fun text(v: String?, fallback: String?) =
            templateProcessor.process(v)?.takeIf { it.isNotBlank() } ?: fallback

        fun image(raw: String?, fallback: RpcImage?): RpcImage? {
            val v = raw?.trim()
            return when {
                v.isNullOrBlank() -> fallback
                v == TemplateKeys.IMAGE_COVER_ART -> coverArt ?: fallback
                v == TemplateKeys.IMAGE_APP_ICON -> appIcon()
                v == TemplateKeys.IMAGE_PLAYBACK_ICON -> playbackIcon ?: fallback
                else -> v.toRpcImage()
            }
        }

        val buttons = buildList {
            val b1Text = text(o.button1Text, null)
            val b1Url = templateProcessor.process(o.button1Url)?.takeIf { it.isNotBlank() }
            if (!b1Text.isNullOrBlank() && !b1Url.isNullOrBlank()) add(RpcButton(b1Text, b1Url))
            val b2Text = text(o.button2Text, null)
            val b2Url = templateProcessor.process(o.button2Url)?.takeIf { it.isNotBlank() }
            if (!b2Text.isNullOrBlank() && !b2Url.isNullOrBlank()) add(RpcButton(b2Text, b2Url))
        }.takeIf { it.isNotEmpty() } ?: globalButtons

        return CommonRpc(
            name = text(o.name, default.name) ?: default.name,
            type = o.activityType ?: globalActivityType,
            details = text(o.details, default.details),
            state = text(o.state, default.state),
            partyCurrentSize = o.partyCurrentSize.takeIf { o.partyMaxSize != null } ?: default.partyCurrentSize,
            partyMaxSize = o.partyMaxSize.takeIf { o.partyCurrentSize != null } ?: default.partyMaxSize,
            largeImage = image(o.imageUrl, default.largeImage),
            smallImage = image(o.smallImageUrl, default.smallImage),
            largeText = text(o.largeText, default.largeText),
            smallText = text(o.smallText, default.smallText),
            time = default.time,
            packageName = default.packageName,
            buttons = buttons,
            streamUrl = templateProcessor.process(o.streamUrl)?.takeIf { it.isNotBlank() },
            status = o.status?.takeIf { it.isNotBlank() } ?: globalStatus,
        )
    }
}
