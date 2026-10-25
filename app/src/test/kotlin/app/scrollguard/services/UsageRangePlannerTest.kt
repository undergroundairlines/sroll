/*
 * Copyright 2026 Scroll Guard contributors
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package app.scrollguard.services

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageRangePlannerTest {
    @Test
    fun partialStartAndEndDaysNeverOverlapArchivedTotals() = inZone("UTC") {
        val start = instant(2026, 10, 2, 14)
        val end = instant(2026, 10, 7, 10)
        val plan = UsageRangePlanner.plan(start, end)
        assertEquals(UsageRange(instant(2026, 10, 3, 0), instant(2026, 10, 7, 0)), plan.archived)
        assertEquals(
            listOf(UsageRange(start, plan.archived.start), UsageRange(plan.archived.end, end)),
            plan.partials,
        )
        assertEquals(end - start, plan.archived.end - plan.archived.start +
            plan.partials.sumOf { it.end - it.start })
    }

    @Test
    fun sameDayRangeReadsEventsOnceAndNeverAddsWholeDayArchive() = inZone("UTC") {
        val start = instant(2026, 10, 7, 14)
        val end = instant(2026, 10, 7, 16)
        val plan = UsageRangePlanner.plan(start, end)
        assertEquals(plan.archived.start, plan.archived.end)
        assertEquals(listOf(UsageRange(start, end)), plan.partials)
    }

    @Test
    fun exactMidnightRangeUsesOnlyWholeDays() = inZone("UTC") {
        val start = instant(2026, 10, 2, 0)
        val end = instant(2026, 10, 7, 0)
        val plan = UsageRangePlanner.plan(start, end)
        assertEquals(UsageRange(start, end), plan.archived)
        assertTrue(plan.partials.isEmpty())
    }

    @Test
    fun daylightSavingDaysIncludeEveryHourSoChartMatchesTotal() = inZone("Pacific/Auckland") {
        val longDay = UsageRangePlanner.hours(instant(2026, 4, 5, 0))
        val shortDay = UsageRangePlanner.hours(instant(2026, 9, 27, 0))
        assertEquals(25, longDay.size)
        assertEquals(23, shortDay.size)
        assertEquals(25L * 60L * 60L * 1000L, longDay.sumOf { it.end - it.start })
        assertEquals(23L * 60L * 60L * 1000L, shortDay.sumOf { it.end - it.start })
        assertTrue(longDay.zipWithNext().all { (a, b) -> a.end == b.start })
    }

    private fun instant(year: Int, month: Int, day: Int, hour: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, 0, 0)
        }.timeInMillis

    private fun inZone(zone: String, block: () -> Unit) {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zone))
            block()
        } finally {
            TimeZone.setDefault(previous)
        }
    }
}
