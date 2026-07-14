/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * FakeGatewayServer.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *****************************************************************
 *
 *
 */

package kizzy.gateway.test

import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Fakes just enough of the real Discord gateway handshake (HELLO -> wait for IDENTIFY -> READY
 * after [readyDelayMs]) over a real local WebSocket to drive [kizzy.gateway.DiscordWebSocketImpl]
 * through its actual network stack in tests, instead of a hand-rolled substitute for the class
 * under test. Every PRESENCE_UPDATE (op 3) frame the client sends is recorded in order, which is
 * the thing these tests actually assert on.
 */
class FakeGatewayServer(private val readyDelayMs: Long = 300) {
    val receivedPresenceUpdates = CopyOnWriteArrayList<JsonElement>()
    private val json = Json { ignoreUnknownKeys = true }

    // Grabbed synchronously before starting the ktor server so the exact bound port is known
    // immediately, instead of querying the (suspend, ktor-version-sensitive) engine afterward.
    private val port = ServerSocket(0).use { it.localPort }

    private val server = embeddedServer(CIO, port = port) {
        install(WebSockets)
        routing {
            webSocket("/") {
                send(Frame.Text("""{"op":10,"d":{"heartbeat_interval":30000}}"""))
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    val text = frame.readText()
                    val op = runCatching { json.parseToJsonElement(text).jsonObject["op"]?.jsonPrimitive?.int }
                        .getOrNull() ?: continue
                    when (op) {
                        1 -> send(Frame.Text("""{"op":11}""")) // HEARTBEAT -> HEARTBEAT_ACK
                        2 -> launch { // IDENTIFY -> READY after readyDelayMs, simulating a slow/mid-reconnect handshake
                            delay(readyDelayMs)
                            send(
                                Frame.Text(
                                    """{"t":"READY","s":1,"op":0,"d":{"resume_gateway_url":"ws://127.0.0.1:$port","session_id":"fake-session"}}"""
                                )
                            )
                        }
                        3 -> receivedPresenceUpdates.add(json.parseToJsonElement(text).jsonObject["d"]!!) // PRESENCE_UPDATE
                    }
                }
            }
        }
    }.start(wait = false)

    fun url(): String = "ws://127.0.0.1:$port/?v=10&encoding=json"

    fun stop() = server.stop(0, 0)
}
