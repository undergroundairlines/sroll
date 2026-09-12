/*
 * Copyright 2026 Scroll Guard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package app.scrollguard.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenTimeFormattingTest {
    @Test
    fun durationIsCompactAndReadable() {
        assertEquals("0m", ScreenTimeFormatting.duration(0L))
        assertEquals("<1m", ScreenTimeFormatting.duration(30_000L))
        assertEquals("42m", ScreenTimeFormatting.duration(42L * 60_000L))
        assertEquals("2h 05m", ScreenTimeFormatting.duration(125L * 60_000L))
    }

    @Test
    fun appPercentageIsRoundedAndBounded() {
        assertEquals(46, ScreenTimeFormatting.percentage(46L, 100L))
        assertEquals(0, ScreenTimeFormatting.percentage(1L, 0L))
        assertEquals(100, ScreenTimeFormatting.percentage(200L, 100L))
    }

    @Test
    fun comparisonPercentageHandlesNoPreviousUsage() {
        assertNull(ScreenTimeFormatting.changePercentage(100L, 0L))
        assertEquals(25, ScreenTimeFormatting.changePercentage(125L, 100L))
        assertEquals(-25, ScreenTimeFormatting.changePercentage(75L, 100L))
    }
}
