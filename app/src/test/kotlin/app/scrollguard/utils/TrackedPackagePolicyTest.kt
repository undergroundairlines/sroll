/* Copyright 2026 Scroll Guard contributors. Licensed under the Apache License, Version 2.0. */
package app.scrollguard.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackedPackagePolicyTest {
    private val instagram = "com.instagram.android"
    private val youtube = "com.google.android.youtube"
    private val tiktok = "com.zhiliaoapp.musically"

    @Test
    fun successiveTogglesUseTheLatestStateAndPreserveOtherApps() {
        val first = TrackedPackagePolicy.setEnabled(
            TrackedPackageState(listOf(instagram)), youtube, true, false, 1_000L,
        )
        val second = TrackedPackagePolicy.setEnabled(first, tiktok, true, false, 1_001L)
        val third = TrackedPackagePolicy.setEnabled(second, youtube, false, false, 1_002L)
        assertEquals(listOf(instagram, tiktok), third.packages)
        assertNull(third.pendingTarget)
    }

    @Test
    fun strictDisableLeavesProtectionEnabledUntilThePersistedDeadline() {
        val state = TrackedPackagePolicy.setEnabled(
            TrackedPackageState(listOf(instagram, youtube)), instagram, false, true, 1_000L,
        )
        assertEquals(listOf(instagram, youtube), state.packages)
        assertEquals(instagram, state.pendingTarget)
        assertEquals(1_801_000L, state.unlockAtMillis)
    }

    @Test
    fun enablingAnAppCancelsItsPendingDisableWithoutTurningOtherProtectionOff() {
        val state = TrackedPackagePolicy.setEnabled(
            TrackedPackageState(listOf(instagram, youtube), instagram, 1_801_000L),
            instagram, true, true, 1_500L,
        )
        assertEquals(listOf(instagram, youtube), state.packages)
        assertNull(state.pendingTarget)
        assertEquals(0L, state.unlockAtMillis)
    }

    @Test
    fun anotherToggleCannotReplaceOrRestartAnExistingStrictRequest() {
        val stored = TrackedPackageState(listOf(instagram, youtube), instagram, 1_801_000L)
        val state = TrackedPackagePolicy.setEnabled(stored, youtube, false, true, 1_500L)
        assertEquals(stored, state)
    }

    @Test
    fun enablingAnotherAppPreservesThePendingStrictModeRequestAndDeadline() {
        val stored = TrackedPackageState(listOf(instagram), StrictModePolicy.STRICT_MODE_TARGET, 2_000L)
        val state = TrackedPackagePolicy.setEnabled(stored, youtube, true, true, 1_500L)
        assertEquals(listOf(instagram, youtube), state.packages)
        assertEquals(stored.pendingTarget, state.pendingTarget)
        assertEquals(stored.unlockAtMillis, state.unlockAtMillis)
    }

    @Test
    fun bulkChangesCannotDisableSeveralAppsDuringStrictMode() {
        val state = TrackedPackagePolicy.setPackages(
            TrackedPackageState(listOf(instagram, youtube)), listOf(tiktok), true, 1_000L,
        )
        assertEquals(listOf(instagram, youtube, tiktok), state.packages)
        assertEquals(instagram, state.pendingTarget)
        assertTrue(state.unlockAtMillis > 1_000L)
    }
}
