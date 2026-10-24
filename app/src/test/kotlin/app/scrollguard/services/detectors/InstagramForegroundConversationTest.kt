/* Copyright 2026 Scroll Guard contributors. Licensed under the Apache License, Version 2.0. */
package app.scrollguard.services.detectors

import app.scrollguard.models.InstagramProtectionMode
import app.scrollguard.models.MediaBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Synthetic retained-page regressions; these do not claim real Instagram compatibility. */
class InstagramForegroundConversationTest {
    private val viewport = MediaBounds(0, 0, 720, 1600)
    private fun node(id: String, parent: Int = -1, order: Int = 0,
        bounds: MediaBounds = viewport, editable: Boolean = false,
        kind: String = "android.widget.FrameLayout") = NodeSignal(
        "com.instagram.android:id/$id", "", false, false, true,
        bounds.width, bounds.height, bounds.left, bounds.top, bounds.right, bounds.bottom,
        parentIndex = parent, drawingOrder = order, editable = editable, className = kind)

    private fun pages(feedId: String = "feed_recycler_view", feedOrder: Int = 1,
        chatOrder: Int = 2, mediaId: String = "clips_video_container",
        mediaKind: String = "android.widget.FrameLayout") = listOf(
        node("root"),
        node(feedId, parent = 0, order = feedOrder),
        node(mediaId, parent = 1, kind = mediaKind),
        node("conversation_page", parent = 0, order = chatOrder),
        node("message_list", parent = 3, bounds = MediaBounds(0, 200, 720, 1400)),
        node("row_thread_composer_edittext", parent = 3, editable = true,
            bounds = MediaBounds(0, 1400, 720, 1500)),
    )

    private fun evaluate(nodes: List<NodeSignal>, mode: InstagramProtectionMode) =
        InstagramFeedPolicy.evaluate(mode, AccessibilityTreeSnapshot(nodes, false), viewport)

    @Test fun currentConversationInFrontOfRetainedHomeVideoRemainsUsable() {
        for (mode in listOf(InstagramProtectionMode.FEED_LOCK, InstagramProtectionMode.SOCIAL))
            assertEquals(InstagramScreen.MESSAGES, evaluate(pages(), mode))
    }

    @Test fun anonymousHomeRenderSurfaceBehindAConversationCannotCoverTyping() {
        assertEquals(InstagramScreen.MESSAGES, evaluate(pages(mediaId = "", mediaKind = "android.view.View"),
            InstagramProtectionMode.SOCIAL))
    }

    @Test fun currentHomeInFrontOfCachedConversationRemainsLocked() {
        for (mode in listOf(InstagramProtectionMode.FEED_LOCK, InstagramProtectionMode.SOCIAL))
            assertFalse(evaluate(pages(feedOrder = 2, chatOrder = 1), mode).allowed)
    }

    @Test fun chatAnchorsWithoutDrawingOrderProofCannotUncoverHome() {
        for (mode in listOf(InstagramProtectionMode.FEED_LOCK, InstagramProtectionMode.SOCIAL))
            assertFalse(evaluate(pages(feedOrder = 0, chatOrder = 0), mode).allowed)
    }

    @Test fun currentConversationInFrontOfRetainedExploreRemainsUsable() {
        for (mode in listOf(InstagramProtectionMode.FEED_LOCK, InstagramProtectionMode.SOCIAL))
            assertEquals(InstagramScreen.MESSAGES, evaluate(pages(feedId = "explore_grid", mediaId = "photo"), mode))
    }

    @Test fun currentExploreInFrontOfCachedConversationRemainsLocked() {
        for (mode in listOf(InstagramProtectionMode.FEED_LOCK, InstagramProtectionMode.SOCIAL))
            assertEquals(InstagramScreen.EXPLORE,
                evaluate(pages(feedId = "explore_grid", feedOrder = 2, chatOrder = 1, mediaId = "photo"), mode))
    }

    @Test fun anExternalReelViewerInFrontOfTheConversationStillLocks() {
        val nodes = pages() + node("reels_viewer", parent = 0, order = 3)
        for (mode in listOf(InstagramProtectionMode.FEED_LOCK, InstagramProtectionMode.SOCIAL))
            assertEquals(InstagramScreen.REELS, evaluate(nodes, mode))
    }

    @Test fun aDedicatedReelPagerInsideMessageHistoryStillLocks() {
        val nodes = pages() + node("clips_viewer_view_pager", parent = 4)
        for (mode in listOf(InstagramProtectionMode.FEED_LOCK, InstagramProtectionMode.SOCIAL))
            assertEquals(InstagramScreen.REELS, evaluate(nodes, mode))
    }

    @Test fun unscannedRootContentCannotUseForegroundChatAsDefaultLockBypass() {
        val tree = AccessibilityTreeSnapshot(pages(), true, incompleteParents = setOf(0))
        assertEquals(InstagramScreen.UNKNOWN,
            InstagramFeedPolicy.evaluate(InstagramProtectionMode.FEED_LOCK, tree, viewport))
    }
}
