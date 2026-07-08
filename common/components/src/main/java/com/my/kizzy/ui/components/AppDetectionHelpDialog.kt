/*
 * Copyright (C) 2026 Yuzu夕
 * Kizzy is free software licensed under GPL-3.0.
 * AppDetectionHelpDialog.kt — in-app tutorial for App Detection, part of Kizzy by Yuzu夕.
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
 * A short, in-app explanation of what App Detection is and how its pieces fit together —
 * opened from the help icon on [com.my.kizzy.feature_apps_rpc.AppsRPC]'s top bar. Exists
 * because the per-app editor ([AppOverrideDialog]) packs a lot into five small tabs with no
 * room for prose; this is the "what does this actually do" reference for someone opening the
 * feature for the first time, without cluttering the editor itself with explanatory text.
 *
 * Plain static copy, no state to hoist — every section is just a title + body string pair.
 */
@Composable
fun AppDetectionHelpDialog(onDismissRequest: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null) },
        title = { Text(text = stringResource(R.string.app_detection_help_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                HelpSection(
                    title = stringResource(R.string.app_detection_help_intro_title),
                    body = stringResource(R.string.app_detection_help_intro_body),
                )
                HelpSection(
                    title = stringResource(R.string.app_detection_help_tabs_title),
                    body = stringResource(R.string.app_detection_help_tabs_body),
                )
                HelpSection(
                    title = stringResource(R.string.app_detection_help_shortcuts_title),
                    body = stringResource(R.string.app_detection_help_shortcuts_body),
                )
                HelpSection(
                    title = stringResource(R.string.app_detection_help_timer_title),
                    body = stringResource(R.string.app_detection_help_timer_body),
                    isLast = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.got_it)) }
        },
    )
}

// Reuses Subtitle's existing labelLarge/primary title styling (same one Settings-style
// screens use for section headers) instead of re-declaring it a third time — AppOverrideDialog.kt
// already has its own private Section() with the identical style tuned for the row of chips
// that follows it there; this dialog's title+prose layout doesn't need a chip-flavored variant.
@Composable
private fun HelpSection(title: String, body: String, isLast: Boolean = false) {
    Subtitle(modifier = Modifier, text = title)
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 2.dp, bottom = if (isLast) 0.dp else 12.dp),
    )
}
