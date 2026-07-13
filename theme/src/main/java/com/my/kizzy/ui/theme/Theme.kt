/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * Theme.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.kyant.monet.dynamicColorScheme

private tailrec fun Context.findWindow(): Window? =
    when (this) {
        is Activity -> window
        is ContextWrapper -> baseContext.findWindow()
        else -> null
    }

// Dynamic color (wallpaper-derived, Android 12+) is retired as of the Yuzu夕
// twilight redesign — always resolve from the app's own seed color via Monet
// instead. This is enforced here, not just by hiding the toggle in Appearance,
// so a leftover `isDynamicColorEnabled = true` from an old install (no longer
// reachable from any UI) can never again override the brand color.
@Composable
fun getColorScheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isHighContrastModeEnabled: Boolean = false,
): ColorScheme {
    val colorScheme = dynamicColorScheme(!darkTheme).run {
        if (isHighContrastModeEnabled && darkTheme) copy(
            surface = Color.Black,
            background = Color.Black,
        )
        else this
    }
    return colorScheme
}
@Composable
fun KizzyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isHighContrastModeEnabled: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = getColorScheme(
        darkTheme,
        isHighContrastModeEnabled,
    )
    val window = LocalView.current.context.findWindow()
    val view = LocalView.current

    window?.let {
        WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = darkTheme
    }

    rememberSystemUiController(window).setSystemBarsColor(Color.Transparent, !darkTheme)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}