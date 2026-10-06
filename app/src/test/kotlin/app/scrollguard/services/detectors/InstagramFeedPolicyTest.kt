/* Copyright 2026 Scroll Guard contributors. Licensed under the Apache License, Version 2.0. */
package app.scrollguard.services.detectors

import app.scrollguard.models.InstagramProtectionMode
import app.scrollguard.models.MediaBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class InstagramFeedPolicyTest {
    private val viewport = MediaBounds(0, 0, 720, 1600)
    private fun node(id: String, bounds: MediaBounds = MediaBounds(0, 300, 720, 1300),
        visible: Boolean = true, selected: Boolean = false) = NodeSignal(
        "com.instagram.android:id/$id", "", selected, false, visible,
        bounds.width, bounds.height, bounds.left, bounds.top, bounds.right, bounds.bottom,
    )
    private fun screen(vararg nodes: NodeSignal) = InstagramFeedPolicy.evaluate(
        InstagramProtectionMode.FEED_LOCK, AccessibilityTreeSnapshot(nodes.toList(), false), viewport,
    )

    @Test fun homeWithNoReelIdentifiersIsStillLocked() {
        assertEquals(InstagramScreen.HOME, screen(node("row_feed_profile_header")))
    }
    @Test fun renamedOrEmptyInterfaceIsLocked() {
        assertEquals(InstagramScreen.UNKNOWN, screen(node("brand_new_feed")))
        assertEquals(InstagramScreen.UNKNOWN, screen())
        assertFalse(InstagramScreen.UNKNOWN.allowed)
    }
    @Test fun missingOrTruncatedTreeCannotGrantAccess() {
        assertEquals(InstagramScreen.UNKNOWN, InstagramFeedPolicy.evaluate(
            InstagramProtectionMode.FEED_LOCK, null, viewport))
        assertEquals(InstagramScreen.UNKNOWN, InstagramFeedPolicy.evaluate(
            InstagramProtectionMode.FEED_LOCK,
            AccessibilityTreeSnapshot(listOf(node("direct_thread")), true), viewport))
    }
    @Test fun selectedProfileTabAloneCannotUncoverFeed() {
        assertFalse(screen(node("profile_tab", selected = true)).allowed)
    }
    @Test fun staleProfileOrInboxNodesDoNotOverrideHome() {
        assertEquals(InstagramScreen.HOME, screen(node("row_feed_profile_header"),
            node("row_profile_header"), node("profile_user_info_compose_view"), node("direct_thread")))
    }
    @Test fun visibleReelWinsOverInbox() {
        assertEquals(InstagramScreen.REELS, screen(node("clips_video_container"), node("direct_thread")))
    }
    @Test fun realProfileWithTwoContentMarkersIsAllowed() {
        assertEquals(InstagramScreen.PROFILE, screen(node("row_profile_header_imageview_frame_layout"),
            node("profile_user_info_compose_view")))
    }
    @Test fun offscreenProfileCannotUnlockUnknownFeed() {
        val offscreen = MediaBounds(0, 2000, 720, 2500)
        assertFalse(screen(node("row_profile_header", offscreen),
            node("profile_user_info_compose_view", offscreen)).allowed)
    }
    @Test fun hiddenProfileCannotUnlockUnknownFeed() {
        assertFalse(screen(node("row_profile_header", visible = false),
            node("profile_user_info_compose_view", visible = false)).allowed)
    }
    @Test fun messagesAndStoriesRequireContentNotNavigationLabels() {
        assertEquals(InstagramScreen.MESSAGES, screen(node("inbox_refreshable_thread_list_recyclerview")))
        assertEquals(InstagramScreen.STORY, screen(node("reel_viewer")))
        assertFalse(screen(node("direct_tab", selected = true)).allowed)
    }
    @Test fun tinyInboxPreviewDoesNotUnlockTheFeed() {
        assertFalse(screen(node("direct_thread", MediaBounds(0, 0, 200, 150))).allowed)
    }
    @Test fun completeAppLockHasNoAllowedScreens() {
        val inbox = AccessibilityTreeSnapshot(listOf(node("direct_thread")), false)
        assertEquals(InstagramScreen.APP_LOCK, InstagramFeedPolicy.evaluate(
            InstagramProtectionMode.APP_LOCK, inbox, viewport))
        assertEquals(InstagramScreen.APP_LOCK, InstagramFeedPolicy.evaluate(
            InstagramProtectionMode.APP_LOCK, null, viewport))
    }
}
