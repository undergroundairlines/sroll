/*
 * Copyright 2026 Scroll Guard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package app.scrollguard.services.detectors

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class DetectionScoringTest {
    @Test
    fun youtubeProgressBarBlocksByItself() {
        val result = scoreYouTube(YouTubeSignals(progressBar = true))

        assertTrue(result.score >= 6)
        assertTrue("Shorts progress bar" in result.reasons)
    }

    @Test
    fun normalYouTubePlayerDoesNotBlock() {
        val result = scoreYouTube(
            YouTubeSignals(
                shortsControls = true,
                normalPlayer = true,
                normalControls = true,
            ),
        )

        assertEquals(0, result.score)
    }

    @Test
    fun selectedReelsTabBlocks() {
        val result = scoreInstagram(InstagramSignals(selectedTabId = true))

        assertTrue(result.score >= 7)
    }

    @Test
    fun weakInstagramHintsDoNotBlockNormalProfile() {
        val result = scoreInstagram(
            InstagramSignals(
                reelIds = true,
                reelsControls = true,
                verticalPager = true,
                normalScreenId = true,
            ),
        )

        assertTrue(result.score < 7)
    }

    @Test
    fun explicitViewerStillBlocksFromProfileEntry() {
        val result = scoreInstagram(
            InstagramSignals(
                viewerId = true,
                normalScreenId = true,
            ),
        )

        assertEquals(7, result.score)
    }

    @Test
    fun selectedHomeTabOverridesPreloadedReelsSignals() {
        val result = scoreInstagram(
            InstagramSignals(
                viewerId = true,
                reelIds = true,
                reelsControls = true,
                verticalPager = true,
                homeTabSelected = true,
                normalScreenId = true,
            ),
        )

        assertTrue(result.score < 7)
    }
}
