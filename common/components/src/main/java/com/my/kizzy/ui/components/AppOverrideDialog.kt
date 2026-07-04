/*
 * Copyright (C) 2026 Yuzu夕
 * Kizzy is free software licensed under GPL-3.0.
 * AppOverrideDialog.kt — per-app custom presence editor, part of Kizzy by Yuzu夕.
 */

package com.my.kizzy.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.my.kizzy.data.rpc.AppRpcOverride
import com.my.kizzy.resources.R

/**
 * Full per-app presence editor: custom name, details/state, large & small images with
 * tooltips, activity type, stream URL, two buttons and an elapsed-time toggle. Purely
 * presentational — the caller persists the returned [AppRpcOverride]. Clearing removes it.
 *
 * The activity types offered map to Discord's verbs; note "Streaming" (1) only renders when a
 * valid Twitch/YouTube [AppRpcOverride.streamUrl] is set, which is why that field sits next to it.
 */
@Composable
fun AppOverrideDialog(
    appName: String,
    initial: AppRpcOverride,
    onSave: (AppRpcOverride) -> Unit,
    onClear: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial.name.orEmpty()) }
    var imageUrl by rememberSaveable { mutableStateOf(initial.imageUrl.orEmpty()) }
    var details by rememberSaveable { mutableStateOf(initial.details.orEmpty()) }
    var state by rememberSaveable { mutableStateOf(initial.state.orEmpty()) }
    var largeText by rememberSaveable { mutableStateOf(initial.largeText.orEmpty()) }
    var smallImageUrl by rememberSaveable { mutableStateOf(initial.smallImageUrl.orEmpty()) }
    var smallText by rememberSaveable { mutableStateOf(initial.smallText.orEmpty()) }
    var streamUrl by rememberSaveable { mutableStateOf(initial.streamUrl.orEmpty()) }
    var button1Text by rememberSaveable { mutableStateOf(initial.button1Text.orEmpty()) }
    var button1Url by rememberSaveable { mutableStateOf(initial.button1Url.orEmpty()) }
    var button2Text by rememberSaveable { mutableStateOf(initial.button2Text.orEmpty()) }
    var button2Url by rememberSaveable { mutableStateOf(initial.button2Url.orEmpty()) }
    var activityType by rememberSaveable { mutableStateOf(initial.activityType ?: 0) }
    var showTimestamps by rememberSaveable { mutableStateOf(initial.showTimestamps ?: true) }

    val hasExisting = remember { !initial.isEmpty }

    fun collect() = AppRpcOverride(
        name = name.trim().ifBlank { null },
        imageUrl = imageUrl.trim().ifBlank { null },
        details = details.trim().ifBlank { null },
        state = state.trim().ifBlank { null },
        largeText = largeText.trim().ifBlank { null },
        smallImageUrl = smallImageUrl.trim().ifBlank { null },
        smallText = smallText.trim().ifBlank { null },
        activityType = activityType,
        streamUrl = streamUrl.trim().ifBlank { null },
        button1Text = button1Text.trim().ifBlank { null },
        button1Url = button1Url.trim().ifBlank { null },
        button2Text = button2Text.trim().ifBlank { null },
        button2Url = button2Url.trim().ifBlank { null },
        showTimestamps = showTimestamps,
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
        title = { Text(text = stringResource(R.string.app_override_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = appName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(12.dp))

                Section(stringResource(R.string.app_override_section_text))
                Field(name, { name = it }, R.string.app_override_name)
                Field(details, { details = it }, R.string.app_override_details)
                Field(state, { state = it }, R.string.app_override_state)

                Spacer(Modifier.height(8.dp))
                Section(stringResource(R.string.app_override_activity_type))
                ActivityTypeRow(selected = activityType, onSelect = { activityType = it })
                if (activityType == 1) {
                    Field(streamUrl, { streamUrl = it }, R.string.app_override_stream_url, KeyboardType.Uri)
                }

                Spacer(Modifier.height(8.dp))
                Section(stringResource(R.string.app_override_section_images))
                Field(imageUrl, { imageUrl = it }, R.string.app_override_image, KeyboardType.Uri)
                Field(largeText, { largeText = it }, R.string.app_override_large_text)
                Field(smallImageUrl, { smallImageUrl = it }, R.string.app_override_small_image, KeyboardType.Uri)
                Field(smallText, { smallText = it }, R.string.app_override_small_text)

                Spacer(Modifier.height(8.dp))
                Section(stringResource(R.string.app_override_section_buttons))
                Field(button1Text, { button1Text = it }, R.string.app_override_button1)
                Field(button1Url, { button1Url = it }, R.string.app_override_button1_url, KeyboardType.Uri)
                Field(button2Text, { button2Text = it }, R.string.app_override_button2)
                Field(button2Url, { button2Url = it }, R.string.app_override_button2_url, KeyboardType.Uri)
                Text(
                    text = stringResource(R.string.app_override_buttons_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.app_override_show_timestamps),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(checked = showTimestamps, onCheckedChange = { showTimestamps = it })
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.app_override_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(collect()) }) {
                Text(text = stringResource(R.string.save))
            }
        },
        dismissButton = {
            if (hasExisting) {
                TextButton(onClick = onClear) { Text(stringResource(R.string.app_override_clear)) }
            } else {
                TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun Section(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ActivityTypeRow(selected: Int, onSelect: (Int) -> Unit) {
    // (value, label) — value 4 is intentionally absent from Discord's activity types.
    val types = listOf(
        0 to R.string.activity_type_playing,
        1 to R.string.activity_type_streaming,
        2 to R.string.activity_type_listening,
        3 to R.string.activity_type_watching,
        5 to R.string.activity_type_competing,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        types.forEach { (value, labelRes) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(stringResource(labelRes)) },
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}
