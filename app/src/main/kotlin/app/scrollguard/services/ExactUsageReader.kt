/*
 * Copyright 2026 Scroll Guard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package app.scrollguard.services

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import kotlin.math.max
import kotlin.math.min

data class UsageSession(
    val packageName: String,
    val startMillis: Long,
    val endMillis: Long,
) {
    val durationMillis: Long get() = (endMillis - startMillis).coerceAtLeast(0L)
}

data class ExactUsageSnapshot(
    val durations: Map<String, Long>,
    val sessions: List<UsageSession>,
    val screenOnMillis: Long,
    val pickups: Int,
)

/** Reconstructs exact foreground sessions instead of using Android's overlapping aggregate buckets. */
class ExactUsageReader(context: Context) {
    private val manager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun read(startMillis: Long, endMillis: Long): ExactUsageSnapshot {
        if (endMillis <= startMillis) {
            return ExactUsageSnapshot(emptyMap(), emptyList(), 0L, 0)
        }

        // Look behind the requested range so an activity or screen already active at the boundary
        // is correctly clipped to startMillis.
        val lookbackStart = (startMillis - LOOKBACK_MILLIS).coerceAtLeast(0L)
        val events = manager.queryEvents(lookbackStart, endMillis)
        val event = UsageEvents.Event()
        val activeActivities = mutableMapOf<String, MutableSet<String>>()
        val activeSince = mutableMapOf<String, Long>()
        val sessions = mutableListOf<UsageSession>()
        var screenInteractive = false
        var screenInteractiveSince = 0L
        var screenOnMillis = 0L
        var pickups = 0

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val timestamp = event.timeStamp.coerceIn(lookbackStart, endMillis)
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    val packageName = event.packageName ?: continue
                    val activities = activeActivities.getOrPut(packageName) { mutableSetOf() }
                    if (activities.isEmpty()) activeSince[packageName] = timestamp
                    activities += event.className ?: PACKAGE_ACTIVITY_KEY
                }

                UsageEvents.Event.MOVE_TO_BACKGROUND,
                UsageEvents.Event.ACTIVITY_STOPPED,
                -> {
                    val packageName = event.packageName ?: continue
                    val activities = activeActivities[packageName] ?: continue
                    activities -= event.className ?: PACKAGE_ACTIVITY_KEY
                    if (activities.isEmpty()) {
                        val started = activeSince.remove(packageName) ?: timestamp
                        addClippedSession(sessions, packageName, started, timestamp, startMillis, endMillis)
                        activeActivities.remove(packageName)
                    }
                }

                UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    if (!screenInteractive) {
                        screenInteractive = true
                        screenInteractiveSince = timestamp
                    }
                    if (timestamp in startMillis until endMillis) pickups += 1
                }

                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    if (screenInteractive) {
                        screenOnMillis += clippedDuration(
                            screenInteractiveSince,
                            timestamp,
                            startMillis,
                            endMillis,
                        )
                        screenInteractive = false
                    }
                }
            }
        }

        activeSince.forEach { (packageName, started) ->
            addClippedSession(sessions, packageName, started, endMillis, startMillis, endMillis)
        }
        if (screenInteractive) {
            screenOnMillis += clippedDuration(
                screenInteractiveSince,
                endMillis,
                startMillis,
                endMillis,
            )
        }

        val durations = sessions
            .groupBy { it.packageName }
            .mapValues { (_, packageSessions) -> packageSessions.sumOf { it.durationMillis } }
        return ExactUsageSnapshot(durations, sessions, screenOnMillis, pickups)
    }

    private fun addClippedSession(
        sessions: MutableList<UsageSession>,
        packageName: String,
        sessionStart: Long,
        sessionEnd: Long,
        rangeStart: Long,
        rangeEnd: Long,
    ) {
        val clippedStart = max(sessionStart, rangeStart)
        val clippedEnd = min(sessionEnd, rangeEnd)
        if (clippedEnd > clippedStart) {
            sessions += UsageSession(packageName, clippedStart, clippedEnd)
        }
    }

    private fun clippedDuration(
        sessionStart: Long,
        sessionEnd: Long,
        rangeStart: Long,
        rangeEnd: Long,
    ): Long = (min(sessionEnd, rangeEnd) - max(sessionStart, rangeStart)).coerceAtLeast(0L)

    private companion object {
        const val LOOKBACK_MILLIS = 24L * 60L * 60L * 1000L
        const val PACKAGE_ACTIVITY_KEY = "__package_activity__"
    }
}
