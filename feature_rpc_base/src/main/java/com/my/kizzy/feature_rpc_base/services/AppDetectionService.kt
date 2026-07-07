/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * AppDetectionService.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

@file:Suppress("DEPRECATION")

package com.my.kizzy.feature_rpc_base.services

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.blankj.utilcode.util.AppUtils
import com.my.kizzy.data.get_current_data.app.ForegroundAppDetector
import com.my.kizzy.data.rpc.AppRpcOverride
import com.my.kizzy.data.rpc.AppRpcOverrides
import com.my.kizzy.data.rpc.CommonRpc
import com.my.kizzy.data.rpc.KizzyRPC
import com.my.kizzy.data.rpc.RpcButton
import com.my.kizzy.data.rpc.RpcConnectionState
import com.my.kizzy.data.rpc.RpcImage
import com.my.kizzy.data.rpc.Timestamps
import com.my.kizzy.domain.interfaces.Logger
import com.my.kizzy.domain.model.rpc.RpcButtons
import com.my.kizzy.feature_rpc_base.Constants
import com.my.kizzy.feature_rpc_base.forgetActiveService
import com.my.kizzy.feature_rpc_base.rememberActiveService
import com.my.kizzy.feature_rpc_base.setLargeIcon
import com.my.kizzy.preference.Prefs
import com.my.kizzy.resources.R
import dagger.hilt.android.AndroidEntryPoint
import com.my.kizzy.feature_rpc_base.observeConnectionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

@AndroidEntryPoint
class AppDetectionService : Service() {

    @Inject
    lateinit var kizzyRPC: KizzyRPC

    @Inject
    lateinit var scope: CoroutineScope

    @Inject
    lateinit var notificationBuilder: Notification.Builder

    @Inject
    lateinit var notificationManager: NotificationManager

    @Inject
    lateinit var foregroundAppDetector: ForegroundAppDetector

    @Inject
    lateinit var rpcConnectionState: RpcConnectionState

    @Inject
    lateinit var logger: Logger

    // Separate from `scope` so it survives the detection loop's lifecycle; cancelled in onDestroy.
    private val connectionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var pendingIntent: PendingIntent

    private lateinit var restartPendingIntent: PendingIntent

