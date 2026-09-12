/*
 * Copyright 2026 Scroll Guard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package app.scrollguard.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictModePolicyTest {

    @Test
    fun unlockDelayIsExactlyThirtyMinutes() {
        val now = 1_000L

        assertEquals(now + 30L * 60L * 1000L, StrictModePolicy.unlockAt(now))
    }

    @Test
    fun remainingSecondsRoundsUpSoCountdownDoesNotFinishEarly() {
        assertEquals(2L, StrictModePolicy.remainingSeconds(2_001L, 1_000L))
        assertEquals(1L, StrictModePolicy.remainingSeconds(2_000L, 1_001L))
        assertEquals(0L, StrictModePolicy.remainingSeconds(2_000L, 2_000L))
    }

    @Test
    fun requestOnlyExpiresAtItsDeadline() {
        assertFalse(StrictModePolicy.isExpired(10_000L, 9_999L))
        assertTrue(StrictModePolicy.isExpired(10_000L, 10_000L))
        assertFalse(StrictModePolicy.isExpired(0L, 10_000L))
    }
}
