/*
 * Copyright (C) 2026 Yuzu夕
 * Kizzy is free software licensed under GPL-3.0.
 * MediaOverrideHelpDialog.kt — in-app tutorial for Media RPC's per-app templates, part of
 * Kizzy by Yuzu夕.
 */

package com.my.kizzy.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.my.kizzy.resources.R

/**
 * A short, in-app explanation of Media RPC's per-app override editor — opened from the help
 * icon next to [com.my.kizzy.feature_media_rpc.MediaRPC]'s top bar. Mirrors
 * [AppDetectionHelpDialog]'s role for App Detection, but the concept here needs its own copy:
 * a Media RPC override isn't a fixed name/image like App Detection's, it's a *template* —
 * placeholders like `{{media_title}}` get substituted with the actual song/video every time
 * the presence updates, since a static override wouldn't be useful for something that changes
 * every track.
 */
@Composable
fun MediaOverrideHelpDialog(onDismissRequest: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null) },
        title = { Text(text = stringResource(R.string.media_override_help_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                HelpSection(
                    title = stringResource(R.string.media_override_help_intro_title),
                    body = stringResource(R.string.media_override_help_intro_body),
                )
                HelpSection(
                    title = stringResource(R.string.media_override_help_placeholders_title),
                    body = stringResource(R.string.media_override_help_placeholders_body),
                )
                HelpSection(
                    title = stringResource(R.string.media_override_help_images_title),
                    body = stringResource(R.string.media_override_help_images_body),
                )
                HelpSection(
                    title = stringResource(R.string.media_override_help_search_button_title),
                    body = stringResource(R.string.media_override_help_search_button_body),
                    isLast = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.got_it)) }
        },
    )
}

@Composable
private fun HelpSection(title: String, body: String, isLast: Boolean = false) {
    Subtitle(modifier = Modifier, text = title)
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 2.dp, bottom = if (isLast) 0.dp else 12.dp),
    )
}
