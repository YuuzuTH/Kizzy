/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * CommonRpc.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.data.rpc

data class CommonRpc(
    val name: String = "",
    val type: Int? = null,
    val details: String? = "",
    val state: String? = "",
    val partyCurrentSize: Int? = null,
    val partyMaxSize: Int? = null,
    val largeImage: RpcImage? = null,
    val smallImage: RpcImage? = null,
    var largeText: String? = null,
    var smallText: String? = null,
    val time: Timestamps? = null,
    val packageName: String = "",
    val platform: String? = null,
    // Per-call presence buttons. When non-null they define the buttons for this update
    // (used by App Detection's per-app overrides); null preserves the previous behaviour
    // where buttons come from whatever was set on the KizzyRPC builder.
    val buttons: List<RpcButton>? = null,
    // Streaming URL — only meaningful with activity type 1 (Streaming). null = unchanged.
    val streamUrl: String? = null,
    // Per-call profile status ("online"/"idle"/"dnd"). null preserves the previous behaviour
    // where status comes from whatever was set on the KizzyRPC builder (Media/Experimental RPC).
    val status: String? = null,
)

/** A single Rich Presence button: a [label] and the [url] it opens. */
data class RpcButton(
    val label: String,
    val url: String,
)
