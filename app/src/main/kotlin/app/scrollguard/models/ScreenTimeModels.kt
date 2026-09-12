/*
 * Copyright 2026 Scroll Guard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package app.scrollguard.models

enum class UsagePeriod { DAY, WEEK, MONTH, ALL }

data class UsageBucket(
    val label: String,
    val durationMillis: Long,
)

data class AppUsage(
    val packageName: String,
    val displayName: String,
    val durationMillis: Long,
    val percentage: Int,
    val buckets: List<UsageBucket> = emptyList(),
)

data class AppImpact(
    val packageName: String,
    val displayName: String,
    val beforeDailyMillis: Long,
    val sinceDailyMillis: Long,
    val changePercentage: Int?,
)

data class ScreenTimeImpact(
    val startedAtMillis: Long,
    val baselineDays: Int,
    val beforeDailyMillis: Long,
    val sinceDailyMillis: Long,
    val changePercentage: Int?,
    val timeSavedMillis: Long,
    val apps: List<AppImpact>,
)

data class ScreenTimeReport(
    val period: UsagePeriod,
    val totalMillis: Long,
    val previousTotalMillis: Long,
    val changePercentage: Int?,
    val apps: List<AppUsage>,
    val usageBuckets: List<UsageBucket>,
    val chartTitle: String,
    val screenOnMillis: Long,
    val pickups: Int,
    val trackingSinceMillis: Long,
    val impact: ScreenTimeImpact? = null,
)
