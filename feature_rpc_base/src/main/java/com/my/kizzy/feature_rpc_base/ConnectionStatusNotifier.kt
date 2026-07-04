/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * ConnectionStatusNotifier.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_rpc_base

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import com.my.kizzy.data.rpc.RpcConnectionState
import com.my.kizzy.resources.R
import kizzy.gateway.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Reflect the gateway [connectionState] in the running service's foreground notification so a
 * silent reconnect reads as "Reconnecting to Discord…" instead of the presence appearing frozen.
 *
 * Call once from a running RPC service. Pass a [scope] that is NOT the one the service churns
 * with `cancelChildren()` during presence updates (Media/Experimental do), and cancel it in
 * onDestroy. The notice only overwrites the notification text on a real reconnect; a normal
 * presence update restores the real content on the next change.
 */
fun Service.observeConnectionStatus(
    scope: CoroutineScope,
    connectionState: RpcConnectionState,
    notificationManager: NotificationManager,
    notificationBuilder: Notification.Builder,
) {
    var showingReconnect = false
    scope.launch {
        connectionState.state.collect { state ->
            when (state) {
                ConnectionState.RECONNECTING -> {
                    showingReconnect = true
                    notificationManager.notify(
                        Constants.NOTIFICATION_ID,
                        notificationBuilder
                            .setContentTitle(getString(R.string.app_name))
                            .setContentText(getString(R.string.reconnecting_to_discord))
                            .build()
                    )
                }

                ConnectionState.CONNECTED -> {
                    // Only clear the notice we posted; leave a normal notification untouched.
                    if (showingReconnect) {
                        showingReconnect = false
                        notificationManager.notify(
                            Constants.NOTIFICATION_ID,
                            notificationBuilder
                                .setContentText(getString(R.string.connected_to_discord))
                                .build()
                        )
                    }
                }

                ConnectionState.CONNECTING -> {}
            }
        }
    }
}
