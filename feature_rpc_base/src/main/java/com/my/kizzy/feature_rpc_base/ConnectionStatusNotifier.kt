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
 * It builds its **own** [Notification.Builder] rather than mutating the service's shared one:
 * the presence-update coroutines mutate that shared builder on a different scope, and
 * Notification.Builder is not thread-safe, so sharing it would race. Both post to the same
 * notification id — [NotificationManager.notify] is thread-safe, so the last write simply wins
 * and a normal presence update restores the full notification on the next change.
 *
 * Call once from a running RPC service with a [scope] that is NOT the one it churns with
 * `cancelChildren()` during presence updates (Media/Experimental do); cancel it in onDestroy.
 */
fun Service.observeConnectionStatus(
    scope: CoroutineScope,
    connectionState: RpcConnectionState,
    notificationManager: NotificationManager,
) {
    fun statusNotification(text: String): Notification =
        Notification.Builder(this, Constants.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_rpc_placeholder)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .build()

    var showingReconnect = false
    scope.launch {
        connectionState.state.collect { state ->
            when (state) {
                ConnectionState.RECONNECTING -> {
                    showingReconnect = true
                    notificationManager.notify(
                        Constants.NOTIFICATION_ID,
                        statusNotification(getString(R.string.reconnecting_to_discord))
                    )
                }

                ConnectionState.CONNECTED -> {
                    // Only overwrite when we were showing the reconnect notice; a normal
                    // presence notification is left untouched.
                    if (showingReconnect) {
                        showingReconnect = false
                        notificationManager.notify(
                            Constants.NOTIFICATION_ID,
                            statusNotification(getString(R.string.connected_to_discord))
                        )
                    }
                }

                ConnectionState.CONNECTING -> {}
            }
        }
    }
}
