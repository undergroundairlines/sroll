/* Copyright 2026 Scroll Guard contributors. Licensed under the Apache License, Version 2.0. */
package app.scrollguard.services.detectors

import app.scrollguard.models.InstagramProtectionMode
import app.scrollguard.models.MediaBounds
import org.junit.Assert.assertEquals
import org.junit.Test

/** Structural regressions, without account content or assumptions about a real Instagram build. */
class InstagramMediaOwnershipTest {
    private val viewport = MediaBounds(0, 0, 720, 1600)
    private fun node(id: String = "", parent: Int = -1, role: MediaRole = MediaRole.NONE,
        visible: Boolean = true, kind: String = "android.widget.FrameLayout",
        bounds: MediaBounds = viewport, editable: Boolean = false) = NodeSignal(
        "com.instagram.android:id/$id", "", false, false, visible,
        bounds.width, bounds.height, bounds.left, bounds.top, bounds.right, bounds.bottom,
        parentIndex = parent, editable = editable, className = kind, mediaRole = role)
    private fun home(vararg media: NodeSignal) = listOf(node(), node("feed_recycler_view", parent = 0)) + media
    private fun screen(nodes: List<NodeSignal>, mode: InstagramProtectionMode = InstagramProtectionMode.SOCIAL) =
        InstagramFeedPolicy.evaluate(mode, AccessibilityTreeSnapshot(nodes, false), viewport)

    @Test fun anExplicitHomePhotoIsNotOverruledByAReusedClipWrapperName() {
        for (id in listOf("clips_video_container", "clips_media_component", "clips_single_media_component"))
            assertEquals(InstagramScreen.HOME_POSTS, screen(home(node(id, parent = 1, role = MediaRole.PHOTO))))
    }

    @Test fun anonymousRenderChildrenOfAnExplicitPhotoAreNotGuessedToBeVideos() {
        for (kind in listOf("android.view.View", "android.view.TextureView", "android.view.SurfaceView")) {
            val photo = home(node("clips_media_component", parent = 1, role = MediaRole.PHOTO),
                node(parent = 2, kind = kind))
            assertEquals(InstagramScreen.HOME_POSTS, screen(photo))
        }
    }

    @Test fun aHomeSurfaceViewIsConservativelyProtectedWithoutClaimingItIsAVideo() {
        assertEquals(InstagramScreen.HOME_MEDIA_UNKNOWN,
            screen(home(node(parent = 1, kind = "android.view.SurfaceView"))))
    }

    @Test fun anExplicitPhotoSurfaceViewRemainsUsableOnHome() {
        assertEquals(InstagramScreen.HOME_POSTS,
            screen(home(node(parent = 1, role = MediaRole.PHOTO, kind = "android.view.SurfaceView"))))
    }

    @Test fun aSurfaceViewCameraOutsideHomeDoesNotBecomeAFeed() {
        val nodes = listOf(node(), node("camera", parent = 0),
            node(parent = 1, kind = "android.view.SurfaceView"))
        assertEquals(InstagramScreen.OTHER_ALLOWED, screen(nodes))
    }

    @Test fun aNativeVideoChildStillBlocksEvenWhenItsWrapperSaysPhoto() {
        val nodes = home(node("clips_media_component", parent = 1, role = MediaRole.PHOTO),
            node(parent = 2, kind = "android.widget.VideoView"))
        assertEquals(InstagramScreen.HOME_REEL, screen(nodes))
    }

    @Test fun anExplicitVideoOrReelChildStillBlocksInsideAPhotoWrapper() {
        for (role in listOf(MediaRole.VIDEO, MediaRole.REEL)) {
            val nodes = home(node("clips_single_media_component", parent = 1, role = MediaRole.PHOTO),
                node(parent = 2, role = role))
            assertEquals(InstagramScreen.HOME_REEL, screen(nodes))
        }
    }

    @Test fun nativeVideoClassWinsOverAContradictoryPhotoDescriptionOnTheSameNode() {
        assertEquals(InstagramScreen.HOME_REEL, screen(home(node("clips_video_container", parent = 1,
            role = MediaRole.PHOTO, kind = "android.widget.VideoView"))))
    }

    @Test fun dedicatedReelBrowsingStillBlocksWhenItsCurrentItemIsAPhoto() {
        for (id in listOf("clips_viewer_view_pager", "reels_viewer"))
            assertEquals(InstagramScreen.REELS, screen(listOf(node(), node(id, parent = 0, role = MediaRole.PHOTO))))
    }

    @Test fun retainedHomeDoesNotOwnAnUnrelatedSmallClipPreviewInAnotherPage() {
        for (id in listOf("clips_video_container", "clips_media_component", "clips_single_media_component")) {
            val nodes = home(node("picker", parent = 0),
                node(id, parent = 2, bounds = MediaBounds(0, 300, 720, 650)))
            assertEquals(InstagramScreen.HOME_POSTS, screen(nodes))
        }
    }

