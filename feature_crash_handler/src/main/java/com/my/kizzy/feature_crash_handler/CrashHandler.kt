/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * CrashHandler.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_crash_handler

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.blankj.utilcode.util.AppUtils
import com.blankj.utilcode.util.DeviceUtils
import com.developer.crashx.CrashActivity
import com.my.kizzy.data.remote.LogWebhookReporter
import com.my.kizzy.ui.theme.KizzyTheme
import com.my.kizzy.ui.theme.LocalDarkTheme

class CrashHandler : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = CrashActivity.getStackTraceFromIntent(intent)
        val crashLog = report.buildCrashLog()
        LogWebhookReporter.report("crash", crashLog)
        setContent {
            KizzyTheme(
                darkTheme = LocalDarkTheme.current.isDarkTheme(),
                isHighContrastModeEnabled = LocalDarkTheme.current.isHighContrastModeEnabled,
            ){
                CrashScreen(trace = crashLog)
            }
        }
    }
    private fun String?.buildCrashLog(): String {
        return """Kizzy crash report
Manufacturer: ${DeviceUtils.getManufacturer()}
Device: ${DeviceUtils.getModel()}
Android version: ${DeviceUtils.getSDKVersionName()}
App version: ${AppUtils.getAppVersionName()} (${AppUtils.getAppVersionCode()})
Stacktrace: 
$this"""
    }
}