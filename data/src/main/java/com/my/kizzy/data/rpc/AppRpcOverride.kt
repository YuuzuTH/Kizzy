/*
 * Copyright (C) 2026 Yuzu夕
 * Kizzy is free software licensed under GPL-3.0.
 * AppRpcOverride.kt — per-app custom presence, part of Kizzy by Yuzu夕.
 */

package com.my.kizzy.data.rpc

import kotlinx.serialization.Serializable

/**
 * A user-defined override for how a specific app/game appears in the Rich Presence.
 *
 * Every field is optional and additive: anything left null/blank falls back to the app's
 * real name / icon and the App-Detection defaults, so an override with only [name] set
 * behaves exactly like the original single-field version. New fields were appended (never
 * reordered/removed) and [kotlinx.serialization] ignores unknown keys, so overrides saved
 * by an older build still deserialize.
 *
 * @property name         Custom display name (title of the presence).
 * @property imageUrl      Custom large image — an https URL, a Discord `mp:` asset, or a
 *                         media-proxy link. Blank ⇒ the real app icon.
 * @property details       Second line of the presence.
 * @property state         Third line of the presence.
 * @property largeText     Tooltip shown when hovering the large image.
 * @property smallImageUrl Small image overlaid on the large one (same URL rules as [imageUrl]).
 * @property smallText     Tooltip for the small image.
 * @property activityType  Discord activity verb: 0 Playing · 1 Streaming · 2 Listening ·
 *                         3 Watching · 5 Competing. null ⇒ inherit the mode's own default
 *                         (App Detection: always Playing; Media RPC: the "Custom Activity Type"
 *                         Settings toggle). 0 is a real, distinct choice ("explicitly Playing")
 *                         from null ("unset") since the editor added a separate "Default" chip —
 *                         they used to be indistinguishable when the UI could only ever save 0.
 * @property streamUrl     Twitch/YouTube URL — only meaningful together with [activityType] 1.
 * @property button1Text   Label of the first presence button. Blank ⇒ no button.
 * @property button1Url    URL the first button opens.
 * @property button2Text   Label of the second presence button.
 * @property button2Url    URL the second button opens.
 * @property showTimestamps Whether to show the "elapsed" timer. null ⇒ inherit the mode default
 *                          (App Detection shows it). false ⇒ force it off for this app. Re-added
 *                          2026-07-08 with automatic event logging (see AppDetectionService) after
 *                          three earlier attempts (v6.6.1.000/v6.7.1.000/v6.7.2.000) fixed real bugs
 *                          but couldn't be confirmed fixed on-device without real diagnostic data.
 * @property status         Profile status while this app is foreground: "online"/"idle"/"dnd".
 *                          null ⇒ inherit the global default (Settings > custom status).
 * @property partyCurrentSize Current party size shown next to the presence. null (with
 *                            [partyMaxSize]) ⇒ no party shown.
 * @property partyMaxSize   Max party size. Only takes effect together with [partyCurrentSize].
 */
@Serializable
data class AppRpcOverride(
    val name: String? = null,
    val imageUrl: String? = null,
    val details: String? = null,
    val state: String? = null,
    val largeText: String? = null,
    val smallImageUrl: String? = null,
    val smallText: String? = null,
    val activityType: Int? = null,
    val streamUrl: String? = null,
    val button1Text: String? = null,
    val button1Url: String? = null,
    val button2Text: String? = null,
    val button2Url: String? = null,
    val showTimestamps: Boolean? = null,
    val status: String? = null,
    val partyCurrentSize: Int? = null,
    val partyMaxSize: Int? = null,
) {
    /**
     * True when the override carries no meaningful customization, so it can be dropped from
     * storage instead of persisting an all-default entry. [showTimestamps] only counts as
     * customization when it differs from the default (timer on). [activityType] counts as
     * customization whenever it's non-null — including 0, now that the editor can save an
     * explicit "Playing" distinct from "Default" (see its doc comment above); treating 0 as
     * empty would silently drop that explicit choice on save.
     */
    val isEmpty: Boolean
        get() = name.isNullOrBlank() &&
            imageUrl.isNullOrBlank() &&
            details.isNullOrBlank() &&
            state.isNullOrBlank() &&
            largeText.isNullOrBlank() &&
            smallImageUrl.isNullOrBlank() &&
            smallText.isNullOrBlank() &&
            streamUrl.isNullOrBlank() &&
            button1Text.isNullOrBlank() &&
            button1Url.isNullOrBlank() &&
            button2Text.isNullOrBlank() &&
            button2Url.isNullOrBlank() &&
            activityType == null &&
            (showTimestamps == null || showTimestamps) &&
            status.isNullOrBlank() &&
            partyCurrentSize == null &&
            partyMaxSize == null

    /** True when any presence-text/image customization is set (used to flag a "customized" app). */
    val hasCustomization: Boolean
        get() = !isEmpty
}
