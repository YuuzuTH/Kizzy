/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * GetCurrentlyPlayingMedia.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.data.get_current_data.media

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.blankj.utilcode.util.AppUtils
import com.my.kizzy.data.rpc.CommonRpc
import com.my.kizzy.data.rpc.Constants.APPLICATION_ID
import com.my.kizzy.data.rpc.MediaRpcOverrides
import com.my.kizzy.data.rpc.RpcButton
import com.my.kizzy.data.rpc.Timestamps
import com.my.kizzy.data.rpc.RpcImage
import com.my.kizzy.data.rpc.TemplateProcessor
import com.my.kizzy.domain.model.rpc.RpcButtons
import com.my.kizzy.preference.Prefs
import com.my.kizzy.resources.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** The result of resolving the current track: the ready-to-send [rpc] plus whether the
 *  elapsed-timer should be sent with it — kept alongside [rpc] instead of a separate global
 *  read because a per-app override can now set its own [com.my.kizzy.data.rpc.AppRpcOverride.showTimestamps],
 *  same as App Detection's per-app timer toggle. */
data class MediaPresence(
    val rpc: CommonRpc,
    val enableTimestamps: Boolean,
)

class GetCurrentPlayingMedia @Inject constructor(
    private val metadataResolver: MetadataResolver,
    private val componentName: ComponentName,
    @ApplicationContext private val context: Context
) {
    object Assets {
        val PLAY = "app-assets/$APPLICATION_ID/1300361266212241430.png";
        val PAUSE = "app-assets/$APPLICATION_ID/1300361619490209802.png";
        val STOP = "app-assets/$APPLICATION_ID/1300361702621188160.png";
    }

    private fun getPlaybackStateIcon(playbackState: Int): RpcImage {
        return when (playbackState) {
            PlaybackState.STATE_PLAYING -> RpcImage.DiscordImage(Assets.PLAY)
            PlaybackState.STATE_PAUSED -> RpcImage.DiscordImage(Assets.PAUSE)
            PlaybackState.STATE_STOPPED -> RpcImage.DiscordImage(Assets.STOP)
            else -> RpcImage.DiscordImage(Assets.PAUSE)
        }
    }

    // Text equivalent of getPlaybackStateIcon, for the {{media_playback_state}} template
    // placeholder — same three states, nothing fancier (buffering/error aren't distinguished
    // by PlaybackState here any more than the icon above does).
    private fun getPlaybackStateText(playbackState: Int): String = context.getString(
        when (playbackState) {
            PlaybackState.STATE_PLAYING -> R.string.playback_state_playing
            PlaybackState.STATE_STOPPED -> R.string.playback_state_stopped
            else -> R.string.playback_state_paused
        }
    )

    operator fun invoke(): MediaPresence {
        var largeIcon: RpcImage? = null
        var smallIcon: RpcImage? = null
        var smallText: String? = null
        var timestamps: Timestamps? = null
        val mediaSessionManager =
            context.getSystemService(Service.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val sessions = mediaSessionManager.getActiveSessions(componentName)
        for (mediaController in sessions) {
            val pkg = mediaController.packageName
            // If the app is not enabled for media rpc, skip it
            if (!Prefs.isMediaAppEnabled(pkg)) {
                continue
            }

            val override = MediaRpcOverrides.of(pkg)
            val playbackState = mediaController.playbackState?.state
            val isPaused = playbackState == PlaybackState.STATE_PAUSED ||
                playbackState == PlaybackState.STATE_STOPPED
            if (Prefs[Prefs.MEDIA_RPC_HIDE_ON_PAUSE, false] && isPaused) {
                continue
            }

            val metadata = mediaController.metadata
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            val appName = AppUtils.getAppName(pkg)
            val author =
                if (Prefs[Prefs.MEDIA_RPC_ARTIST_NAME, false])
                metadata?.let { metadataResolver.getArtistOrAuthor(it) }
                else null
            // Ungated — used for the {{media_album}} template placeholder even when the
            // legacy "Album name" toggle below (which only feeds the *default*, non-override
            // presence) is off. `album` (gated) is kept separately for that default.
            val albumRaw = metadata?.let { metadataResolver.getAlbum(it) }
            val album = if (Prefs[Prefs.MEDIA_RPC_ALBUM_NAME, false]) albumRaw else null
            val bitmap = metadata?.let { metadataResolver.getCoverArt(it) }
            val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)
            val position = mediaController.playbackState?.position
            duration?.let {
                if (it != 0L && playbackState == PlaybackState.STATE_PLAYING) timestamps = Timestamps(
                    end = System.currentTimeMillis() + duration - (position ?: 0L),
                    start = System.currentTimeMillis() - (position ?: 0L)
                )
            }
            if (title != null) {
                // Computed once, independent of the toggles below, so per-app overrides can
                // reference "the app icon" / "the cover art" / "the play/pause icon" even when
                // the corresponding legacy toggle (which only drives the *default* image
                // assignment) is off. Lazy: RpcImage.ApplicationIcon's constructor eagerly
                // reads+decodes the saved-app-icons Prefs map, which isn't free — only pay for
                // it when the app-icon toggle or an {{app_icon}} override actually needs it,
                // not on every presence update for every app.
                val appIconImage by lazy { RpcImage.ApplicationIcon(pkg, context) }
                val coverArtImage: RpcImage? = bitmap?.let {
                    RpcImage.BitmapImage(
                        context = context,
                        bitmap = it,
                        packageName = pkg,
                        // <Main artist>|<Album>|<Title>
                        title = "${metadata.let { m -> metadataResolver.getAlbumArtists(m) }}|${albumRaw ?: "unknown"}|${title}"
                    )
                }
                val playbackIconImage = getPlaybackStateIcon(playbackState ?: PlaybackState.STATE_PAUSED)

                largeIcon = if (Prefs[Prefs.MEDIA_RPC_APP_ICON, false]) appIconImage else null
                if (coverArtImage != null) {
                    smallIcon = largeIcon
                    smallText = appName
                    largeIcon = coverArtImage
                }

                if (Prefs[Prefs.MEDIA_RPC_SHOW_PLAYBACK_STATE, false]) {
                    smallIcon = playbackIconImage
                    smallText = null
                }

                val default = if (Prefs[Prefs.MEDIA_RPC_SHOW_SONG_AS_TITLE, false]) {
                    CommonRpc(
                        name = title,
                        details = author,
                        state = album,
                        largeImage = largeIcon,
                        smallImage = smallIcon,
                        largeText = appName,
                        smallText = smallText,
                        packageName = "$title::$pkg",
                        time = timestamps.takeIf { it != null }
                    )
                } else {
                    CommonRpc(
                        name = appName,
                        details = title,
                        state = author,
                        largeImage = largeIcon,
                        smallImage = smallIcon,
                        largeText = album,
                        smallText = smallText,
                        packageName = "$title::$pkg",
                        time = timestamps.takeIf { it != null }
                    )
                }

                val globalActivityType = Prefs[Prefs.MEDIA_RPC_ACTIVITY_TYPE, 0]
                val globalStatus = Prefs[Prefs.CUSTOM_ACTIVITY_STATUS, "dnd"]
                val globalButtons = if (Prefs[Prefs.USE_RPC_BUTTONS, false]) {
                    val rpcButtons = Json.decodeFromString<RpcButtons>(Prefs[Prefs.RPC_BUTTONS_DATA, "{}"])
                    buildList {
                        if (rpcButtons.button1.isNotEmpty() && rpcButtons.button1Url.isNotEmpty())
                            add(RpcButton(rpcButtons.button1, rpcButtons.button1Url))
                        if (rpcButtons.button2.isNotEmpty() && rpcButtons.button2Url.isNotEmpty())
                            add(RpcButton(rpcButtons.button2, rpcButtons.button2Url))
                    }.takeIf { it.isNotEmpty() }
                } else null

                // Always resolved through MediaRpcOverrides (same style as AppDetectionService
                // always calling AppRpcOverrides.resolveFull) — it already falls back to
                // `default` field-by-field when there's no override for pkg, so there's no
                // separate "no override" branch to keep in sync here.
                val templateProcessor = TemplateProcessor(
                    mediaMetadata = metadata,
                    mediaPlayerAppName = appName,
                    mediaPlayerPackageName = pkg,
                    album = albumRaw,
                    playbackStateText = getPlaybackStateText(playbackState ?: PlaybackState.STATE_PAUSED),
                    positionMs = position,
                    durationMs = duration,
                )
                val resolved = MediaRpcOverrides.resolveFull(
                    // Reuses the `override` already fetched at the top of this loop iteration
                    // (line ~92) instead of re-deriving it from pkg — resolveFull used to call
                    // MediaRpcOverrides.of(pkg) itself, decoding the whole overrides map from
                    // Prefs a second time on every single presence update.
                    override = override,
                    default = default,
                    templateProcessor = templateProcessor,
                    coverArt = coverArtImage,
                    appIcon = { appIconImage },
                    playbackIcon = playbackIconImage,
                    globalActivityType = globalActivityType,
                    globalStatus = globalStatus,
                    globalButtons = globalButtons,
                )

                return MediaPresence(
                    rpc = resolved,
                    enableTimestamps = override?.showTimestamps ?: Prefs[Prefs.MEDIA_RPC_ENABLE_TIMESTAMPS, false],
                )
            }
        }
        return MediaPresence(rpc = CommonRpc(), enableTimestamps = false)
    }
}
