/* Copyright 2026 Scroll Guard contributors. Licensed under the Apache License, Version 2.0. */
package app.scrollguard.services.detectors

import app.scrollguard.models.InstagramProtectionMode
import app.scrollguard.models.MediaBounds
import org.junit.Assert.*
import org.junit.Test

class InstagramHomeVideoTest {
    private val viewport = MediaBounds(0, 0, 720, 1600)
    private fun node(id: String = "", parent: Int = -1, kind: String = "android.widget.FrameLayout",
        role: MediaRole = MediaRole.NONE, visible: Boolean = true,
        bounds: MediaBounds = viewport) = NodeSignal(id, "", false, false, visible,
        bounds.width, bounds.height, bounds.left, bounds.top, bounds.right, bounds.bottom,
        parentIndex = parent, className = kind, mediaRole = role)
    private fun home(video: NodeSignal) = listOf(node(), node("com.instagram.android:id/feed_recycler_view", 0), video)
    private fun evaluate(nodes: List<NodeSignal>, truncated: Boolean = false) = InstagramFeedPolicy.evaluate(
        InstagramProtectionMode.SOCIAL, AccessibilityTreeSnapshot(nodes, truncated), viewport)

    @Test fun nativeVideoWithoutAnyReelIdentifierIsBlockedOnlyWithinHome() {
        for (kind in listOf("android.view.TextureView", "android.widget.VideoView")) {
            assertEquals(if (kind == "android.widget.VideoView") InstagramScreen.HOME_REEL else InstagramScreen.HOME_MEDIA_UNKNOWN,
                evaluate(home(node(parent = 1, kind = kind))))
            assertEquals(InstagramScreen.OTHER_ALLOWED, evaluate(listOf(node(), node(parent = 0, kind = kind))))
        }
    }
    @Test fun positivelyIdentifiedVideoIsStillBlockedInAnIncompleteHomeTree() {
        assertEquals(InstagramScreen.HOME_MEDIA_UNKNOWN, evaluate(home(node(parent = 1, kind = "android.view.TextureView")), true))
    }
    @Test fun explicitMediaRoleDoesNotNeedGuessedPrivateResourceIds() {
        for (role in listOf(MediaRole.VIDEO, MediaRole.REEL))
            assertEquals(InstagramScreen.HOME_REEL, evaluate(home(node(parent = 1, role = role))))
    }
    @Test fun selectedTabAndUnrelatedCameraPreviewCannotLockPhotoSending() {
        val nodes = home(node(parent = 0, kind = "android.view.TextureView")) +
            node("com.instagram.android:id/feed_tab").copy(selected = true)
        assertEquals(InstagramScreen.HOME_POSTS, evaluate(nodes))
    }
    @Test fun photoAndGenericSurfaceViewsAreNotAssumedToBeVideos() {
        for (kind in listOf("android.widget.ImageView", "android.view.SurfaceView"))
            assertEquals(InstagramScreen.HOME_POSTS, evaluate(home(node(parent = 1, kind = kind))))
    }
    @Test fun hiddenOffscreenAndSmallAvatarVideosCannotLockHome() {
        for (n in listOf(node(parent = 1, kind = "android.view.TextureView", visible = false),
            node(parent = 1, kind = "android.view.TextureView", bounds = MediaBounds(0, 1700, 720, 2000)),
            node(parent = 1, kind = "android.view.TextureView", bounds = MediaBounds(0, 0, 100, 100))))
            assertEquals(InstagramScreen.HOME_POSTS, evaluate(home(n)))
    }
    @Test fun partialVisibleHomeVideoRemainsBlocked() {
        assertEquals(InstagramScreen.HOME_REEL, evaluate(home(node(parent = 1,
            kind = "android.widget.VideoView", bounds = MediaBounds(0, -250, 720, 30)))))
    }
    @Test fun currentStoryWithRetainedHomeVideoRemainsAllowed() {
        val nodes = home(node(parent = 1, kind = "android.view.TextureView")) +
            node("com.instagram.android:id/reel_viewer", 0).copy(drawingOrder = 2) +
            node("com.instagram.android:id/reel_viewer_progress_bar", 3) +
            node("com.instagram.android:id/reel_viewer_title", 3)
        assertEquals(InstagramScreen.STORY, evaluate(nodes))
    }
    @Test fun sharePreviewInsideAChatIsNotHomeVideo() {
        val nodes = listOf(node(), node("conversation", 0),
            node("com.instagram.android:id/message_list", 1),
            node("com.instagram.android:id/row_thread_composer_edittext", 1,
                bounds = MediaBounds(0, 1400, 720, 1500)).copy(editable = true),
            node("com.instagram.android:id/feed_recycler_view", 2),
            node(parent = 4, kind = "android.view.TextureView"))
        assertEquals(InstagramScreen.MESSAGES, evaluate(nodes))
    }
    @Test fun retainedMessagePreviewCannotLockAPhotoPickerWhenComposerIsHidden() {
        val nodes = listOf(node(), node("com.instagram.android:id/message_list", 0),
            node("com.instagram.android:id/feed_recycler_view", 1),
            node("com.instagram.android:id/clips_video_container", 2, kind = "android.view.TextureView"))
        assertEquals(InstagramScreen.OTHER_ALLOWED, evaluate(nodes))
        assertEquals(InstagramScreen.UNKNOWN, InstagramFeedPolicy.evaluate(InstagramProtectionMode.FEED_LOCK,
            AccessibilityTreeSnapshot(nodes, false), viewport))
    }
    @Test fun ordinaryPhotoReplacesVideoWithoutATimedUnlock() {
        assertEquals(InstagramScreen.HOME_MEDIA_UNKNOWN, evaluate(home(node(parent = 1, kind = "android.view.TextureView"))))
        assertEquals(InstagramScreen.HOME_POSTS, evaluate(home(node(parent = 1, kind = "android.widget.ImageView"))))
    }
    @Test fun expandedReportIncludesAnonymousNativeViewsButNotDescriptions() {
        val tree = AccessibilityTreeSnapshot(home(node(parent = 1, kind = "android.view.TextureView")
            .copy(label = "private username caption password")), false)
        val report = tree.structuralReport().joinToString("\n")
        assertTrue(report.contains("android.view.TextureView"))
        assertFalse(report.contains("private"))
        assertFalse(report.contains("username"))
    }
    @Test fun onlyFixedDescriptionRolesAreRetained() {
        assertEquals(MediaRole.VIDEO, InstagramMediaSemantics.role("Video, private account content"))
        assertEquals(MediaRole.REEL, InstagramMediaSemantics.role("Play reel"))
        assertEquals(MediaRole.PHOTO, InstagramMediaSemantics.role("Photo by private account"))
        for (value in listOf("Photo by Alice: a reel I like", "Reels", "Original audio", "Video games are fun", "Reel life"))
            assertEquals(MediaRole.NONE, InstagramMediaSemantics.role(value))
    }
    @Test fun aPlainAndroidViewLeafInHomeIsAmbiguousRatherThanPretendingItIsVideo() {
        assertEquals(InstagramScreen.HOME_MEDIA_UNKNOWN, evaluate(home(node(parent = 1, kind = "android.view.View"))))
        assertEquals(InstagramScreen.HOME_POSTS, evaluate(home(node(parent = 1, kind = "android.view.View").copy(childCount = 2))))
        assertEquals(InstagramScreen.HOME_POSTS, evaluate(home(node(parent = 1, kind = "android.view.View", role = MediaRole.PHOTO))))
    }
    @Test fun aRememberedRenderNodeStaysGuardedUntilItsVisiblePartHasGone() {
        val small = home(node(parent = 1, kind = "android.view.View", bounds = MediaBounds(0, 200, 720, 205)).copy(identity = 42))
        assertEquals(InstagramScreen.HOME_POSTS, evaluate(small))
        assertEquals(InstagramScreen.HOME_MEDIA_UNKNOWN, InstagramFeedPolicy.evaluate(InstagramProtectionMode.SOCIAL,
            AccessibilityTreeSnapshot(small, false), viewport, rememberedHomeMedia = setOf(42)))
        val photo = home(node(parent = 1, kind = "android.view.View", role = MediaRole.PHOTO).copy(identity = 42))
        assertEquals(InstagramScreen.HOME_POSTS, InstagramFeedPolicy.evaluate(InstagramProtectionMode.SOCIAL,
            AccessibilityTreeSnapshot(photo, false), viewport, rememberedHomeMedia = setOf(42)))
        val gone = small.map { it.copy(visible = false) }
        assertEquals(InstagramScreen.OTHER_ALLOWED, InstagramFeedPolicy.evaluate(InstagramProtectionMode.SOCIAL,
            AccessibilityTreeSnapshot(gone, false), viewport, rememberedHomeMedia = setOf(42)))
    }
}
