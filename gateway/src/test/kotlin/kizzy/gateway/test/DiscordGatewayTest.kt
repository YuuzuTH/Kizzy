/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * DiscordGatewayTest.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package kizzy.gateway.test

import com.my.kizzy.domain.interfaces.Logger
import kizzy.gateway.DiscordWebSocketImpl
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeNotNull
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

// Hits the real Discord gateway with a real account token — manual/local-only sanity check
// (never wired into CI, which has no token to give it). Skips instead of failing with a
// confusing NPE when DISCORD_TOKEN isn't set, which is the normal case everywhere except a
// developer's own machine while deliberately running this one test.
class DiscordGatewayTest {
    @Test
    fun `Check Gateway Connection`() = runBlocking {
        val token = System.getenv("DISCORD_TOKEN")
        assumeNotNull(token)
        val logger = object: Logger  {
            override fun clear() {

            }
            override fun i(tag: String, event: String) {
                println("[$tag]\t\t\t: $event")
            }
            override fun e(tag: String, event: String) {
                println("[$tag]\t\t\t: $event")
            }
            override fun d(tag: String, event: String) {
                println("[$tag]\t\t\t: $event")
            }
            override fun w(tag: String, event: String) {
                println("[$tag]\t\t\t: $event")
            }

        }
        val gateway = DiscordWebSocketImpl(token!!, logger)
        gateway.connect()
        delay(2.seconds)
        assert(gateway.isWebSocketConnected())
    }
}