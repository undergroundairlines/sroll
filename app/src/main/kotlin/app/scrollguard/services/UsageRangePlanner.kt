/*
 * Copyright 2026 Scroll Guard contributors
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package app.scrollguard.services

import java.util.Calendar

internal data class UsageRange(val start: Long, val end: Long)

internal data class UsageRangePlan(
    val archived: UsageRange,
    val partials: List<UsageRange>,
)

/** Partitions a range into whole local days and disjoint partial event queries. */
internal object UsageRangePlanner {
    fun plan(start: Long, end: Long): UsageRangePlan {
        if (end <= start) return UsageRangePlan(UsageRange(start, start), emptyList())
        val startDay = dayStart(start)
        val endDay = dayStart(end)
        if (startDay == endDay) {
            return UsageRangePlan(UsageRange(endDay, endDay), listOf(UsageRange(start, end)))
        }
        val fullStart = if (start == startDay) startDay else Calendar.getInstance().apply {
            timeInMillis = startDay
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        val partials = buildList {
            if (start < fullStart) add(UsageRange(start, fullStart))
            if (endDay < end) add(UsageRange(endDay, end))
        }
        return UsageRangePlan(UsageRange(fullStart, endDay), partials)
    }

    fun hours(dayStart: Long): List<UsageRange> {
        val end = Calendar.getInstance().apply {
            timeInMillis = dayStart
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        return buildList {
            var cursor = dayStart
            while (cursor < end) {
                val next = minOf(cursor + 60L * 60L * 1000L, end)
                add(UsageRange(cursor, next))
                cursor = next
            }
        }
    }

    private fun dayStart(time: Long): Long = Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
