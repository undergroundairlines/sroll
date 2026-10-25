/* Copyright 2026 Scroll Guard contributors. Licensed under the Apache License, Version 2.0. */
package app.scrollguard.services.detectors

import app.scrollguard.models.InstagramProtectionMode
import app.scrollguard.models.MediaBounds
import org.junit.Assert.assertEquals
import org.junit.Test

/** Synthetic ownership checks; a real Instagram profile hierarchy remains unverified. */
class InstagramProfileOwnershipTest {
    private val viewport = MediaBounds(0, 0, 720, 1600)
    private val headerBounds = MediaBounds(0, 100, 720, 300)
    private val detailBounds = MediaBounds(0, 300, 720, 600)
    private fun node(id: String, parent: Int = -1, bounds: MediaBounds = viewport,
        kind: String = "android.widget.FrameLayout", visible: Boolean = true) = NodeSignal(
        "com.instagram.android:id/$id", "", false, false, visible,
        bounds.width, bounds.height, bounds.left, bounds.top, bounds.right, bounds.bottom,
        parentIndex = parent, className = kind)
    private fun screen(nodes: List<NodeSignal>) = InstagramFeedPolicy.evaluate(
        InstagramProtectionMode.FEED_LOCK, AccessibilityTreeSnapshot(nodes, false), viewport)
    private fun profile(root: NodeSignal = node("profile_content")) = listOf(root,
        node("row_profile_header", parent = 0, bounds = headerBounds),
        node("profile_user_info_compose_view", parent = 0, bounds = detailBounds))

    @Test fun directHeaderAndDetailsBelongingToOneProfileRootRemainAllowed() {
        assertEquals(InstagramScreen.PROFILE, screen(profile()))
    }

    @Test fun nestedHeaderAndDetailsBelongingToOneContentPageRemainAllowed() {
        val nodes = listOf(node("root"), node("profile_page", parent = 0),
            node("header_wrapper", parent = 1, bounds = headerBounds),
            node("detail_wrapper", parent = 1, bounds = detailBounds),
            node("row_profile_header", parent = 2, bounds = headerBounds),
            node("profile_header_bio", parent = 3, bounds = detailBounds))
        assertEquals(InstagramScreen.PROFILE, screen(nodes))
    }

    @Test fun parallelVisibleCachedPagesCannotCombineTheirProfileMarkers() {
        val nodes = listOf(node("root"), node("cached_page", parent = 0), node("current_page", parent = 0),
            node("row_profile_header", parent = 1, bounds = headerBounds),
            node("profile_user_info_compose_view", parent = 2, bounds = detailBounds))
        assertEquals(InstagramScreen.UNKNOWN, screen(nodes))
    }

    @Test fun genericWindowWrappersCannotCombineSeparateFullHeightPages() {
        val nodes = listOf(node("root", kind = "android.view.DecorView"),
            node("android_content", parent = 0), node("cached_page", parent = 1), node("current_page", parent = 1),
            node("row_profile_header", parent = 2, bounds = headerBounds),
            node("profile_header_follow_button", parent = 3, bounds = detailBounds))
        assertEquals(InstagramScreen.UNKNOWN, screen(nodes))
    }

    @Test fun aPagerCannotJoinEvenSmallParallelProfileBranches() {
        val nodes = listOf(node("root"), node("pager", parent = 0, kind = "androidx.viewpager.widget.ViewPager"),
            node("first_page", parent = 1, bounds = headerBounds),
            node("second_page", parent = 1, bounds = detailBounds),
            node("row_profile_header", parent = 2, bounds = headerBounds),
            node("profile_user_info_compose_view", parent = 3, bounds = detailBounds))
        assertEquals(InstagramScreen.UNKNOWN, screen(nodes))
    }

    @Test fun hiddenCachedProfileParentCannotGrantAccessThroughVisibleChildren() {
        val nodes = listOf(node("root"), node("cached_profile", parent = 0, visible = false),
            node("row_profile_header", parent = 1, bounds = headerBounds),
            node("profile_header_bio", parent = 1, bounds = detailBounds))
        assertEquals(InstagramScreen.UNKNOWN, screen(nodes))
    }

    @Test fun aCurrentNativeReelViewerStillWinsOverAValidProfile() {
        assertEquals(InstagramScreen.REELS, screen(profile() + node("reels_viewer", parent = 0)))
    }

    @Test fun disconnectedFlatMarkersDoNotProveOneProfilePage() {
        assertEquals(InstagramScreen.UNKNOWN, screen(listOf(
            node("row_profile_header", bounds = headerBounds),
            node("profile_user_info_compose_view", bounds = detailBounds))))
    }
}
