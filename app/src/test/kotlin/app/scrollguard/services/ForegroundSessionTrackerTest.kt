/*
 * Copyright 2026 Scroll Guard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package app.scrollguard.services

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundSessionTrackerTest {
    @Test
    fun switchingAppsClosesThePreviousSessionWhenPauseIsMissing() {
        val tracker = ForegroundSessionTracker(0L, 30L)
        tracker.resume("instagram", "Feed", 0L)
        tracker.resume("youtube", "Watch", 10L)
        tracker.pause("instagram", "Feed", 12L)
        tracker.pause("youtube", "Watch", 20L)

        assertEquals(
            listOf(UsageSession("instagram", 0L, 10L), UsageSession("youtube", 10L, 20L)),
            tracker.finish(),
        )
    }

    @Test
    fun oldActivityStoppingDoesNotEndTheNewActivity() {
        val tracker = ForegroundSessionTracker(0L, 30L)
        tracker.resume("instagram", "Feed", 0L)
        tracker.resume("instagram", "Profile", 10L)
        tracker.pause("instagram", "Feed", 12L)
        tracker.pause("instagram", "Profile", 20L)

        assertEquals(listOf(UsageSession("instagram", 0L, 20L)), tracker.finish())
    }

    @Test
    fun timeWithScreenOffIsNotCounted() {
        val tracker = ForegroundSessionTracker(0L, 60L)
        tracker.resume("instagram", "Feed", 0L)
        tracker.screenOff(20L)
        tracker.screenOn(50L)
        tracker.pause("instagram", "Feed", 60L)

        assertEquals(
            listOf(UsageSession("instagram", 0L, 20L), UsageSession("instagram", 50L, 60L)),
            tracker.finish(),
        )
    }

    @Test
    fun sessionsCrossingBothBoundariesAreClippedToTheRequestedPeriod() {
        val tracker = ForegroundSessionTracker(10L, 30L)
        tracker.resume("instagram", "Feed", 0L)
        tracker.pause("instagram", "Feed", 40L)
        assertEquals(listOf(UsageSession("instagram", 10L, 30L)), tracker.finish())
    }

    @Test
    fun rapidActivityTransitionsNeverCountTwoAppsForTheSameTime() {
        val tracker = ForegroundSessionTracker(0L, 100L)
        tracker.resume("instagram", "Home", 0L)
        tracker.resume("instagram", "Inbox", 5L)
        tracker.resume("youtube", "Watch", 10L)
        tracker.pause("instagram", "Home", 15L)
        tracker.pause("instagram", "Inbox", 20L)
        tracker.resume("instagram", "Thread", 30L)
        tracker.pause("youtube", "Watch", 35L)
        tracker.resume("instagram", "Thread", 40L)
        val sessions = tracker.finish()
        assertEquals(100L, sessions.sumOf { it.durationMillis })
        assertEquals(
            listOf(UsageSession("instagram", 0L, 10L), UsageSession("youtube", 10L, 30L),
                UsageSession("instagram", 30L, 100L)),
            sessions,
        )
    }
}
