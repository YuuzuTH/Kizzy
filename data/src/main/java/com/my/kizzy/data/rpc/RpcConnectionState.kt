/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * RpcConnectionState.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.data.rpc

import kizzy.gateway.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide holder for the gateway connection status. The gateway callback pushes updates here
 * (via the DI-provided socket) and the running RPC service observes [state] to reflect a
 * "reconnecting…" status in its notification. A singleton so the pure-Kotlin gateway and the
 * Android service stay decoupled — neither references the other.
 */
@Singleton
class RpcConnectionState @Inject constructor() {
    private val _state = MutableStateFlow(ConnectionState.CONNECTING)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    fun update(value: ConnectionState) {
        _state.value = value
    }

    /**
     * Reset to a neutral state when a fresh service starts, so a previous session's
     * RECONNECTING value doesn't linger on the new service's notification before its own
     * socket reports in.
     */
    fun reset() {
        _state.value = ConnectionState.CONNECTING
    }
}
