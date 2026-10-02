/* Copyright 2026 Scroll Guard contributors. Licensed under the Apache License, Version 2.0. */
package app.scrollguard.services.detectors

import app.scrollguard.models.BlockAction
import app.scrollguard.models.MediaBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression scenarios derived from the user's resource-ID captures, with synthetic geometry. */
class InstagramHomeRegressionTest {
    private val video = MediaBounds(0, 300, 720, 1300)
    private val detector = InstagramReelsDetector()

    private fun node(id: String, bounds: MediaBounds = MediaBounds(0, 0, 0, 0),
        label: String = "", selected: Boolean = false, visible: Boolean = true) = NodeSignal(
        id = "com.instagram.android:id/$id", label = label, selected = selected,
        scrollable = false, visible = visible, width = bounds.width, height = bounds.height,
        left = bounds.left, top = bounds.top, right = bounds.right, bottom = bounds.bottom,
    )

    private fun result(vararg nodes: NodeSignal) = detector.detectTree(
        AccessibilityTreeSnapshot(nodes.toList(), false), 720, 1600,
    )

    private fun reelNodes() = arrayOf(
        node("clips_tab", label = "reels", selected = true),
        node("clips_viewer_view_pager"),
        node("clips_video_container", video),
        node("comment", label = "comments"), node("share", label = "share"),
        node("audio", label = "original audio"),
    )

    @Test fun homeReelWorksWhenInstagramReportsReelsSelectedInsteadOfHome() {
        val actual = result(node("row_feed_profile_header"), *reelNodes())
        assertTrue(actual.shouldBlock)
        assertEquals(BlockAction.SKIP_REEL, actual.action)
        assertEquals(video, actual.reelBounds)
    }

    @Test fun normalHomePostWithPreloadedOffscreenReelsNeverNavigatesBack() {
        val actual = result(node("row_feed_profile_header"), *reelNodes().filter {
            !it.id.endsWith("/clips_video_container")
        }.toTypedArray(), node("clips_video_container", MediaBounds(0, 1800, 720, 2800)))
        assertFalse(actual.shouldBlock)
        assertEquals(null, actual.reelBounds)
    }

    @Test fun selectedHomeWithHiddenPreloadedVideoIsAllowed() {
        val actual = result(node("feed_tab", selected = true),
            node("clips_video_container", video, visible = false),
            node("clips_viewer_view_pager"))
        assertFalse(actual.shouldBlock)
    }

    @Test fun profileIsAllowedEvenWhenAllReelSignalsArePresent() {
        val actual = result(node("row_profile_header_imageview_frame_layout"), *reelNodes())
        assertFalse(actual.shouldBlock)
        assertEquals(null, actual.reelBounds)
    }

    @Test fun storyIsAllowedEvenWhenAllReelSignalsArePresent() {
        val actual = result(node("reel_viewer"), *reelNodes())
        assertFalse(actual.shouldBlock)
    }

    @Test fun directMessageIsAllowedWithSharedVideoControls() {
        val actual = result(node("direct_thread"), *reelNodes())
        assertFalse(actual.shouldBlock)
    }

    @Test fun dedicatedReelsViewerStillRequestsBack() {
        val actual = result(*reelNodes())
        assertTrue(actual.shouldBlock)
        assertEquals(BlockAction.BACK, actual.action)
        assertEquals(null, actual.reelBounds)
    }

    @Test fun videoSurfaceWinsOverBroadReelContainer() {
        val actual = result(node("row_feed_profile_header"), *reelNodes(),
            node("clips_media_component", MediaBounds(0, 0, 720, 1600)))
        assertEquals(video, actual.reelBounds)
    }

    @Test fun coverClipsToToolbarAndBottomNavigation() {
        val actual = result(node("row_feed_profile_header"),
            node("clips_video_container", MediaBounds(-20, -100, 800, 1700)),
            node("action_bar", MediaBounds(0, 60, 720, 200)),
            node("feed_tab", MediaBounds(0, 1450, 144, 1520)))
        assertTrue(actual.shouldBlock)
        assertEquals(MediaBounds(0, 200, 720, 1450), actual.reelBounds)
    }

    @Test fun smallReelThumbnailInFeedDoesNotMaskOrdinaryPosts() {
        val actual = result(node("row_feed_profile_header"),
            node("clips_video_container", MediaBounds(200, 500, 450, 750)))
        assertFalse(actual.shouldBlock)
    }
}
