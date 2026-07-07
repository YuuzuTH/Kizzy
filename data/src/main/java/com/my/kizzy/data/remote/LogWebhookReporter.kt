/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * LogWebhookReporter.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.data.remote

import com.blankj.utilcode.util.AppUtils
import com.blankj.utilcode.util.DeviceUtils
import com.my.kizzy.data.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Sends app logs to a Discord webhook so bugs can be diagnosed without asking
 * the user to screenshot the in-app Logs screen. The webhook URL doubles as
 * the auth secret (POST-only, unguessable) and is injected at build time via
 * [BuildConfig.LOG_WEBHOOK_URL] from a CI secret — never committed to source.
 * Two callers: automatic on crash ([kind] = "crash"), manual button in the
 * Logs screen ([kind] = "manual").
 */
object LogWebhookReporter {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Lazy + shared across calls: first access happens inside the try in
    // report() below, so a failure creating the engine is caught like any
    // other send failure instead of crashing at object-init time.
    private val client by lazy { HttpClient(CIO) }

    // Discord bot/user token shape: three base64url-ish segments separated by dots.
    private val tokenRegex =
        Regex("""\b[A-Za-z0-9_-]{24,28}\.[A-Za-z0-9_-]{6}\.[A-Za-z0-9_-]{27,40}\b""")

    // "session_id": "xxxx" or session_id=xxxx appearing in raw gateway logs.
    private val sessionRegex =
        Regex("""(?i)("?session_id"?\s*[:=]\s*"?)([A-Za-z0-9._-]+)("?)""")

    private fun sanitize(text: String): String {
        val noTokens = tokenRegex.replace(text, "[REDACTED_TOKEN]")
        return sessionRegex.replace(noTokens) { m ->
            "${m.groupValues[1]}[REDACTED_SESSION]${m.groupValues[3]}"
        }
    }

    private fun String.jsonEscape(): String = buildString {
        for (c in this@jsonEscape) {
            when {
                c == '\\' -> append("\\\\")
                c == '"' -> append("\\\"")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c.code < 0x20 -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
    }

    private fun deviceHeader(kind: String): String {
        return "🐛 Kizzy $kind — ${DeviceUtils.getManufacturer()} ${DeviceUtils.getModel()}, " +
            "Android ${DeviceUtils.getSDKVersionName()}, app ${AppUtils.getAppVersionName()}" +
            " (${AppUtils.getAppVersionCode()})"
    }

    /** Fire-and-forget: never throws, no-ops if no webhook is configured for this build. */
    fun report(kind: String, text: String) {
        val url = BuildConfig.LOG_WEBHOOK_URL
        if (url.isBlank()) return
        val header = deviceHeader(kind)
        val body = sanitize(text)
        scope.launch {
            try {
                client.post(url) {
                    setBody(
                        MultiPartFormDataContent(
                            formData {
                                append("payload_json", "{\"content\":\"${header.jsonEscape()}\"}")
                                append(
                                    "files[0]",
                                    body.toByteArray(Charsets.UTF_8),
                                    Headers.build {
                                        append(HttpHeaders.ContentType, "text/plain")
                                        append(
                                            HttpHeaders.ContentDisposition,
                                            "filename=\"kizzy_log.txt\""
                                        )
                                    }
                                )
                            }
                        )
                    )
                }
            } catch (_: Exception) {
                // Best-effort only — a failed log upload (or even failing to
                // construct the HTTP client above) must never crash the app
                // or the crash screen itself.
            }
        }
    }
}
