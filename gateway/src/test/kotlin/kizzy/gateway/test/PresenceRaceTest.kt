/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * PresenceRaceTest.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *****************************************************************
 *
 *
 */

package kizzy.gateway.test

import com.my.kizzy.domain.interfaces.NoOpLogger
import kizzy.gateway.DiscordWebSocketImpl
import kizzy.gateway.entities.presence.Activity
import kizzy.gateway.entities.presence.Presence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

private fun JsonElement.trackName(): String =
    jsonObject["activities"]!!.jsonArray[0].jsonObject["name"]!!.jsonPrimitive.content

/**
 * Regression coverage for the concurrent-send race fixed in v6.13.2.007: [DiscordWebSocketImpl
 * .sendActivity] (fires on a track/app change) and its internal `resendLastPresence()` (fires on
 * every gateway READY/RESUMED) used to be two independent coroutines with nothing serializing
 * them. If a track change landed while the gateway was mid-reconnect, both raced to send a
 * PRESENCE_UPDATE and whichever happened to finish last won — regardless of which one was the
 * actually-current track. These tests drive the real [DiscordWebSocketImpl] over a real local
 * WebSocket (see [FakeGatewayServer]) instead of asserting against a re-implementation of the
 * fix, so they exercise the exact code that ships.
 */
class PresenceRaceTest {

    private fun presence(track: String) = Presence(
        activities = listOf(Activity(name = track)),
        afk = true,
        since = 0L,
        status = "online",
    )

    @Test
    fun `track change during reconnect - only the latest track ever reaches the wire`() = runBlocking {
        // readyDelayMs is deliberately longer than sendActivity's 250ms poll interval so both
        // calls below are guaranteed to still be waiting on the socket when READY arrives —
        // exactly the "track changed mid-reconnect" window the original bug depended on.
        val server = FakeGatewayServer(readyDelayMs = 600)
        val gateway = DiscordWebSocketImpl(token = "test-token", logger = NoOpLogger, gatewayUrl = server.url())
        try {
            gateway.connect()

            val callers = CoroutineScope(SupervisorJob())
            // CoroutineStart.UNDISPATCHED runs each call synchronously up to its first
            // suspension point (inside sendActivity's wait loop) the instant it's launched, so
            // "track1 then track2" here deterministically means track1's `lastPresence` write
            // happens-before track2's — the same ordering a real double-track-change would have.
            callers.launch(start = CoroutineStart.UNDISPATCHED) { gateway.sendActivity(presence("track1")) }
            callers.launch(start = CoroutineStart.UNDISPATCHED) { gateway.sendActivity(presence("track2")) }

            withTimeout(5.seconds) {
                while (server.receivedPresenceUpdates.isEmpty()) delay(50)
                // Give any (incorrect) second/stale send a chance to land before asserting.
                delay(500)
            }

            assertEquals(
                "exactly one PRESENCE_UPDATE should reach Discord, not a stale one racing the fresh one",
                1,
                server.receivedPresenceUpdates.size
            )
            assertEquals(
                "the one presence that reaches Discord must be the latest track, not a stale replay",
                "track2",
                server.receivedPresenceUpdates.first().trackName()
            )
        } finally {
            gateway.close()
            server.stop()
        }
    }

    @Test
    fun `already connected - each send still goes through in order (no regression)`() = runBlocking {
        val readyDelayMs = 50L
        val server = FakeGatewayServer(readyDelayMs = readyDelayMs)
        val gateway = DiscordWebSocketImpl(token = "test-token", logger = NoOpLogger, gatewayUrl = server.url())
        try {
            gateway.connect()
            // isWebSocketConnected() only reflects the raw socket, which opens well before the
            // HELLO/IDENTIFY/READY handshake finishes and `connected` flips true internally —
            // waiting on it here would still land inside sendActivity's reconnect-wait window,
            // i.e. accidentally re-run the race test above instead of the no-wait-loop case this
            // test means to cover. There's no public "fully READY" signal, so wait out the known
            // handshake delay plus a safety margin instead.
            delay(readyDelayMs + 500)
            // Once actually connected there's no wait loop for two sequential sends to race
            // inside, so both should land — this pins down that the fix didn't turn sendActivity
            // into a "last one only" no-op for the common, non-racing case.
            gateway.sendActivity(presence("track1"))
            gateway.sendActivity(presence("track2"))

            withTimeout(5.seconds) {
                while (server.receivedPresenceUpdates.size < 2) delay(50)
            }
            assertEquals(2, server.receivedPresenceUpdates.size)
            assertEquals("track1", server.receivedPresenceUpdates[0].trackName())
            assertEquals("track2", server.receivedPresenceUpdates[1].trackName())
        } finally {
            gateway.close()
            server.stop()
        }
    }
}