    @Test fun aHeaderOwnsItsNearestFeedListRatherThanEveryOuterScrollContainer() {
        val nodes = listOf(node(kind = "android.widget.ScrollView"),
            node("renamed_feed", parent = 0, kind = "androidx.recyclerview.widget.RecyclerView"),
            node("row_feed_profile_header", parent = 1, bounds = MediaBounds(0, 200, 720, 300)),
            node("photo_picker", parent = 0),
            node(parent = 3, kind = "android.view.TextureView"))
        assertEquals(InstagramScreen.HOME_POSTS, screen(nodes))
        val inFeed = nodes + node(parent = 1, kind = "android.widget.VideoView")
        assertEquals(InstagramScreen.HOME_REEL, screen(inFeed))
    }

    @Test fun aHiddenCachedHomePageCannotBlockTheCurrentConversation() {
        val nodes = listOf(node(), node("feed_recycler_view", parent = 0, visible = false),
            node("clips_video_container", parent = 1), node("conversation_page", parent = 0),
            node("message_list", parent = 3, bounds = MediaBounds(0, 200, 720, 1400)),
            node("row_thread_composer_edittext", parent = 3, editable = true,
                bounds = MediaBounds(0, 1400, 720, 1500)))
        for (mode in listOf(InstagramProtectionMode.FEED_LOCK, InstagramProtectionMode.SOCIAL))
            assertEquals(InstagramScreen.MESSAGES, screen(nodes, mode))
    }

    @Test fun hiddenReelPageChildrenDoNotCountAsACurrentViewer() {
        val nodes = listOf(node(), node("cached_page", parent = 0, visible = false),
            node("reels_viewer", parent = 1))
        assertEquals(InstagramScreen.OTHER_ALLOWED, screen(nodes))
        assertEquals(InstagramScreen.UNKNOWN, screen(nodes, InstagramProtectionMode.FEED_LOCK))
    }

    @Test fun hiddenStoryPageChildrenCannotUnlockCurrentHome() {
        val nodes = home(node("cached_page", parent = 0, visible = false),
            node("reel_viewer", parent = 2), node("reel_viewer_title", parent = 3),
            node("reel_viewer_progress_bar", parent = 3))
        assertEquals(InstagramScreen.HOME, screen(nodes, InstagramProtectionMode.FEED_LOCK))
    }

    @Test fun hiddenProfilePageChildrenCannotGrantAnAllowlistException() {
        val nodes = listOf(node(), node("cached_page", parent = 0, visible = false),
            node("row_profile_header", parent = 1), node("profile_user_info_compose_view", parent = 1))
        assertEquals(InstagramScreen.UNKNOWN, screen(nodes, InstagramProtectionMode.FEED_LOCK))
    }

    @Test fun aHomeHeaderOrEmptyListIsNotPositivePhotoEvidence() {
        for (nodes in listOf(home(), home(node("row_feed_profile_header", parent = 1))))
            assertEquals(false, AccessibilityTreeSnapshot(nodes, false).hasHomePhoto(viewport))
    }

    @Test fun aLargeOwnedPhotoOrImageViewIsPositiveHomePhotoEvidence() {
        for (media in listOf(node(parent = 1, role = MediaRole.PHOTO),
            node(parent = 1, kind = "android.widget.ImageView")))
            assertEquals(true, AccessibilityTreeSnapshot(home(media), false).hasHomePhoto(viewport))
    }

    @Test fun aPhotoOutsideHomeCannotConfirmThatTheGuardedFeedIsGone() {
        val nodes = home(node(parent = 0, kind = "android.widget.ImageView", role = MediaRole.PHOTO))
        assertEquals(false, AccessibilityTreeSnapshot(nodes, false).hasHomePhoto(viewport))
    }

    @Test fun aSmallAvatarAndHeaderPhotoCannotConfirmAnOrdinaryHomePost() {
        val avatar = home(node(parent = 1, kind = "android.widget.ImageView", bounds = MediaBounds(0, 200, 80, 280)))
        val header = home(node("row_feed_profile_header", parent = 1),
            node(parent = 2, kind = "android.widget.ImageView", role = MediaRole.PHOTO))
        for (nodes in listOf(avatar, header))
            assertEquals(false, AccessibilityTreeSnapshot(nodes, false).hasHomePhoto(viewport))
    }

    @Test fun aPhotoOnAHiddenCachedHomePageCannotReleaseTheCurrentGuard() {
        val nodes = listOf(node(), node("feed_recycler_view", parent = 0, visible = false),
            node(parent = 1, kind = "android.widget.ImageView", role = MediaRole.PHOTO))
        assertEquals(false, AccessibilityTreeSnapshot(nodes, false).hasHomePhoto(viewport))
    }

    @Test fun aVideoChildPreventsItsPhotoWrapperFromConfirmingAnAllowedHomePost() {
        val nodes = home(node("clips_media_component", parent = 1, role = MediaRole.PHOTO),
            node(parent = 2, kind = "android.widget.VideoView"))
        assertEquals(false, AccessibilityTreeSnapshot(nodes, false).hasHomePhoto(viewport))
    }
}
