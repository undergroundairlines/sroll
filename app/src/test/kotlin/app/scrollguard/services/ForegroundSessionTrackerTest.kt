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
}
