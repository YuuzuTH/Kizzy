/*
 * Copyright (C) 2026 Yuzu夕
 * Kizzy is free software licensed under GPL-3.0.
 * AppOverrideDialog.kt — per-app custom presence editor, part of Kizzy by Yuzu夕.
 */

package com.my.kizzy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.my.kizzy.data.rpc.AppRpcOverride
import com.my.kizzy.data.utils.uriToFile
import com.my.kizzy.resources.R
import com.my.kizzy.ui.theme.DISCORD_BLURPLE
import java.io.File
import kotlinx.coroutines.launch

/**
 * Full per-app presence editor: custom name, details/state, large & small images with
 * tooltips, activity type, stream URL, two buttons, status and party size. Purely
 * presentational — the caller persists the returned [AppRpcOverride]. Clearing removes it.
 *
 * All fields are hoisted to this top level with [rememberSaveable] and the tab body below is
 * just a `when` over the current pager page — so switching tabs (by tap or swipe) never loses
 * an edit in progress, it only changes which fields are currently visible. Each tab owns its own
 * scroll state.
 *
 * The [TabRow] and the [HorizontalPager] are kept in sync two ways: tapping a tab animates the
 * pager to that page, and [snapshotFlow]-ing the pager's settled page back into `selectedTab`
 * keeps the tab indicator following a swipe. `selectedTab` is still the single source of truth
 * that survives rotation/process death (it seeds the pager's initial page), the pager itself
 * only drives which page is on-screen moment to moment.
 *
 * Each tab that carries fields (not the preview tab) shows a small dot when any of its fields
 * hold a non-default value, mirroring [AppRpcOverride.isEmpty]'s field grouping — this replaces
 * the at-a-glance "what's filled in" visibility the single long-scroll form used to give for free.
 *
 * The activity types offered map to Discord's verbs; note "Streaming" (1) only renders when a
 * valid Twitch/YouTube [AppRpcOverride.streamUrl] is set, which is why that field sits next to it.
 *
 * The large/small image fields also accept a picked-from-device image: [onUploadImage] does the
 * actual upload (caller owns the network/use-case), this dialog only supplies the file and writes
 * the returned asset id back into the field, same as [AppRpcOverride.imageUrl] would from a URL.
 *
 * A reset icon next to the app name (only shown when [initial] already had customization) clears
 * the whole override in one action; tapping it swaps the dialog body into a lightweight inline
 * confirmation instead of opening a second dialog on top of this one.
 */
