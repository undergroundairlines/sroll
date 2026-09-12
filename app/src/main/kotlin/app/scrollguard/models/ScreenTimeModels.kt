/*
 * Copyright 2026 Scroll Guard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package app.scrollguard.models

enum class UsagePeriod { DAY, WEEK }

data class AppUsage(
    val packageName: String,
    val displayName: String,
    val durationMillis: Long,
    val percentage: Int,
)

data class DailyUsage(
    val dayLabel: String,
    val durationMillis: Long,
)

data class ScreenTimeReport(
    val period: UsagePeriod,
    val totalMillis: Long,
    val previousTotalMillis: Long,
    val changePercentage: Int?,
    val apps: List<AppUsage>,
    val lastSevenDays: List<DailyUsage>,
)
