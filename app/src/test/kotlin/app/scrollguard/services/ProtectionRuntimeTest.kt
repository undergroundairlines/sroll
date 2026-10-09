/* Copyright 2026 Scroll Guard contributors. Licensed under the Apache License, Version 2.0. */
package app.scrollguard.services

import app.scrollguard.models.InstagramProtectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionRuntimeTest {
    @Test fun disconnectCannotLeaveCurrentAttachmentOrAppliedPreferencesClaimed() {
        ProtectionRuntime.connected(true)
        ProtectionRuntime.configured(InstagramProtectionMode.FEED_LOCK, true)
        ProtectionRuntime.checked("com.instagram.android", 7, "Complete (4 nodes)",
            "Home feed locked", "Attached")
        ProtectionRuntime.connected(false)
        val state = ProtectionRuntime.state.value
        assertFalse(state.connected)
        assertFalse(state.preferencesApplied)
        assertEquals("Removed: service disconnected", state.overlay)
        assertEquals("Service disconnected", state.screen)
    }

    @Test fun reconnectionRequiresPreferencesToBeAppliedAgain() {
        ProtectionRuntime.configured(InstagramProtectionMode.SOCIAL, true)
        ProtectionRuntime.connected(false)
        ProtectionRuntime.connected(true)
        assertTrue(ProtectionRuntime.state.value.connected)
        assertFalse(ProtectionRuntime.state.value.preferencesApplied)
        ProtectionRuntime.configured(InstagramProtectionMode.SOCIAL, true)
        assertTrue(ProtectionRuntime.state.value.preferencesApplied)
        assertEquals(InstagramProtectionMode.SOCIAL, ProtectionRuntime.state.value.instagramMode)
    }

    @Test fun leavingInstagramUpdatesCurrentStateWithoutTreatingHistoricalSampleAsCurrent() {
        val instagram = InstagramObservation(1L, 7, "0,0,720,1600", "Complete (4 nodes)",
            "Home feed locked", listOf("1 parent=0 feed_recycler_view bounds=0,0,720,1600 visible=true"))
        ProtectionRuntime.checked("com.instagram.android", 7, instagram.rootState,
            instagram.screen, "Attached", instagram)
        ProtectionRuntime.checked("com.android.launcher", 8, "Not sampled", "Instagram not foreground", "Removed")
        val state = ProtectionRuntime.state.value
        assertEquals("com.android.launcher", state.foregroundPackage)
        assertEquals(8, state.foregroundWindowId)
        assertEquals("Not sampled", state.rootState)
        assertEquals("Instagram not foreground", state.screen)
        assertEquals("Removed", state.overlay)
        assertEquals(instagram, state.lastInstagram)
        assertTrue(state.checkedAtMillis > instagram.timestampMillis)
    }

    @Test fun aMissingRootReplacesAPreviousCompleteRootAndClassification() {
        ProtectionRuntime.checked("com.instagram.android", 7, "Complete (4 nodes)", "Messages allowed", "Removed")
        ProtectionRuntime.checked("com.instagram.android", 7, "Missing", "Unrecognised screen locked", "Attached")
        val state = ProtectionRuntime.state.value
        assertEquals("Missing", state.rootState)
        assertEquals("Unrecognised screen locked", state.screen)
        assertEquals("Attached", state.overlay)
    }
}