@Composable
fun AppOverrideDialog(
    appName: String,
    initial: AppRpcOverride,
    onSave: (AppRpcOverride) -> Unit,
    onClear: () -> Unit,
    onDismissRequest: () -> Unit,
    onUploadImage: (file: File, onResult: (String) -> Unit) -> Unit = { _, _ -> },
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
    var status by rememberSaveable { mutableStateOf(initial.status) }
    var partyCurrent by rememberSaveable { mutableStateOf(initial.partyCurrentSize?.toString().orEmpty()) }
    var partyMax by rememberSaveable { mutableStateOf(initial.partyMaxSize?.toString().orEmpty()) }

    val hasExisting = remember { !initial.isEmpty }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    // Gates an inline "are you sure" swap of the dialog body (see AlertDialog's `text` below)
    // instead of stacking a second AlertDialog on top of this one for the reset action.
    var showResetConfirm by rememberSaveable { mutableStateOf(false) }

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
        status = status,
        partyCurrentSize = partyCurrent.toIntOrNull(),
        partyMaxSize = partyMax.toIntOrNull(),
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
        title = { Text(text = stringResource(R.string.app_override_title)) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f),
                    )
                    // Only offered when there was already something to reset. Disabled (rather
                    // than removed) while showResetConfirm is up so a second tap can't re-arm it.
                    if (hasExisting) {
                        IconButton(
                            onClick = { showResetConfirm = true },
                            enabled = !showResetConfirm,
                        ) {
                            Icon(
                                Icons.Outlined.RestartAlt,
                                contentDescription = stringResource(R.string.app_override_clear),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                if (showResetConfirm) {
                    Text(
                        text = stringResource(R.string.app_override_reset_confirm_message, appName),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    // Tab titles reuse the same section-title strings the fields used to be
                    // grouped under when this was one long scrolling form — same wording, just no
                    // longer stacked vertically. selectedTab lives above this composable
                    // (rememberSaveable) so rotation/process-death restores the tab the user was
                    // on; it also seeds the pager's initial page below.
                    val tabTitles = listOf(
                        R.string.app_override_section_text,
                        R.string.app_override_section_images,
                        R.string.app_override_section_buttons,
                        R.string.app_override_tab_status_party,
                        R.string.app_override_section_preview,
                    )
                    // Mirrors AppRpcOverride.isEmpty's field grouping, split per tab — true when
                    // that tab holds any non-default value, so its dot lights up. The preview tab
                    // is a computed view of the others, never gets one of its own.
                    val tabHasContent = listOf(
                        name.isNotBlank() || details.isNotBlank() || state.isNotBlank(),
                        imageUrl.isNotBlank() || largeText.isNotBlank() ||
                            smallImageUrl.isNotBlank() || smallText.isNotBlank(),
                        button1Text.isNotBlank() || button1Url.isNotBlank() ||
                            button2Text.isNotBlank() || button2Url.isNotBlank(),
                        activityType != 0 || streamUrl.isNotBlank() || status != null ||
                            partyCurrent.isNotBlank() || partyMax.isNotBlank(),
                        false,
                    )

                    val pagerState = rememberPagerState(initialPage = selectedTab) { tabTitles.size }
                    val coroutineScope = rememberCoroutineScope()
                    // Pager -> tab: whichever page the swipe settles on becomes the selected tab.
                    // Tab -> pager (below, in each Tab's onClick) is the other half of the sync.
                    LaunchedEffect(pagerState) {
                        snapshotFlow { pagerState.currentPage }.collect { page ->
                            selectedTab = page
                        }
                    }

                    TabRow(selectedTabIndex = selectedTab) {
                        tabTitles.forEachIndexed { index, titleRes ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = {
                                    selectedTab = index
                                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                },
                                text = {
                                    BadgedBox(badge = {
                                        if (tabHasContent[index]) {
                                            Badge(modifier = Modifier.size(6.dp))
                                        }
                                    }) {
                                        Text(stringResource(titleRes), maxLines = 1)
                                    }
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    // Fixed height so swiping between tabs of very different lengths (a handful
                    // of text fields vs. the single-row preview) doesn't jerk the dialog's own
                    // size around mid-gesture — each page scrolls internally instead.
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp),
                    ) { page ->
                        // Each page gets its own fresh scroll state — intentional: these are
                        // separate screens now, not one form, so there's no shared scroll position
                        // to preserve. Field *values* themselves are hoisted above and untouched
                        // by page switches.
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            when (page) {
                                0 -> {
                                    Field(name, { name = it }, R.string.app_override_name)
                                    Field(details, { details = it }, R.string.app_override_details, charLimited = true)
                                    Field(state, { state = it }, R.string.app_override_state, charLimited = true)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.app_override_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }

                                1 -> {
                                    ImageField(imageUrl, { imageUrl = it }, R.string.app_override_image, onUploadImage)
                                    Field(largeText, { largeText = it }, R.string.app_override_large_text, charLimited = true)
                                    ImageField(smallImageUrl, { smallImageUrl = it }, R.string.app_override_small_image, onUploadImage)
                                    Field(smallText, { smallText = it }, R.string.app_override_small_text, charLimited = true)
                                }

                                2 -> {
                                    Field(button1Text, { button1Text = it }, R.string.app_override_button1)
                                    Field(button1Url, { button1Url = it }, R.string.app_override_button1_url, KeyboardType.Uri)
                                    Field(button2Text, { button2Text = it }, R.string.app_override_button2)
                                    Field(button2Url, { button2Url = it }, R.string.app_override_button2_url, KeyboardType.Uri)
                                    Text(
                                        text = stringResource(R.string.app_override_buttons_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }

                                3 -> {
                                    Section(stringResource(R.string.app_override_activity_type))
                                    ActivityTypeRow(selected = activityType, onSelect = { activityType = it })
                                    if (activityType == 1) {
                                        Field(streamUrl, { streamUrl = it }, R.string.app_override_stream_url, KeyboardType.Uri)
                                    }

                                    Spacer(Modifier.height(8.dp))
                                    Section(stringResource(R.string.app_override_section_status))
                                    StatusRow(selected = status, onSelect = { status = it })

                                    Spacer(Modifier.height(8.dp))
                                    Section(stringResource(R.string.app_override_section_party))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = partyCurrent,
                                            // Capped so a long paste can't silently overflow Int
                                            // (toIntOrNull() would return null past ~10 digits and
                                            // drop the party with no visible warning).
                                            onValueChange = { partyCurrent = it.filter(Char::isDigit).take(9) },
                                            label = { Text(stringResource(R.string.app_override_party_current)) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f),
                                        )
                                        OutlinedTextField(
                                            value = partyMax,
                                            onValueChange = { partyMax = it.filter(Char::isDigit).take(9) },
                                            label = { Text(stringResource(R.string.app_override_party_max)) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }

                                else -> {
                                    PresenceCardPreview(
                                        name = name,
                                        defaultName = appName,
                                        details = details,
                                        state = state,
                                        imageUrl = imageUrl,
                                        smallImageUrl = smallImageUrl,
                                        partyCurrent = partyCurrent.toIntOrNull(),
                                        partyMax = partyMax.toIntOrNull(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (showResetConfirm) {
                TextButton(onClick = onClear) { Text(stringResource(R.string.app_override_clear)) }
            } else {
                TextButton(onClick = { onSave(collect()) }) { Text(text = stringResource(R.string.save)) }
            }
        },
        dismissButton = {
            if (showResetConfirm) {
                TextButton(onClick = { showResetConfirm = false }) { Text(stringResource(R.string.cancel)) }
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

// Discord truncates these fields at 128 chars server-side (KizzyRPC.sanitize()) — surface
// that limit here so it's not a silent surprise once the presence renders.
private const val RPC_FIELD_CHAR_LIMIT = 128

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    keyboardType: KeyboardType = KeyboardType.Text,
    charLimited: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
    if (charLimited && value.length > RPC_FIELD_CHAR_LIMIT) {
        Text(
            text = "${value.length}/$RPC_FIELD_CHAR_LIMIT — " +
                stringResource(R.string.app_override_char_limit_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Spacer(Modifier.height(8.dp))
}

/** Same as [Field], but with a gallery-icon trailing button that picks + uploads an image
 *  from the device and writes the returned asset id back into [value] — same field the user
 *  could otherwise paste a URL into. */
@Composable
private fun ImageField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    onUploadImage: (file: File, onResult: (String) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }
    var showProgress by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        trailingIcon = {
            IconButton(onClick = { showPicker = true }) {
                Icon(Icons.Default.Image, contentDescription = stringResource(R.string.upload_image))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    ImagePicker(
        visible = showPicker,
        onDismiss = { showPicker = false },
        showProgress = showProgress,
    ) { uri ->
        showProgress = true
        onUploadImage(context.uriToFile(uri)) { result ->
            showProgress = false
            showPicker = false
            onValueChange(result)
        }
    }
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

/** [selected] is null for "inherit the global default" — the other three map to Discord's
 *  real profile-status values (online/idle/dnd) the same way [AppRpcOverride.status] does. */
@Composable
private fun StatusRow(selected: String?, onSelect: (String?) -> Unit) {
    val options = listOf(
        null to R.string.app_override_status_default,
        "online" to R.string.app_override_status_online,
        "idle" to R.string.app_override_status_idle,
        "dnd" to R.string.app_override_status_dnd,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, labelRes) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(stringResource(labelRes)) },
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

/** An already-uploaded Discord asset (from [ImageField]'s picker) is stored as a bare
 *  `attachments/...`/`external/...` id, same convention [RpcImage] uses elsewhere — everything
 *  else is treated as a direct URL. Preview-only cosmetics; the real upload/proxy resolution
 *  happens server-side in [com.my.kizzy.data.rpc.RpcImage]. */
private fun previewImageModel(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    return if (trimmed.startsWith("attachments") || trimmed.startsWith("external"))
        "https://media.discordapp.net/$trimmed"
    else trimmed
}

/** Static mock of how this override will render on a Discord profile — same visual language
 *  as the real presence card ([com.my.kizzy.feature_profile.ui.component.ActivityRow]) but
 *  driven directly by the dialog's live field state instead of a network-fetched presence, so
 *  it updates on every keystroke without an elapsed-timer tick. */
@Composable
private fun PresenceCardPreview(
    name: String,
    defaultName: String,
    details: String,
    state: String,
    imageUrl: String,
    smallImageUrl: String,
    partyCurrent: Int?,
    partyMax: Int?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(DISCORD_BLURPLE),
            contentAlignment = Alignment.Center,
        ) {
            previewImageModel(imageUrl)?.let { model ->
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
            }
            previewImageModel(smallImageUrl)?.let { model ->
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .align(Alignment.BottomEnd),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        // Mirrors ActivityRow.kt's real rendering rule: party only ever shows appended to a
        // non-blank state line, and only when both sides are positive (0 of anything = no party).
        val partyLabel = if (state.isNotBlank() && partyCurrent != null && partyMax != null &&
            partyCurrent > 0 && partyMax > 0 && partyCurrent <= partyMax
        ) " " + stringResource(R.string.user_profile_party, partyCurrent, partyMax) else ""
        Column {
            Text(
                text = name.ifBlank { defaultName },
                style = MaterialTheme.typography.titleSmall,
            )
            if (details.isNotBlank()) {
                Text(text = details, style = MaterialTheme.typography.bodySmall)
            }
            if (state.isNotBlank()) {
                Text(text = state + partyLabel, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
