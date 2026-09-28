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
        val foreground = ForegroundSessionTracker(startMillis, endMillis)
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
                    foreground.resume(packageName, event.className?.toString(), timestamp)
                }

                UsageEvents.Event.MOVE_TO_BACKGROUND,
                UsageEvents.Event.ACTIVITY_STOPPED,
                -> {
                    val packageName = event.packageName ?: continue
                    foreground.pause(packageName, event.className?.toString(), timestamp)
                }

                UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    foreground.screenOn(timestamp)
                    if (!screenInteractive) {
                        screenInteractive = true
                        screenInteractiveSince = timestamp
                    }
                    if (timestamp in startMillis until endMillis) pickups += 1
                }

                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    foreground.screenOff(timestamp)
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

        val sessions = foreground.finish()
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

    private fun clippedDuration(
        sessionStart: Long,
        sessionEnd: Long,
        rangeStart: Long,
        rangeEnd: Long,
    ): Long = (min(sessionEnd, rangeEnd) - max(sessionStart, rangeStart)).coerceAtLeast(0L)

    private companion object {
        const val LOOKBACK_MILLIS = 24L * 60L * 60L * 1000L
    }
}

/** Keeps one focused foreground app at a time, even when Android omits a pause event. */
internal class ForegroundSessionTracker(
    private val rangeStart: Long,
    private val rangeEnd: Long,
) {
    private val sessions = mutableListOf<UsageSession>()
    private var packageName: String? = null
    private var activityName: String? = null
    private var sessionStart: Long? = null
    private var screenIsOff = false

    fun resume(nextPackage: String, nextActivity: String?, timestamp: Long) {
        if (packageName != nextPackage) {
            closeSession(timestamp)
            packageName = nextPackage
            sessionStart = if (screenIsOff) null else timestamp
        } else if (sessionStart == null && !screenIsOff) {
            sessionStart = timestamp
        }
        activityName = nextActivity
    }

    fun pause(pausedPackage: String, pausedActivity: String?, timestamp: Long) {
        if (packageName != pausedPackage) return
        // The old activity may stop after a new activity in the same app has resumed.
        if (pausedActivity != null && activityName != null && pausedActivity != activityName) return
        closeSession(timestamp)
        packageName = null
        activityName = null
    }

    fun screenOff(timestamp: Long) {
        closeSession(timestamp)
        screenIsOff = true
    }

    fun screenOn(timestamp: Long) {
        screenIsOff = false
        if (packageName != null && sessionStart == null) sessionStart = timestamp
    }

    fun finish(): List<UsageSession> {
        closeSession(rangeEnd)
        return sessions.toList()
    }

    private fun closeSession(timestamp: Long) {
        val activePackage = packageName ?: return
        val start = sessionStart ?: return
        val clippedStart = max(start, rangeStart)
        val clippedEnd = min(timestamp, rangeEnd)
        if (clippedEnd > clippedStart) {
            sessions += UsageSession(activePackage, clippedStart, clippedEnd)
        }
        sessionStart = null
    }
}
