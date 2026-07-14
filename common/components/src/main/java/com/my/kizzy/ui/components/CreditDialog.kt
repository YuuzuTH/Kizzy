/*
 * Copyright (C) 2026 Yuzu夕
 * Kizzy is free software licensed under GPL-3.0.
 * CreditDialog.kt — one-time credit/license/disclaimer dialog shown on first
 * launch, part of Kizzy by Yuzu夕.
 */

package com.my.kizzy.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.my.kizzy.resources.R

const val ORIGINAL_KIZZY_REPO_URL = "https://github.com/dead8309/Kizzy"
const val FORK_KIZZY_REPO_URL = "https://github.com/YuuzuTH/Kizzy"

/**
 * Shown once on first launch (and once more for existing users updating into the
 * version that introduced it) to make the GPL-3.0 credit/license terms and the
 * Discord-affiliation / user-token disclaimers visible up front. Dismissing it
 * (the only action available) is expected to persist [com.my.kizzy.preference.Prefs.CREDIT_DIALOG_SHOWN].
 */
@Composable
fun CreditDialog(
    modifier: Modifier = Modifier,
    onAcknowledge: () -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onAcknowledge,
        icon = {
            Icon(imageVector = Icons.Outlined.Info, contentDescription = null)
        },
        title = {
            Text(text = stringResource(R.string.credit_dialog_title))
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(text = stringResource(R.string.credit_dialog_message))
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { uriHandler.openUri(ORIGINAL_KIZZY_REPO_URL) }) {
                    Text(text = stringResource(R.string.credit_dialog_original_repo))
                }
                TextButton(onClick = { uriHandler.openUri(FORK_KIZZY_REPO_URL) }) {
                    Text(text = stringResource(R.string.credit_dialog_fork_repo))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAcknowledge) {
                Text(text = stringResource(R.string.acknowledge))
            }
        },
    )
}

@Preview
@Composable
fun CreditDialogPreview() {
    CreditDialog()
}
