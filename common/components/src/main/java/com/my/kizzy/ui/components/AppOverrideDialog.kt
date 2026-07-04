/*
 * Copyright (C) 2026 Yuzu夕
 * Kizzy is free software licensed under GPL-3.0.
 * AppOverrideDialog.kt — per-app custom name/image editor, part of Kizzy by Yuzu夕.
 */

package com.my.kizzy.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.my.kizzy.resources.R

/**
 * Lets the user override how one app appears in the Rich Presence: a custom
 * display name and/or a custom image URL. Purely presentational — the caller
 * persists the result. An empty save clears the override.
 */
@Composable
fun AppOverrideDialog(
    appName: String,
    initialName: String,
    initialImageUrl: String,
    onSave: (name: String, imageUrl: String) -> Unit,
    onClear: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var imageUrl by rememberSaveable { mutableStateOf(initialImageUrl) }
    val hasExisting = remember { initialName.isNotBlank() || initialImageUrl.isNotBlank() }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
        title = { Text(text = stringResource(R.string.app_override_title)) },
        text = {
            Column {
                Text(
                    text = appName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.app_override_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = imageUrl,
                    onValueChange = { imageUrl = it },
                    label = { Text(stringResource(R.string.app_override_image)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.app_override_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), imageUrl.trim()) }) {
                Text(text = stringResource(R.string.save))
            }
        },
        dismissButton = {
            if (hasExisting) {
                TextButton(onClick = onClear) {
                    Text(text = stringResource(R.string.app_override_clear))
                }
            } else {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        },
    )
}
