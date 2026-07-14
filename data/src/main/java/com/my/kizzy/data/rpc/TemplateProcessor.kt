/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * TemplateProcessor.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.data.rpc

import android.media.MediaMetadata
import java.net.URLEncoder
import java.util.Locale

class TemplateKeys {
    companion object {
        const val MEDIA_TITLE = "{{media_title}}"
        const val MEDIA_ARTIST = "{{media_artist}}"
        const val MEDIA_AUTHOR = "{{media_author}}"
        const val APP_NAME = "{{app_name}}"

        // Added for Media RPC per-app overrides (2026-07) — same "media_"-prefixed naming
        // convention as the four keys above, matched by the unmatched-placeholder cleanup
        // regex in [TemplateProcessor.process] (which also explicitly lists cover_art /
        // playback_icon below, since those two don't have a media_/app_ prefix).
        const val MEDIA_ALBUM = "{{media_album}}"
        const val MEDIA_PLAYBACK_STATE = "{{media_playback_state}}"
        const val MEDIA_POSITION = "{{media_position}}"
        const val MEDIA_DURATION = "{{media_duration}}"
        const val MEDIA_REMAINING = "{{media_remaining}}"
        // URL-encoded variants — meant for building a search-link button URL
        // (e.g. a YouTube search for the currently playing track) that still updates per song.
        const val MEDIA_TITLE_URLENCODED = "{{media_title_urlencoded}}"
        const val MEDIA_ARTIST_URLENCODED = "{{media_artist_urlencoded}}"

        // Image-field tokens — resolved to a specific RpcImage by MediaRpcOverrides.resolveFull
        // instead of a text substitution; listed here for a single source of truth on the
        // literal strings the editor UI offers and the resolver matches against.
        const val IMAGE_COVER_ART = "{{cover_art}}"
        const val IMAGE_APP_ICON = "{{app_icon}}"
        const val IMAGE_PLAYBACK_ICON = "{{playback_icon}}"
    }
}

/** m:ss, or h:mm:ss once past an hour. Negative/unknown durations render as "0:00". */
fun formatMillis(ms: Long?): String {
    val totalSeconds = ((ms ?: 0L) / 1000).coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

class TemplateProcessor(
    private val mediaMetadata: MediaMetadata? = null,
    private val mediaPlayerAppName: String? = null,
    private val mediaPlayerPackageName: String? = null,
    private val detectedAppInfo: CommonRpc? = null,
    // Media RPC extras — none of these come from MediaMetadata, so they're passed in
    // separately by the caller, which already computed them for the legacy default presence.
    private val album: String? = null,
    private val playbackStateText: String? = null,
    private val positionMs: Long? = null,
    private val durationMs: Long? = null,
) {
    fun process(template: String?): String? {
        if (template.isNullOrBlank()) return null

        var result = template

        if (mediaMetadata != null && mediaPlayerAppName != null && mediaPlayerPackageName != null) {
            val title = mediaMetadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
            val artist = mediaMetadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            fun urlPart(s: String) = runCatching { URLEncoder.encode(s, "UTF-8") }.getOrDefault("")
            result = result
                .replace(TemplateKeys.MEDIA_TITLE, title)
                .replace(
                    TemplateKeys.MEDIA_ARTIST,
                    artist
                )
                .replace(
                    TemplateKeys.MEDIA_AUTHOR,
                    mediaMetadata.getString(MediaMetadata.METADATA_KEY_AUTHOR) ?: ""
                )
                .replace(TemplateKeys.APP_NAME, mediaPlayerAppName)
                .replace(TemplateKeys.MEDIA_ALBUM, album ?: "")
                .replace(TemplateKeys.MEDIA_PLAYBACK_STATE, playbackStateText ?: "")
                .replace(TemplateKeys.MEDIA_POSITION, formatMillis(positionMs))
                .replace(TemplateKeys.MEDIA_DURATION, formatMillis(durationMs))
                .replace(
                    TemplateKeys.MEDIA_REMAINING,
                    formatMillis(durationMs?.let { d -> positionMs?.let { p -> d - p } })
                )
                .replace(TemplateKeys.MEDIA_TITLE_URLENCODED, urlPart(title))
                .replace(TemplateKeys.MEDIA_ARTIST_URLENCODED, urlPart(artist))
        } else if (detectedAppInfo != null) {
            result = result.replace(TemplateKeys.APP_NAME, detectedAppInfo.name)
        }

        // NOTE: remove unreplaced placeholders — also covers the image-only tokens
        // (cover_art/playback_icon don't have a media_/app_ prefix, so they need their own
        // alternative here; app_icon already matched app_[^}]+ before this was added, kept
        // explicit anyway so all three image tokens are handled the same visible way).
        result = result.replace(
            Regex("\\{\\{(media_[^}]+|app_[^}]+|cover_art|playback_icon)\\}\\}"), ""
        )

        return result
    }
}
