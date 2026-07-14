/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * RpcServiceController.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_rpc_base

import android.app.Service
import android.content.Context
import android.content.Intent
import com.my.kizzy.preference.Prefs

/**
 * Tracks which RPC service the user currently has running so [com.my.kizzy.feature_rpc_base.services.BootReceiver]
 * can bring the same one back after a reboot — and only if the user did not stop it first.
 *
 * The record is written when a service goes foreground ([rememberActiveService]) and cleared
 * the moment the user stops one, either from the UI ([stopRpcService]) or the notification's
 * Stop action ([forgetActiveService]). A mode switch stops one service and immediately starts
 * another; the clear runs first and the freshly started service re-writes the record, so the
 * stored value is always the service that is actually running. A system low-memory kill goes
 * through neither path, so the record survives (START_STICKY restarts it; a reboot uses it).
 */

/** Remember [service] as the running one. Call right after it successfully goes foreground. */
fun rememberActiveService(service: Service) {
    Prefs[Prefs.LAST_ACTIVE_SERVICE] = service.javaClass.name
}

/** Forget the running service if it matches [serviceName]. For a service's own Stop action. */
fun forgetActiveService(serviceName: String) {
    if (Prefs[Prefs.LAST_ACTIVE_SERVICE, ""] == serviceName) {
        Prefs.remove(Prefs.LAST_ACTIVE_SERVICE)
    }
}

/**
 * Stop an RPC [service] and forget it as the boot-restart target if it was the tracked one.
 * Use in place of [Context.stopService] for the RPC services so a user-initiated stop is
 * never resurrected on the next boot.
 */
fun Context.stopRpcService(service: Class<out Service>) {
    forgetActiveService(service.name)
    stopService(Intent(this, service))
}
