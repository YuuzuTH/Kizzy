/*
 * Copyright (C) 2026 Yuzu夕
 * Kizzy is free software licensed under GPL-3.0.
 * AppUpdaterImpl.kt — in-app self-updater, part of Kizzy by Yuzu夕.
 */

package com.my.kizzy.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.my.kizzy.domain.model.update.UpdateDownloadState
import com.my.kizzy.domain.repository.AppUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdaterImpl @Inject constructor(
    private val client: HttpClient,
    @ApplicationContext private val context: Context,
) : AppUpdater {

    override fun downloadAndInstall(
        downloadUrl: String,
        versionName: String,
    ): Flow<UpdateDownloadState> = channelFlow {
        try {
            trySend(UpdateDownloadState.Downloading(0f))

            // Fresh, self-contained folder that provider_paths exposes as a content:// uri.
            val dir = File(context.cacheDir, "updates").apply {
                if (!exists()) mkdirs() else listFiles()?.forEach { it.delete() }
            }
            val apk = File(dir, "kizzy-${versionName.ifBlank { "update" }}.apk")

            // The shared HttpClient has a 30s request timeout — disable it for the
            // (potentially tens of MB) APK stream, otherwise the download is aborted.
            client.prepareGet(downloadUrl) {
                timeout { requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS }
            }.execute { response ->
                val total = response.contentLength() ?: 0L
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(DEFAULT_BUFFER_BYTES)
                var received = 0L
                apk.outputStream().use { out ->
                    while (true) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read == -1) break
                        if (read > 0) {
                            out.write(buffer, 0, read)
                            received += read
                            if (total > 0L) {
                                trySend(
                                    UpdateDownloadState.Downloading(
                                        (received.toFloat() / total).coerceIn(0f, 1f)
                                    )
                                )
                            }
                        }
                    }
                    out.flush()
                }
            }

            // Android O+: installing an APK needs the user to allow "install unknown
            // apps" for Kizzy. Send them there once; they retry Update after granting.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                openUnknownSourcesSettings()
                trySend(UpdateDownloadState.PermissionRequired)
                return@channelFlow
            }

            trySend(UpdateDownloadState.Installing)
            installApk(apk)
        } catch (e: Exception) {
            trySend(UpdateDownloadState.Failed(e.message ?: "Download failed"))
        }
    }.flowOn(Dispatchers.IO)

    private fun installApk(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun openUnknownSourcesSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
    }

    private companion object {
        const val DEFAULT_BUFFER_BYTES = 8 * 1024
    }
}
