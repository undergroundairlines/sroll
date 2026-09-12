/*
 * Copyright 2026 Scroll Guard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package app.scrollguard.services

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import app.scrollguard.models.AppUsage
import app.scrollguard.models.DailyUsage
import app.scrollguard.models.ScreenTimeReport
import app.scrollguard.models.UsagePeriod
import app.scrollguard.utils.ScreenTimeFormatting
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Reads Android's on-device app usage history. No usage data leaves the phone. */
class ScreenTimeRepository(private val context: Context) {
    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
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
        val currentStart: Long
        val previousStart: Long
        val previousEnd: Long

        if (period == UsagePeriod.DAY) {
            currentStart = todayStart
            previousStart = addDays(todayStart, -1)
            previousEnd = todayStart
        } else {
            currentStart = addDays(todayStart, -6)
            previousStart = addDays(currentStart, -7)
            previousEnd = currentStart
        }

        val currentDurations = queryDurations(currentStart, nowMillis)
        val total = currentDurations.values.sum()
        val previousTotal = queryDurations(previousStart, previousEnd).values.sum()
        val apps = currentDurations.entries
            .sortedByDescending { it.value }
            .map { (packageName, durationMillis) ->
                AppUsage(
                    packageName = packageName,
                    displayName = appLabel(packageName),
                    durationMillis = durationMillis,
                    percentage = ScreenTimeFormatting.percentage(durationMillis, total),
                )
            }

        return ScreenTimeReport(
            period = period,
            totalMillis = total,
            previousTotalMillis = previousTotal,
            changePercentage = ScreenTimeFormatting.changePercentage(total, previousTotal),
            apps = apps,
            lastSevenDays = buildLastSevenDays(todayStart, nowMillis),
        )
    }

    private fun buildLastSevenDays(todayStart: Long, nowMillis: Long): List<DailyUsage> {
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        return (-6..0).map { offset ->
            val start = addDays(todayStart, offset)
            val end = if (offset == 0) nowMillis else addDays(start, 1)
            DailyUsage(
                dayLabel = dayFormat.format(start),
                durationMillis = queryDurations(start, end).values.sum(),
            )
        }
    }

    private fun queryDurations(startMillis: Long, endMillis: Long): Map<String, Long> {
        if (endMillis <= startMillis) return emptyMap()
        val excludedPackages = excludedPackages()
        return usageStatsManager.queryAndAggregateUsageStats(startMillis, endMillis)
            .asSequence()
            .filter { (packageName, stats) ->
                packageName !in excludedPackages && stats.totalTimeInForeground >= 60_000L
            }
            .associate { (packageName, stats) -> packageName to stats.totalTimeInForeground }
    }

    private fun excludedPackages(): Set<String> {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launcher = packageManager.resolveActivity(
            homeIntent,
            PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName
        return setOfNotNull(
            context.packageName,
            launcher,
            "com.android.systemui",
        )
    }

    private fun appLabel(packageName: String): String {
        return try {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        } catch (_: SecurityException) {
            packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    private fun startOfDay(timeMillis: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun addDays(timeMillis: Long, days: Int): Long {
        return Calendar.getInstance().apply {
            this.timeInMillis = timeMillis
            add(Calendar.DAY_OF_YEAR, days)
        }.timeInMillis
    }
}
