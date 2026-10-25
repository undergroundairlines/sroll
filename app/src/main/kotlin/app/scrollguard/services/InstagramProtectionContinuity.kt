/* Copyright 2026 Scroll Guard contributors. Licensed under the Apache License, Version 2.0. */
package app.scrollguard.services

import app.scrollguard.models.InstagramProtectionMode
import app.scrollguard.services.detectors.InstagramScreen

internal enum class InstagramTreeQuality { MISSING, INCOMPLETE, COMPLETE }

internal data class InstagramContinuityDecision(val screen: InstagramScreen, val retained: Boolean)

/** An unrecognised frame cannot prove that a guarded feed has been left.
 * Called only after the service confirms Instagram's current application window.
 * This is a screen-specific latch, not a timed permission or an old success claim.
 */
internal class InstagramProtectionContinuity {
    private var windowId = -1
    private var mode: InstagramProtectionMode? = null
    private var guardedScreen: InstagramScreen? = null

    fun resolve(currentMode: InstagramProtectionMode, currentWindow: Int,
        observed: InstagramScreen, quality: InstagramTreeQuality,
        confirmedHomePhoto: Boolean = false): InstagramContinuityDecision {
        if (windowId != currentWindow || mode != currentMode) reset()
        windowId = currentWindow
        mode = currentMode
        val previous = guardedScreen
        val knownSafe = observed in setOf(InstagramScreen.MESSAGES, InstagramScreen.PROFILE, InstagramScreen.STORY)
        val completeHome = observed == InstagramScreen.HOME_POSTS && quality == InstagramTreeQuality.COMPLETE && confirmedHomePhoto
        if (knownSafe || completeHome) {
            guardedScreen = null
            return InstagramContinuityDecision(observed, false)
        }
        if (previous != null && currentMode == InstagramProtectionMode.SOCIAL &&
            (observed == InstagramScreen.OTHER_ALLOWED ||
                observed == InstagramScreen.HOME_POSTS)) {
            return InstagramContinuityDecision(previous, true)
        }
        if (observed in guardedScreens) guardedScreen = observed
        return InstagramContinuityDecision(observed, false)
    }

    fun isGuarded(currentMode: InstagramProtectionMode, currentWindow: Int): Boolean =
        mode == currentMode && windowId == currentWindow && guardedScreen != null

    fun reset() {
        windowId = -1
        mode = null
        guardedScreen = null
    }

    private companion object {
        val guardedScreens = setOf(InstagramScreen.HOME, InstagramScreen.HOME_REEL,
            InstagramScreen.HOME_MEDIA_UNKNOWN, InstagramScreen.REELS, InstagramScreen.EXPLORE)
    }
}
