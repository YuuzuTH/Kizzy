package com.my.kizzy.feature_settings.style

/**
 * source: https://github.com/JunkFood02/Seal
 */

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.my.kizzy.preference.DarkThemePreference.Companion.FOLLOW_SYSTEM
import com.my.kizzy.preference.DarkThemePreference.Companion.OFF
import com.my.kizzy.preference.DarkThemePreference.Companion.ON
import com.my.kizzy.preference.modifyDarkThemePreference
import com.my.kizzy.resources.R
import com.my.kizzy.ui.components.BackButton
import com.my.kizzy.ui.components.Subtitle
import com.my.kizzy.ui.components.preference.PreferenceSwitch
import com.my.kizzy.ui.theme.LocalDarkTheme

// Simplified 2026-07-13: dropped the generic Material-You color/style picker
// (8 unrelated preset hues, "dynamic color from wallpaper", freeform hex entry)
// in favor of a short, fully-branded list — light/dark/system, the one Yuzu夕
// twilight color, and a locked "seasonal themes" placeholder for later. Dynamic
// color is retired at the theme-resolution level too (see ui/theme/Theme.kt),
// not just hidden here, so a stale enabled flag from an old install can't
// silently override the brand color with no way to turn it back off.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Appearance(onBackPressed: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState(),
        canScroll = { true }
    )
    val darkThemePreference = LocalDarkTheme.current
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(text = stringResource(id = R.string.display)) },
                navigationIcon = { BackButton { onBackPressed() } },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth()
        ) {
            ThemeModeCard(
                title = stringResource(R.string.theme_light),
                icon = Icons.Outlined.LightMode,
                selected = darkThemePreference.darkThemeValue == OFF
            ) { modifyDarkThemePreference(darkThemeValue = OFF) }
            ThemeModeCard(
                title = stringResource(R.string.theme_dark),
                icon = Icons.Outlined.DarkMode,
                selected = darkThemePreference.darkThemeValue == ON
            ) { modifyDarkThemePreference(darkThemeValue = ON) }
            ThemeModeCard(
                title = stringResource(R.string.follow_system),
                icon = Icons.Outlined.BrightnessAuto,
                selected = darkThemePreference.darkThemeValue == FOLLOW_SYSTEM
            ) { modifyDarkThemePreference(darkThemeValue = FOLLOW_SYSTEM) }

            Subtitle(text = stringResource(R.string.advance_settings))
            PreferenceSwitch(
                title = stringResource(R.string.amoled),
                icon = Icons.Outlined.Contrast,
                isChecked = darkThemePreference.isHighContrastModeEnabled,
                onClick = {
                    modifyDarkThemePreference(
                        isHighContrastModeEnabled = !darkThemePreference.isHighContrastModeEnabled
                    )
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            BrandThemeCard(
                title = stringResource(R.string.theme_brand_title),
                description = stringResource(R.string.theme_brand_desc)
            )
            SeasonalThemeCard(
                title = stringResource(R.string.theme_seasonal_title),
                description = stringResource(R.string.theme_seasonal_desc)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ThemeModeCard(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() },
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun BrandThemeCard(title: String, description: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(20.dp)),
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "夕",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(end = 16.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun SeasonalThemeCard(title: String, description: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(20.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Celebration,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
            Surface(
                shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Text(
                    text = stringResource(R.string.coming_soon),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Preview
@Composable
private fun AppearancePreview() {
    Appearance(onBackPressed = {})
}
