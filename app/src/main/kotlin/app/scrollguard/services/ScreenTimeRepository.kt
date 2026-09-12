/*
 * Copyright 2026 Scroll Guard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package app.scrollguard.services

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import app.scrollguard.models.AppUsage
import app.scrollguard.models.ScreenTimeReport
import app.scrollguard.models.UsageBucket
import app.scrollguard.models.UsagePeriod
import app.scrollguard.utils.ScreenTimeFormatting
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Exact, local screen-time reporting backed by Android events and a private daily archive. */
class ScreenTimeRepository(private val context: Context) {
    private val reader = ExactUsageReader(context)
    private val archive = ScreenTimeArchive(context)
    private val packageManager = context.packageManager

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }

    fun load(period: UsagePeriod, nowMillis: Long = System.currentTimeMillis()): ScreenTimeReport {
        val todayStart = startOfDay(nowMillis)
        val trackingStart = minOf(
            startOfDay(firstInstallTime()),
            addDays(todayStart, -60),
        )
        if (period != UsagePeriod.DAY) archiveCompletedDays()

        val currentStart = when (period) {
            UsagePeriod.DAY -> todayStart
            UsagePeriod.WEEK -> addDays(todayStart, -6)
            UsagePeriod.MONTH -> addDays(todayStart, -29)
            UsagePeriod.ALL -> trackingStart
        }
        val current = if (period == UsagePeriod.DAY) {
            filtered(reader.read(currentStart, nowMillis))
        } else {
            combinedSnapshot(currentStart, todayStart, nowMillis)
        }
        val previous = previousSnapshot(period, currentStart, todayStart, nowMillis)
        val total = current.durations.values.sum()
        val boundaries = chartBoundaries(period, currentStart, todayStart, nowMillis)
        val overallBuckets = bucketsForSnapshot(current, boundaries, todayStart)
        val apps = current.durations.entries
            .sortedByDescending { it.value }
            .map { (packageName, durationMillis) ->
                AppUsage(
                    packageName = packageName,
                    displayName = appLabel(packageName),
                    durationMillis = durationMillis,
                    percentage = ScreenTimeFormatting.percentage(durationMillis, total),
                    buckets = bucketsForPackage(
                        packageName,
                        period,
                        current,
                        boundaries,
                        currentStart,
                        todayStart,
                    ),
                )
            }

        return ScreenTimeReport(
            period = period,
            totalMillis = total,
            previousTotalMillis = previous?.durations?.values?.sum() ?: 0L,
            changePercentage = previous?.let {
                ScreenTimeFormatting.changePercentage(total, it.durations.values.sum())
            },
            apps = apps,
            usageBuckets = overallBuckets,
            chartTitle = when (period) {
                UsagePeriod.DAY -> "Today by hour"
                UsagePeriod.WEEK -> "Last 7 days"
                UsagePeriod.MONTH -> "Last 30 days"
                UsagePeriod.ALL -> "All time by month"
            },
            screenOnMillis = current.screenOnMillis,
            pickups = current.pickups,
            trackingSinceMillis = trackingStart,
        )
    }

    /** Saves every completed day since installation so all-time data survives OS pruning. */
    fun archiveCompletedDays() {
        if (!hasUsageAccess()) return
        val todayStart = startOfDay(System.currentTimeMillis())
        val initialStart = minOf(startOfDay(firstInstallTime()), addDays(todayStart, -60))
        var day = archive.latestDay()?.let { addDays(it, 1) } ?: initialStart
        while (day < todayStart) {
            if (!archive.hasDay(day)) {
                archive.replaceDay(day, filtered(reader.read(day, addDays(day, 1))))
            }
            day = addDays(day, 1)
        }
    }

    private fun combinedSnapshot(
        rangeStart: Long,
        todayStart: Long,
        nowMillis: Long,
    ): ExactUsageSnapshot {
        val archivedDurations = archive.appTotals(rangeStart, todayStart).toMutableMap()
        val archivedSummary = archive.summary(rangeStart, todayStart)
        val today = filtered(reader.read(todayStart, nowMillis))
        today.durations.forEach { (packageName, duration) ->
            archivedDurations[packageName] = (archivedDurations[packageName] ?: 0L) + duration
        }
        return ExactUsageSnapshot(
            durations = archivedDurations,
            sessions = today.sessions,
            screenOnMillis = archivedSummary.screenOnMillis + today.screenOnMillis,
            pickups = archivedSummary.pickups + today.pickups,
        )
    }

    private fun previousSnapshot(
        period: UsagePeriod,
        currentStart: Long,
        todayStart: Long,
        nowMillis: Long,
    ): ExactUsageSnapshot? = when (period) {
        UsagePeriod.DAY -> {
            val previousStart = addDays(todayStart, -1)
            val sameElapsedTime = previousStart + (nowMillis - todayStart)
            filtered(reader.read(previousStart, sameElapsedTime))
        }
        UsagePeriod.WEEK -> archivedSnapshot(addDays(currentStart, -7), currentStart)
        UsagePeriod.MONTH -> archivedSnapshot(addDays(currentStart, -30), currentStart)
        UsagePeriod.ALL -> null
    }

    private fun archivedSnapshot(startDay: Long, endDay: Long): ExactUsageSnapshot {
        val summary = archive.summary(startDay, endDay)
        return ExactUsageSnapshot(
            durations = archive.appTotals(startDay, endDay),
            sessions = emptyList(),
            screenOnMillis = summary.screenOnMillis,
            pickups = summary.pickups,
        )
    }

    private fun filtered(snapshot: ExactUsageSnapshot): ExactUsageSnapshot {
        val excluded = excludedPackages()
        return snapshot.copy(
            durations = snapshot.durations.filterKeys { it !in excluded },
            sessions = snapshot.sessions.filter { it.packageName !in excluded },
        )
    }

    private data class BucketBoundary(val start: Long, val end: Long, val label: String)

    private fun chartBoundaries(
        period: UsagePeriod,
        currentStart: Long,
        todayStart: Long,
        nowMillis: Long,
    ): List<BucketBoundary> = when (period) {
        UsagePeriod.DAY -> (0 until 24).map { hour ->
            val start = todayStart + hour * HOUR_MILLIS
            BucketBoundary(start, start + HOUR_MILLIS, hourLabel(hour))
        }
        UsagePeriod.WEEK -> dailyBoundaries(currentStart, 7, "EEE")
        UsagePeriod.MONTH -> dailyBoundaries(currentStart, 30, "d")
        UsagePeriod.ALL -> monthlyBoundaries(currentStart, nowMillis)
    }

    private fun dailyBoundaries(start: Long, count: Int, pattern: String): List<BucketBoundary> {
        val format = SimpleDateFormat(pattern, Locale.getDefault())
        return (0 until count).map { index ->
            val day = addDays(start, index)
            BucketBoundary(day, addDays(day, 1), format.format(day))
        }
    }

    private fun monthlyBoundaries(start: Long, end: Long): List<BucketBoundary> {
        val format = SimpleDateFormat("MMM", Locale.getDefault())
        val result = mutableListOf<BucketBoundary>()
        var month = startOfMonth(start)
        while (month <= end) {
            val next = addMonths(month, 1)
            result += BucketBoundary(month, next, format.format(month))
            month = next
        }
        return result
    }

    private fun bucketsForSnapshot(
        snapshot: ExactUsageSnapshot,
        boundaries: List<BucketBoundary>,
        todayStart: Long,
    ): List<UsageBucket> {
        if (boundaries.size == 24) {
            return boundaries.map { boundary ->
                UsageBucket(boundary.label, durationInBoundary(snapshot.sessions, boundary))
            }
        }
        val daily = archive.dailyTotals(
            boundaries.firstOrNull()?.start ?: todayStart,
            todayStart,
        ).toMutableMap()
        snapshot.sessions.groupBy { startOfDay(it.startMillis) }.forEach { (day, sessions) ->
            daily[day] = (daily[day] ?: 0L) + sessions.sumOf { it.durationMillis }
        }
        return boundaries.map { boundary ->
            UsageBucket(
                boundary.label,
                daily.entries.filter { (day, _) -> day in boundary.start until boundary.end }
                    .sumOf { it.value },
            )
        }
    }

    private fun bucketsForPackage(
        packageName: String,
        period: UsagePeriod,
        snapshot: ExactUsageSnapshot,
        boundaries: List<BucketBoundary>,
        currentStart: Long,
        todayStart: Long,
    ): List<UsageBucket> {
        val packageSessions = snapshot.sessions.filter { it.packageName == packageName }
        if (period == UsagePeriod.DAY) {
            return boundaries.map { boundary ->
                UsageBucket(boundary.label, durationInBoundary(packageSessions, boundary))
            }
        }
        val daily = archive.appDailyTotals(packageName, currentStart, todayStart).toMutableMap()
        packageSessions.groupBy { startOfDay(it.startMillis) }.forEach { (day, sessions) ->
            daily[day] = (daily[day] ?: 0L) + sessions.sumOf { it.durationMillis }
        }
        return boundaries.map { boundary ->
            UsageBucket(
                boundary.label,
                daily.entries.filter { (day, _) -> day in boundary.start until boundary.end }
                    .sumOf { it.value },
            )
        }
    }

    private fun durationInBoundary(sessions: List<UsageSession>, boundary: BucketBoundary): Long =
        sessions.sumOf { session ->
            val start = maxOf(session.startMillis, boundary.start)
            val end = minOf(session.endMillis, boundary.end)
            (end - start).coerceAtLeast(0L)
        }

    private fun excludedPackages(): Set<String> {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launcher = packageManager.resolveActivity(
            homeIntent,
            PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName
        return setOfNotNull(context.packageName, launcher, "com.android.systemui")
    }

    private fun appLabel(packageName: String): String = try {
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        fallbackLabel(packageName)
    } catch (_: SecurityException) {
        fallbackLabel(packageName)
    }

    private fun fallbackLabel(packageName: String): String =
        packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }

    private fun firstInstallTime(): Long = try {
        packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
    } catch (_: PackageManager.NameNotFoundException) {
        System.currentTimeMillis()
    }

    private fun hourLabel(hour: Int): String = when {
        hour == 0 -> "12a"
        hour < 12 -> "${hour}a"
        hour == 12 -> "12p"
        else -> "${hour - 12}p"
    }

    private fun startOfDay(timeMillis: Long): Long = Calendar.getInstance().apply {
        this.timeInMillis = timeMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun startOfMonth(timeMillis: Long): Long = Calendar.getInstance().apply {
        this.timeInMillis = timeMillis
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun addDays(timeMillis: Long, days: Int): Long = Calendar.getInstance().apply {
        this.timeInMillis = timeMillis
        add(Calendar.DAY_OF_YEAR, days)
    }.timeInMillis

    private fun addMonths(timeMillis: Long, months: Int): Long = Calendar.getInstance().apply {
        this.timeInMillis = timeMillis
        add(Calendar.MONTH, months)
    }.timeInMillis

    private companion object {
        const val HOUR_MILLIS = 60L * 60L * 1000L
    }
}
