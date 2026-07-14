/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * BootReceiver.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_rpc_base.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.my.kizzy.preference.Prefs

/**
 * Restarts the RPC service the user had running when the device rebooted, so the presence
 * comes back on its own instead of the user having to reopen the app and toggle it again.
 *
 * It is deliberately conservative — it only starts a service when *both* hold:
 *  1. a Discord token is still stored, and
 *  2. [Prefs.LAST_ACTIVE_SERVICE] names a service (i.e. the user had one running and did not
 *     turn it off — that record is cleared on every user-initiated stop).
 *
 * Anything unexpected → do nothing. This can never revive a service the user had switched off,
 * and any failure to start (e.g. a background-start restriction) fails soft without crashing.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) return

        // Fail-safe #1: no account, nothing to connect with.
        if (Prefs[Prefs.TOKEN, ""].isEmpty()) return

        // Fail-safe #2: only restart a service the user actually left running.
        val serviceName = Prefs[Prefs.LAST_ACTIVE_SERVICE, ""]
        if (serviceName.isEmpty()) return

        // Explicit name → class mapping (no reflection): unknown/renamed → do nothing.
        val serviceClass = when (serviceName) {
            AppDetectionService::class.java.name -> AppDetectionService::class.java
            MediaRpcService::class.java.name -> MediaRpcService::class.java
            CustomRpcService::class.java.name -> CustomRpcService::class.java
            ExperimentalRpc::class.java.name -> ExperimentalRpc::class.java
            else -> return
        }

        try {
            ContextCompat.startForegroundService(context, Intent(context, serviceClass))
        } catch (e: Exception) {
            // Some OEMs/Android versions restrict starting a foreground service from the
            // boot broadcast. Fail soft — the user can still start it from the app.
            Log.e("BootReceiver", "Boot auto-start failed for $serviceName: ${e.message}")
        }
    }
}