    private var runningPackage = ""
    // The override last pushed for [runningPackage]; lets the loop notice an in-place edit
    // (e.g. the user toggling the timer) and re-send without waiting for an app switch.
    private var lastAppliedOverride: AppRpcOverride? = null
    override fun onBind(intent: Intent): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == Constants.ACTION_STOP_SERVICE) {
            // User tapped Stop — don't auto-start this again on the next boot.
            forgetActiveService(javaClass.name)
            stopSelf()
        } else if (intent?.action == Constants.ACTION_RESTART_SERVICE) {
            stopSelf()
            startService(Intent(this, AppDetectionService::class.java))
        } else {
            handleAppDetection()
        }
        // START_STICKY so the system resurrects the service (with a null intent) after
        // killing it for memory — detection reads live state, so it rebuilds on its own.
        return START_STICKY
    }

    override fun onDestroy() {
        connectionScope.cancel()
        scope.cancel()
        kizzyRPC.closeRPC()
        super.onDestroy()
    }

    private fun handleAppDetection() {
        val enabledPackages = getEnabledPackages()

        val stopIntent = createStopIntent()
        pendingIntent = createPendingIntent(stopIntent)

        val restartIntent = createRestartIntent()
        restartPendingIntent = PendingIntent.getService(
            this,
            0, restartIntent, PendingIntent.FLAG_IMMUTABLE
        )
        // Adding action to notification builder here to avoid having multiple Exit buttons
        // https://github.com/dead8309/Kizzy/issues/197
        notificationBuilder
            .setSmallIcon(R.drawable.ic_apps)
            .addAction(R.drawable.ic_apps, getString(R.string.restart), restartPendingIntent)
            .addAction(R.drawable.ic_apps, getString(R.string.exit), pendingIntent)


        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                startForeground(Constants.NOTIFICATION_ID, createDefaultNotification())
            } else {
                startForeground(Constants.NOTIFICATION_ID, createDefaultNotification(), FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            }
        } catch (e: Exception) {
            // A background START_STICKY restart can be rejected on Android 12+
            // (ForegroundServiceStartNotAllowedException) — fail soft, don't crash.
            stopSelf()
            return
        }
        // Went foreground successfully — mark this as the service to bring back after a reboot.
        rememberActiveService(this)
        // Surface "reconnecting…" in the notification if the gateway drops mid-session.
        rpcConnectionState.reset()
        observeConnectionStatus(connectionScope, rpcConnectionState, notificationManager)

        val rpcButtons = getRpcButtons()
        // User-selected detection sensitivity: Fast 2s / Normal 5s / Battery 10s. Read once
        // per (re)start; changing it takes effect after the service is restarted.
        val pollInterval = Prefs[
            Prefs.APP_DETECTION_POLL_INTERVAL,
            Prefs.APP_DETECTION_POLL_DEFAULT
        ].toLong().coerceIn(1000L, 60000L)

        scope.launch {
            while (isActive) {
                try {
                    // If the presence died (e.g. socket dropped) while we still think an
                    // app is running, forget it so the next detected package rebuilds
                    // instead of being skipped as "unchanged" and staying frozen.
                    if (runningPackage.isNotEmpty() && !kizzyRPC.isRpcRunning()) {
                        runningPackage = ""
                        lastAppliedOverride = null
                    }

                    val packageName = foregroundAppDetector.getForegroundPackage()
                    if (packageName != null) {
                        handleValidPackage(packageName, enabledPackages, rpcButtons)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A transient failure (e.g. network error while uploading an app
                    // icon) must not kill the detection loop permanently
                    Log.e("AppDetectionService", "Detection cycle failed: ${e.message}")
                }
                delay(pollInterval)
            }
        }
    }

    private fun getEnabledPackages(): List<String> {
        val apps = Prefs[Prefs.ENABLED_APPS, "[]"]
        return Json.decodeFromString(apps)
    }

    private fun getRpcButtons(): RpcButtons {
        val rpcButtonsString = Prefs[Prefs.RPC_BUTTONS_DATA, "{}"]
        return Json.decodeFromString(rpcButtonsString)
    }

    private suspend fun handleValidPackage(
        packageName: String,
        enabledPackages: List<String>,
        rpcButtons: RpcButtons,
    ) {
        if (packageName in enabledPackages) {
            // Re-apply not only when the foreground app changes, but also when the user
            // edits the current app's override while it's still in the foreground — otherwise
            // a name/timer/button/image change wouldn't show until the next app switch.
            val override = AppRpcOverrides.of(packageName)
            if (packageName != runningPackage || override != lastAppliedOverride) {
                // Only meaningful when it's the *same* app re-applying an in-place edit —
                // an actual app switch has no "previous state" to diff the timer against.
                val previousOverride = lastAppliedOverride.takeIf { packageName == runningPackage }
                handleEnabledPackage(packageName, rpcButtons, previousOverride)
                runningPackage = packageName
                lastAppliedOverride = override
            }
        } else if (packageName != runningPackage) {
            handleDisabledPackage()
            runningPackage = ""
            lastAppliedOverride = null
        }
    }

    private suspend fun handleEnabledPackage(
        packageName: String,
        rpcButtons: RpcButtons,
        previousOverride: AppRpcOverride?,
    ) {
        // Build the icon once and reuse it for the RPC image and the notification —
        // its constructor reads Prefs[SAVED_IMAGES] and parses JSON, so constructing
        // it several times per switch repeats that work for nothing.
        val icon = RpcImage.ApplicationIcon(packageName, this@AppDetectionService)
        // Full per-app override: name, image, details/state, activity type, extra image,
        // buttons, streaming url and timestamp toggle all merged with the app's real
        // name/icon defaults. The notification keeps the real app icon; only the Discord
        // presence reflects the override.
        val rpc = AppRpcOverrides.resolveFull(
            packageName,
            defaultName = AppUtils.getAppName(packageName),
            fallbackImage = icon
        )

        // Per-app buttons win; otherwise fall back to the global buttons (when enabled).
        // Locals so nullability is smart-castable (the fields live in another module).
        val b1Text = rpc.button1Text; val b1Url = rpc.button1Url
        val b2Text = rpc.button2Text; val b2Url = rpc.button2Url
        val buttons: List<RpcButton>? = when {
            rpc.hasButtons -> buildList {
                if (!b1Text.isNullOrBlank() && !b1Url.isNullOrBlank())
                    add(RpcButton(b1Text, b1Url))
                if (!b2Text.isNullOrBlank() && !b2Url.isNullOrBlank())
                    add(RpcButton(b2Text, b2Url))
            }.takeIf { it.isNotEmpty() }

            Prefs[Prefs.USE_RPC_BUTTONS, false] -> buildList {
                if (rpcButtons.button1.isNotEmpty() && rpcButtons.button1Url.isNotEmpty())
                    add(RpcButton(rpcButtons.button1, rpcButtons.button1Url))
                if (rpcButtons.button2.isNotEmpty() && rpcButtons.button2Url.isNotEmpty())
                    add(RpcButton(rpcButtons.button2, rpcButtons.button2Url))
            }.takeIf { it.isNotEmpty() }

            else -> null
        }

        val startTime = System.currentTimeMillis()

        // Turning the timer OFF for the *same still-running* app needs a brand new gateway
        // session, not an in-place update — Discord's client keeps rendering the elapsed
        // counter it already anchored for the current session even once the payload stops
        // carrying `timestamps`. Only this direction needs the reconnect: turning it back ON
        // just starts a fresh counter from a normal update, nothing stale to clear. Ordinary
        // app switches (previousOverride null) keep updating in place exactly as before.
        val timerTurnedOff = previousOverride != null &&
            (previousOverride.showTimestamps ?: true) && !rpc.showTimestamps
        if (timerTurnedOff && kizzyRPC.isRpcRunning()) {
            // TEMP DIAGNOSTIC (Tier 1.1 timer bug, round 3) — confirm closeRPC() actually
            // flips isRpcRunning() to false before the branch check below runs. Remove
            // once confirmed via the in-app Logs screen.
            logger.d("AppDetectionService", "[DBG-TIMER] timerTurnedOff=true, closing RPC before rebuild")
            kizzyRPC.closeRPC()
            logger.d("AppDetectionService", "[DBG-TIMER] after closeRPC isRpcRunning=${kizzyRPC.isRpcRunning()}")
        }

        if (kizzyRPC.isRpcRunning()) {
            if (timerTurnedOff) {
                logger.d("AppDetectionService", "[DBG-TIMER] BUG: still isRpcRunning=true after closeRPC, taking updateRPC (in-place) branch instead of rebuild")
            }
            // A presence is already running for the previous app. Update it in place so
            // switching games immediately reflects the new one — otherwise the RPC stays
            // stuck on the app it first started with.
            kizzyRPC.updateRPC(
                CommonRpc(
                    name = rpc.name,
                    type = rpc.activityType,
                    details = rpc.details,
                    state = rpc.state,
                    largeImage = rpc.largeImage,
                    smallImage = rpc.smallImage,
                    largeText = rpc.largeText,
                    smallText = rpc.smallText,
                    time = Timestamps(start = startTime).takeIf { rpc.showTimestamps },
                    packageName = packageName,
                    // Always explicit (empty = no buttons) so switching to an app without
                    // buttons clears the previous app's instead of inheriting them.
                    buttons = buttons ?: emptyList(),
                    streamUrl = rpc.streamUrl,
                    partyCurrentSize = rpc.partyCurrentSize,
                    partyMaxSize = rpc.partyMaxSize,
                    status = rpc.status
                ),
                enableTimestamps = rpc.showTimestamps
            )
        } else {
            if (timerTurnedOff) {
                logger.d("AppDetectionService", "[DBG-TIMER] OK: isRpcRunning=false after closeRPC, taking build (full rebuild) branch")
            }
            kizzyRPC.apply {
                // Same instance is reused across app switches; clear leftover state (appended
                // buttons, previous app's timestamps/images/details) before building fresh.
                resetBuilder()
                setName(rpc.name)
                setType(rpc.activityType)
                setDetails(rpc.details)
                setState(rpc.state)
                if (rpc.showTimestamps) setStartTimestamps(startTime)
                setStatus(rpc.status)
                setPartySize(rpc.partyCurrentSize, rpc.partyMaxSize)
                setLargeImage(rpc.largeImage, rpc.largeText)
                setSmallImage(rpc.smallImage, rpc.smallText)
                setStreamUrl(rpc.streamUrl)
                buttons?.forEachIndexed { index, btn ->
                    // Builder keeps two parallel lists; add label + url together.
                    if (index == 0) {
                        setButton1(btn.label); setButton1URL(btn.url)
                    } else {
                        setButton2(btn.label); setButton2URL(btn.url)
                    }
                }
                build()
            }
        }
        notificationManager.notify(
            Constants.NOTIFICATION_ID, notificationBuilder
                .setContentText(packageName)
                .setLargeIcon(rpcImage = icon, context = this@AppDetectionService)
                .build()
        )
    }

    private fun handleDisabledPackage() {
        if (kizzyRPC.isRpcRunning()) {
            kizzyRPC.closeRPC()
        }
        notificationManager.notify(Constants.NOTIFICATION_ID, createDefaultNotification())
    }

    private fun createDefaultNotification(): Notification {
        return Notification.Builder(this, Constants.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_apps)
            .setContentTitle(getString(R.string.service_enabled))
            .addAction(R.drawable.ic_apps, getString(R.string.exit), pendingIntent)
            .addAction(R.drawable.ic_apps, getString(R.string.restart), restartPendingIntent)
            .build()
    }

    private fun createStopIntent(): Intent {
        val stopIntent = Intent(this, AppDetectionService::class.java)
        stopIntent.action = Constants.ACTION_STOP_SERVICE
        return stopIntent
    }

    private fun createRestartIntent(): Intent {
        val restartIntent = Intent(this, AppDetectionService::class.java)
        restartIntent.action = Constants.ACTION_RESTART_SERVICE
        return restartIntent
    }

    private fun createPendingIntent(stopIntent: Intent): PendingIntent {
        return PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)
    }
}
