/*
 * Copyright (C) 2026 Yuzu夕
 * Kizzy is free software licensed under GPL-3.0.
 * ForegroundAppDetector.kt — part of Kizzy by Yuzu夕.
 *
 * Single source of truth for "which app is on screen right now", shared by
 * App Detection and Experimental RPC. Uses UsageEvents (queryEvents) as the
 * primary signal — it reports the exact moment an app comes to the foreground,
 * so switching games is picked up immediately and accurately. Falls back to the
 * older queryUsageStats aggregation when no foreground event happened inside the
 * short window (e.g. the user has been idle in the same app), which keeps the
 * presence stable instead of dropping.
 */

package com.my.kizzy.data.get_current_data.app

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ForegroundAppDetector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Package name of the most recent foreground app, or null when it cannot be
     * determined or is one of the excluded apps (Kizzy / Discord themselves).
     */
    fun getForegroundPackage(): String? {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return null
        val now = System.currentTimeMillis()

        val pkg = latestForegroundFromEvents(usageStatsManager, now - EVENT_WINDOW_MS, now)
            ?: latestFromUsageStats(usageStatsManager, now - STATS_WINDOW_MS, now)

        return pkg?.takeIf { it.isNotEmpty() && it !in EXCLUDED_APPS }
    }

    /** Last app that moved to the foreground within [begin]..[end], or null. */
    private fun latestForegroundFromEvents(
        usageStatsManager: UsageStatsManager,
        begin: Long,
        end: Long,
    ): String? {
        val events = usageStatsManager.queryEvents(begin, end)
        val event = UsageEvents.Event()
        var latestPackage: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            @Suppress("DEPRECATION")
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                latestPackage = event.packageName
            }
        }
        return latestPackage
    }

    /** Fallback: app with the most recent lastTimeUsed in the aggregated stats. */
    private fun latestFromUsageStats(
        usageStatsManager: UsageStatsManager,
        begin: Long,
        end: Long,
    ): String? {
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, begin, end
        ) ?: return null
        return stats.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    private companion object {
        // Short window: matches the ~2s detection poll; large enough to catch the
        // foreground event of a fresh app switch, small enough to stay current.
        const val EVENT_WINDOW_MS = 15_000L
        const val STATS_WINDOW_MS = 10_000L
        val EXCLUDED_APPS = listOf("com.my.kizzy", "com.discord")
    }
}
