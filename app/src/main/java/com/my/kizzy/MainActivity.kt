package com.my.kizzy

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import com.my.kizzy.preference.autoTranslateTargetTag
import com.my.kizzy.preference.getLanguageConfig
import com.my.kizzy.preference.translate.AutoTranslateEpoch
import com.my.kizzy.preference.translate.AutoTranslateResources
import com.my.kizzy.ui.theme.KizzyTheme
import com.my.kizzy.ui.theme.LocalDarkTheme
import com.my.kizzy.ui.theme.LocalDynamicColorSwitch
import com.my.kizzy.ui.theme.SettingsProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var usageAccessStatus: MutableState<Boolean>
    private lateinit var notificationListenerAccess: MutableState<Boolean>

    // Resolved once (not on every getResources() call — Compose calls it a lot) so we
    // don't rebuild the ML Kit translator client or re-read Prefs/the device locale
    // repeatedly. Overriding getResources() here (rather than attachBaseContext)
    // deliberately runs *after* AppCompat's own per-app-language Resources/Configuration
    // handling, so our wrapper is always the last one applied and can't get silently
    // discarded by it. Safe to resolve lazily on first call regardless of exact timing:
    // MMKV (which autoTranslateTargetTag() reads) is initialized in Application.onCreate,
    // which always runs before any Activity's getResources().
    private var translatedResourcesResolved = false
    private var translatedResources: AutoTranslateResources? = null

    override fun getResources(): Resources {
        if (!translatedResourcesResolved) {
            translatedResourcesResolved = true
            autoTranslateTargetTag()?.let { targetTag ->
                translatedResources = AutoTranslateResources(
                    super.getResources(),
                    targetTag,
                    onTranslated = AutoTranslateEpoch::bump
                )
            }
        }
        return translatedResources ?: super.getResources()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Otherwise every Activity recreation (rotation, etc.) leaks the previous
        // instance's loaded on-device translation model.
        translatedResources?.close()
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usageAccessStatus = mutableStateOf(this.hasUsageAccess())
        notificationListenerAccess = mutableStateOf(this.hasNotificationAccess())
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
            v.setPadding(0, 0, 0, 0)
            insets
        }
        runBlocking {
            if (Build.VERSION.SDK_INT < 33)
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(getLanguageConfig())
                )
        }
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            // Auto-translate (see getResources() above) resolves strings async in the
            // background and can't make Compose's stringResource() itself observable —
            // so once translations for the current screen settle (debounced in
            // AutoTranslateEpoch), we key the whole tree on the epoch to force a full
            // rebuild, which re-reads every stringResource() call against the
            // now-updated cache. Only fires for someone on an unsupported system
            // language (see autoTranslateTargetTag) and only once per that language's
            // one-time warm-up — everyone else never sees this key change.
            val translationEpoch = AutoTranslateEpoch.value.intValue
            key(translationEpoch) {
                SettingsProvider(windowSizeClass.widthSizeClass) {
                    KizzyTheme(
                        darkTheme = LocalDarkTheme.current.isDarkTheme(),
                        isHighContrastModeEnabled = LocalDarkTheme.current.isHighContrastModeEnabled,
                        isDynamicColorEnabled = LocalDynamicColorSwitch.current,
                    ) {
                        Kizzy(
                            usageAccessStatus = usageAccessStatus,
                            notificationListenerAccess = notificationListenerAccess,
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        notificationListenerAccess.value = hasNotificationAccess()
        usageAccessStatus.value = hasUsageAccess()
    }

    @Suppress("DEPRECATION")
    private fun Context.hasUsageAccess(): Boolean {
        return try {
            val packageManager: PackageManager = this.packageManager
            val applicationInfo = packageManager.getApplicationInfo(this.packageName, 0)
            val appOpsManager = this.getSystemService(APP_OPS_SERVICE) as AppOpsManager
            val mode = appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                applicationInfo.uid,
                applicationInfo.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun Context.hasNotificationAccess(): Boolean {
        val enabledNotificationListeners = Settings.Secure.getString(
            this.contentResolver, "enabled_notification_listeners"
        )
        return enabledNotificationListeners != null && enabledNotificationListeners.contains(this.packageName)
    }

    companion object {
        fun setLanguage(locale: String) {
            val localeListCompat =
                if (locale.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                else LocaleListCompat.forLanguageTags(locale)
            AppCompatDelegate.setApplicationLocales(localeListCompat)
        }
    }
}