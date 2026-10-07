/* Copyright 2026 Scroll Guard contributors. Licensed under the Apache License, Version 2.0. */
package app.scrollguard.services.detectors

import app.scrollguard.models.InstagramProtectionMode
import app.scrollguard.models.MediaBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramFeedPolicyTest {
    private val viewport = MediaBounds(0, 0, 720, 1600)
    private fun node(id: String, bounds: MediaBounds = MediaBounds(0, 300, 720, 1300),
        visible: Boolean = true, selected: Boolean = false, parent: Int = -1,
        editable: Boolean = false) = NodeSignal(
        "com.instagram.android:id/$id", "", selected, false, visible,
        bounds.width, bounds.height, bounds.left, bounds.top, bounds.right, bounds.bottom,
        parentIndex = parent, editable = editable,
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

    // These are synthetic structural regressions. They intentionally contain no account
    // content and do not assert compatibility with a particular real Instagram version.
    private fun conversationNodes(
        historyBounds: MediaBounds = MediaBounds(0, 200, 720, 1400),
        composerBounds: MediaBounds = MediaBounds(0, 1400, 720, 1500),
    ): List<NodeSignal> = listOf(
        node("root", viewport),
        node("conversation_page", viewport, parent = 0),
        node("message_list", historyBounds, parent = 1),
        node("row_thread_composer_edittext", composerBounds, parent = 1, editable = true),
    )

    @Test fun conversationHistoryAndEditableComposerAllowTyping() {
        assertEquals(InstagramScreen.MESSAGES, screen(*conversationNodes().toTypedArray()))
    }

    @Test fun composerAndHistoryCanBeNestedUnderOneVisibleConversationPage() {
        val nodes = listOf(
            node("root", viewport),
            node("conversation_page", viewport, parent = 0),
            node("history_wrapper", viewport, parent = 1),
            node("message_list", MediaBounds(0, 200, 720, 1400), parent = 2),
            node("composer_wrapper", MediaBounds(0, 1400, 720, 1500), parent = 1),
            node("row_thread_composer_edittext", MediaBounds(0, 1400, 720, 1500),
                parent = 4, editable = true),
        )
        assertEquals(InstagramScreen.MESSAGES, screen(*nodes.toTypedArray()))
    }

    @Test fun keyboardSizedViewportDoesNotRelockAConfirmedConversation() {
        val keyboardViewport = MediaBounds(0, 0, 720, 940)
        val nodes = conversationNodes(
            historyBounds = MediaBounds(0, 180, 720, 820),
            composerBounds = MediaBounds(0, 820, 720, 920),
        )
        assertEquals(InstagramScreen.MESSAGES, InstagramFeedPolicy.evaluate(
            InstagramProtectionMode.FEED_LOCK, AccessibilityTreeSnapshot(nodes, false), keyboardViewport))
    }

    @Test fun focusedChatContentBelowImeStillUsesApplicationContentCoordinates() {
        // Structural coordinates from the failed Android 15 synthetic keyboard test, not Instagram.
        // The IME began at y=381, but Android left its active editable composer at y=418..464.
        val content = MediaBounds(0, 24, 320, 616)
        val shield = content.copy(bottom = 381)
        val nodes = listOf(
            node("root", content), node("conversation_page", MediaBounds(0, 80, 320, 512), parent = 0),
            node("message_list", MediaBounds(0, 153, 320, 345), parent = 1),
            node("row_thread_composer_edittext", MediaBounds(0, 418, 320, 464), parent = 1, editable = true),
        )
        val tree = AccessibilityTreeSnapshot(nodes, false)
        assertEquals(InstagramScreen.MESSAGES, InstagramFeedPolicy.evaluate(InstagramProtectionMode.FEED_LOCK, tree, content))
        assertEquals(InstagramScreen.UNKNOWN, InstagramFeedPolicy.evaluate(InstagramProtectionMode.FEED_LOCK, tree, shield))
    }

    @Test fun aComposerAloneCannotUnlockAnUnknownPageEvenWithADirectThreadPrefix() {
        assertFalse(screen(node("direct_thread_composer_edittext", editable = true)).allowed)
        assertFalse(screen(node("message_composer", editable = true)).allowed)
    }

    @Test fun conversationHistoryAloneCannotUnlockAnUnknownPage() {
        assertFalse(screen(node("message_list")).allowed)
        assertFalse(screen(node("direct_thread_message_list_recycler_view")).allowed)
    }

    @Test fun nonEditableComposerCannotConfirmAConversation() {
        val nodes = conversationNodes().toMutableList()
        nodes[3] = nodes[3].copy(editable = false)
        assertFalse(screen(*nodes.toTypedArray()).allowed)
    }

    @Test fun hiddenComposerOrHistoryCannotConfirmAConversation() {
        for (hiddenIndex in listOf(2, 3)) {
            val nodes = conversationNodes().toMutableList()
            nodes[hiddenIndex] = nodes[hiddenIndex].copy(visible = false)
            assertFalse(screen(*nodes.toTypedArray()).allowed)
        }
    }

    @Test fun offscreenComposerCannotConfirmAConversation() {
        val nodes = conversationNodes(composerBounds = MediaBounds(0, 1700, 720, 1800))
        assertFalse(screen(*nodes.toTypedArray()).allowed)
    }

    @Test fun cachedConversationBehindHomeDoesNotUnlockHome() {
        val nodes = conversationNodes() + node("feed_recycler_view", viewport)
        assertEquals(InstagramScreen.HOME, screen(*nodes.toTypedArray()))
    }

    @Test fun composerAndHistoryOnParallelPagesCannotConfirmAConversation() {
        val nodes = listOf(
            node("root", viewport),
            node("current_page", viewport, parent = 0),
            node("cached_page", viewport, parent = 0),
            node("message_list", MediaBounds(0, 200, 720, 1400), parent = 1),
            node("row_thread_composer_edittext", MediaBounds(0, 1400, 720, 1500),
                parent = 2, editable = true),
        )
        assertFalse(screen(*nodes.toTypedArray()).allowed)
    }

    @Test fun decorAndContentWrappersCannotJoinTwoFullHeightCachedPages() {
        val nodes = listOf(
            node("root", viewport).copy(className = "android.view.DecorView"),
            node("android_content", viewport, parent = 0).copy(className = "android.widget.FrameLayout"),
            node("navigation_wrapper", viewport, parent = 1),
            node("current_page", viewport, parent = 2),
            node("cached_page", viewport, parent = 2),
            node("message_list", MediaBounds(0, 200, 720, 1400), parent = 3),
            node("row_thread_composer_edittext", MediaBounds(0, 1400, 720, 1500),
                parent = 4, editable = true),
        )
        for (mode in listOf(InstagramProtectionMode.FEED_LOCK, InstagramProtectionMode.SOCIAL)) {
            assertEquals(InstagramScreen.UNKNOWN, InstagramFeedPolicy.evaluate(
                mode, AccessibilityTreeSnapshot(nodes, false), viewport))
        }
    }

    @Test fun aViewPagerCannotConfirmTheSameConversationEvenWithSmallBranches() {
        val nodes = conversationNodes().toMutableList()
        nodes[1] = nodes[1].copy(className = "androidx.viewpager.widget.ViewPager")
        assertFalse(screen(*nodes.toTypedArray()).allowed)
    }

    @Test fun aSharedRootAloneDoesNotProveTheSameConversationPage() {
        val nodes = listOf(
            node("root", viewport),
            node("message_list", MediaBounds(0, 200, 720, 1400), parent = 0),
            node("row_thread_composer_edittext", MediaBounds(0, 1400, 720, 1500),
                parent = 0, editable = true),
        )
        assertFalse(screen(*nodes.toTypedArray()).allowed)
    }

    @Test fun aHiddenSharedContentParentCannotConfirmAConversation() {
        val nodes = conversationNodes().toMutableList()
        nodes[1] = nodes[1].copy(visible = false)
        assertFalse(screen(*nodes.toTypedArray()).allowed)
    }

    @Test fun narrowOrTinyMessagePreviewCannotConfirmAConversation() {
        for (bounds in listOf(MediaBounds(0, 200, 150, 1400), MediaBounds(0, 200, 720, 230))) {
            assertFalse(screen(*conversationNodes(historyBounds = bounds).toTypedArray()).allowed)
        }
    }

    @Test fun smallInlineClipPreviewDoesNotRelockMessages() {
        val nodes = conversationNodes() + node("clips_video_container", MediaBounds(200, 400, 500, 600))
        assertEquals(InstagramScreen.MESSAGES, screen(*nodes.toTypedArray()))
    }

    @Test fun aFullReelViewerOpenedFromMessagesStillLocks() {
        val nodes = conversationNodes() + node("clips_viewer_view_pager", viewport)
        assertEquals(InstagramScreen.REELS, screen(*nodes.toTypedArray()))
    }

    @Test fun missingMessageRowsDoNotTurnACurrentConversationIntoUnknown() {
        val tree = AccessibilityTreeSnapshot(conversationNodes(), true, incompleteParents = setOf(2))
        assertEquals(InstagramScreen.MESSAGES, InstagramFeedPolicy.evaluate(InstagramProtectionMode.FEED_LOCK, tree, viewport))
    }

    @Test fun unreadRowsNestedWithinMessageHistoryDoNotRelockChat() {
        val nodes = conversationNodes() + node("message_row", parent = 2)
        val tree = AccessibilityTreeSnapshot(nodes, true, incompleteParents = setOf(4))
        assertEquals(InstagramScreen.MESSAGES, InstagramFeedPolicy.evaluate(InstagramProtectionMode.FEED_LOCK, tree, viewport))
    }

    @Test fun gapsAtRootOrUnknownParallelPageCannotUseChatAsABypass() {
        for (gap in listOf(0, 1, 4)) {
            val nodes = conversationNodes() + node("unknown_page", viewport, parent = 0)
            val tree = AccessibilityTreeSnapshot(nodes, true, incompleteParents = setOf(gap))
            assertEquals(InstagramScreen.UNKNOWN, InstagramFeedPolicy.evaluate(InstagramProtectionMode.FEED_LOCK, tree, viewport))
        }
    }

    @Test fun aPartialChatStillRelocksIfItsCurrentComposerDisappears() {
        val nodes = conversationNodes().filterIndexed { i, _ -> i != 3 }
        val tree = AccessibilityTreeSnapshot(nodes, true, incompleteParents = setOf(2))
        assertEquals(InstagramScreen.UNKNOWN, InstagramFeedPolicy.evaluate(InstagramProtectionMode.FEED_LOCK, tree, viewport))
    }

    @Test fun inlineReelMediaBelongingToMessageHistoryDoesNotBlockTexting() {
        val nodes = conversationNodes() + node("clips_video_container", viewport, parent = 2)
        assertEquals(InstagramScreen.MESSAGES, screen(*nodes.toTypedArray()))
    }

    @Test fun externalReelViewerWinsEvenWhenSomeMessageRowsAreUnavailable() {
        val nodes = conversationNodes() + node("clips_viewer_view_pager", viewport, parent = 0)
        val tree = AccessibilityTreeSnapshot(nodes, true, incompleteParents = setOf(2))
        assertEquals(InstagramScreen.REELS, InstagramFeedPolicy.evaluate(InstagramProtectionMode.FEED_LOCK, tree, viewport))
    }

    @Test fun tallChatWrappersInsideAnExactThreadAreStillOneConversation() {
        val nodes = listOf(node("root", viewport), node("direct_thread", viewport, parent = 0),
            node("history_wrapper", viewport, parent = 1), node("composer_wrapper", viewport, parent = 1),
            node("message_list", parent = 2),
            node("row_thread_composer_edittext", MediaBounds(0, 1400, 720, 1500), parent = 3, editable = true),
            node("clips_video_container", viewport, parent = 4))
        assertEquals(InstagramScreen.MESSAGES, screen(*nodes.toTypedArray()))
    }

    private fun foregroundStoryNodes(storyOrder: Int = 2, homeOrder: Int = 1) = listOf(
        node("root", viewport),
        node("feed_recycler_view", viewport, parent = 0).copy(drawingOrder = homeOrder),
        node("reel_viewer", viewport, parent = 0).copy(drawingOrder = storyOrder),
        node("reel_viewer_progress_bar", MediaBounds(0, 20, 720, 40), parent = 2),
        node("reel_viewer_title", MediaBounds(0, 40, 720, 100), parent = 2),
    )

    @Test fun foregroundStoryWithRetainedHomeRemainsAllowed() {
        assertEquals(InstagramScreen.STORY, screen(*foregroundStoryNodes().toTypedArray()))
        assertEquals(InstagramScreen.STORY, screen(*foregroundStoryNodes(0, 0).toTypedArray()))
    }

    @Test fun cachedStoryBehindCurrentHomeCannotUnlockHome() {
        assertEquals(InstagramScreen.HOME, screen(*foregroundStoryNodes(1, 2).toTypedArray()))
    }

    @Test fun storyDrawingOrderProofDoesNotNeedEveryProgressControlDuringAdvance() {
        assertEquals(InstagramScreen.STORY, screen(*foregroundStoryNodes().take(3).toTypedArray()))
        assertEquals(InstagramScreen.HOME, screen(*foregroundStoryNodes(0, 0).take(3).toTypedArray()))
    }

    @Test fun reelMediaResharedWithinAStoryDoesNotBlockTheStory() {
        val nodes = foregroundStoryNodes() + node("clips_video_container", viewport, parent = 2)
        assertEquals(InstagramScreen.STORY, screen(*nodes.toTypedArray()))
    }

    @Test fun retainedHomeReelBehindStoryDoesNotBlockItsForegroundViewer() {
        val nodes = foregroundStoryNodes() + node("clips_video_container", viewport, parent = 1)
        assertEquals(InstagramScreen.STORY, screen(*nodes.toTypedArray()))
    }

    @Test fun missingStoryMediaDescendantsDoNotBlockAnIdentifiedStory() {
        val tree = AccessibilityTreeSnapshot(foregroundStoryNodes(), true, incompleteParents = setOf(2))
        assertEquals(InstagramScreen.STORY, InstagramFeedPolicy.evaluate(InstagramProtectionMode.FEED_LOCK, tree, viewport))
    }

    @Test fun aRootGapOrExternalReelCannotBeHiddenByAStory() {
        val nodes = foregroundStoryNodes()
        assertEquals(InstagramScreen.UNKNOWN, InstagramFeedPolicy.evaluate(InstagramProtectionMode.FEED_LOCK,
            AccessibilityTreeSnapshot(nodes, true, incompleteParents = setOf(0)), viewport))
        assertEquals(InstagramScreen.REELS, screen(*(nodes + node("reels_viewer", viewport, parent = 0).copy(drawingOrder = 3)).toTypedArray()))
    }

    @Test fun storyControlIdsWithoutTheViewerDoNotOpenTheFeed() {
        assertEquals(InstagramScreen.HOME, screen(node("feed_recycler_view", viewport),
            node("reel_viewer_progress_bar"), node("reel_viewer_title")))
    }

    @Test fun aFullReelViewerAlsoLocksWhenProfileOrStoryMarkersRemain() {
        for (safeNodes in listOf(
            listOf(node("row_profile_header"), node("profile_user_info_compose_view")),
            listOf(node("reel_viewer", viewport)),
        )) {
            val nodes = safeNodes + node("reels_viewer", viewport)
            assertEquals(InstagramScreen.REELS, screen(*nodes.toTypedArray()))
        }
    }

    @Test fun socialModeAllowsRecognisedOrdinaryHomePosts() {
        val tree = AccessibilityTreeSnapshot(listOf(node("feed_recycler_view", viewport)), false)
        assertEquals(InstagramScreen.HOME_POSTS,
            InstagramFeedPolicy.evaluate(InstagramProtectionMode.SOCIAL, tree, viewport))
        assertEquals(InstagramScreen.HOME,
            InstagramFeedPolicy.evaluate(InstagramProtectionMode.FEED_LOCK, tree, viewport))
    }

    @Test fun socialModeStillLocksReelsEmbeddedInHome() {
        for (id in listOf("clips_video_container", "clips_media_component", "clips_single_media_component")) {
            val tree = AccessibilityTreeSnapshot(listOf(
                node("feed_recycler_view", viewport), node(id, MediaBounds(0, 500, 720, 850))), false)
            assertEquals(InstagramScreen.REELS,
                InstagramFeedPolicy.evaluate(InstagramProtectionMode.SOCIAL, tree, viewport))
        }
    }

    @Test fun socialModeCannotOpenAnUnknownOrTruncatedHome() {
        assertEquals(InstagramScreen.UNKNOWN, InstagramFeedPolicy.evaluate(
            InstagramProtectionMode.SOCIAL, AccessibilityTreeSnapshot(listOf(node("renamed_feed")), false), viewport))
        assertEquals(InstagramScreen.UNKNOWN, InstagramFeedPolicy.evaluate(
            InstagramProtectionMode.SOCIAL, AccessibilityTreeSnapshot(listOf(node("feed_recycler_view", viewport)), true), viewport))
        assertEquals(InstagramScreen.HOME, InstagramFeedPolicy.evaluate(
            InstagramProtectionMode.SOCIAL, AccessibilityTreeSnapshot(listOf(node("feed_tab", selected = true)), false), viewport))
    }

    @Test fun strongerModeOrderingSupportsStrictModeAndPreservesStoredModes() {
        assertTrue(InstagramProtectionMode.SOCIAL.strength < InstagramProtectionMode.FEED_LOCK.strength)
        assertTrue(InstagramProtectionMode.FEED_LOCK.strength < InstagramProtectionMode.APP_LOCK.strength)
        assertEquals(InstagramProtectionMode.FEED_LOCK, InstagramProtectionMode.fromStored("FEED_LOCK"))
        assertEquals(InstagramProtectionMode.APP_LOCK, InstagramProtectionMode.fromStored("APP_LOCK"))
        assertEquals(InstagramProtectionMode.SOCIAL, InstagramProtectionMode.fromStored("SOCIAL"))
        assertEquals(InstagramProtectionMode.FEED_LOCK, InstagramProtectionMode.fromStored(null))
        assertEquals(InstagramProtectionMode.FEED_LOCK, InstagramProtectionMode.fromStored("unsupported-mode"))
    }

    @Test fun structuralDiagnosticReportExcludesAccountContent() {
        val nodes = conversationNodes().map { it.copy(label = "private caption username password message") }
        val report = AccessibilityTreeSnapshot(nodes, false).structuralReport().joinToString("\n")
        assertTrue(report.contains("message_list"))
        assertTrue(report.contains("parent=1"))
        assertFalse(report.contains("private"))
        assertFalse(report.contains("username"))
        assertFalse(report.contains("password"))
    }
}
