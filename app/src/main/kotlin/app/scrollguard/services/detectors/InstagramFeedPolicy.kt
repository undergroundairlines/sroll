/* Copyright 2026 Scroll Guard contributors. Licensed under the Apache License, Version 2.0. */
package app.scrollguard.services.detectors

import app.scrollguard.models.InstagramProtectionMode
import app.scrollguard.models.MediaBounds

internal enum class InstagramScreen(val description: String, val allowed: Boolean = false) {
    HOME("Home feed locked"),
    HOME_POSTS("Home posts allowed — selective detection is best effort", true),
    REELS("Reels locked"),
    EXPLORE("Explore locked"),
    PROFILE("Profile allowed", true),
    MESSAGES("Messages allowed", true),
    STORY("Story allowed", true),
    UNKNOWN("Unrecognised screen locked"),
    APP_LOCK("Instagram blocked completely"),
}

/** An allowlist, not a Reel score: missing or conflicting evidence cannot open the feed. */
internal object InstagramFeedPolicy {
    fun reason(screen: InstagramScreen, tree: AccessibilityTreeSnapshot?, viewport: MediaBounds): String = when (screen) {
        InstagramScreen.MESSAGES -> if (tree?.hasConversation(viewport) == true)
            "Visible message history and editable composer in one content subtree"
            else "Large visible exact inbox/thread container"
        InstagramScreen.PROFILE -> "Visible profile header and separate profile details"
        InstagramScreen.STORY -> "Large current Story viewer; underlying Home is not the foreground screen"
        InstagramScreen.HOME -> "Home content or selected Home navigation; entire Home is locked"
        InstagramScreen.HOME_POSTS -> "Large visible Home container; no recognised on-screen Reel media (best effort)"
        InstagramScreen.REELS -> "Visible Reel viewer or recognised Home Reel media"
        InstagramScreen.EXPLORE -> "Visible Explore content or selected Explore navigation"
        InstagramScreen.APP_LOCK -> "Whole-app preference; no interface exceptions"
        InstagramScreen.UNKNOWN -> when {
            tree == null -> "No root snapshot in the confirmed Instagram window"
            tree.truncated -> "Incomplete tree cannot grant access"
            else -> "No supported positive safe-screen structure"
        }
    }
    fun evaluate(
        mode: InstagramProtectionMode,
        tree: AccessibilityTreeSnapshot?,
        viewport: MediaBounds,
    ): InstagramScreen {
        if (mode == InstagramProtectionMode.APP_LOCK) return InstagramScreen.APP_LOCK
        if (tree == null) return InstagramScreen.UNKNOWN

        // A visible video/viewer wins over cached profile or inbox nodes. A small preview does
        // not count as a viewer, but the Home feed remains blocked even without any Reel IDs.
        if (tree.hasExternalMedia(viewport, 0.40f,
                "clips_viewer_view_pager", "reels_viewer") ||
            tree.hasExternalMedia(viewport, 0.60f, "clips_video_container")) {
            return InstagramScreen.REELS
        }
        // Story surfaces can overlay retained Home content. Require current viewer structure
        // (and controls or sibling drawing order when Home remains), never a selected tab.
        if (tree.hasForegroundStory(viewport) && tree.contentGapsAreSafe(InstagramScreen.STORY, viewport))
            return InstagramScreen.STORY
        if (tree.truncated && !tree.contentGapsAreSafe(InstagramScreen.MESSAGES, viewport))
            return InstagramScreen.UNKNOWN
        if (tree.hasOnScreenId(viewport, "row_feed_profile_header", "feed_recycler_view")) {
            if (mode == InstagramProtectionMode.SOCIAL &&
                tree.hasExactOnScreenId(viewport, "clips_video_container", "clips_media_component",
                    "clips_single_media_component")) return InstagramScreen.REELS
            if (!tree.truncated && mode == InstagramProtectionMode.SOCIAL &&
                tree.hasVisibleContentId(viewport, 0.30f, "feed_recycler_view")) return InstagramScreen.HOME_POSTS
            return InstagramScreen.HOME
        }
        if (tree.hasVisibleContentId(viewport, 0.30f, "explore_grid", "explore_recycler_view")) {
            return InstagramScreen.EXPLORE
        }
        if ((tree.hasConversation(viewport) || tree.hasExactContentId(viewport, 0.30f,
                "direct_thread", "direct_inbox", "inbox_refreshable_thread_list_recyclerview")) &&
            tree.contentGapsAreSafe(InstagramScreen.MESSAGES, viewport)) {
            return InstagramScreen.MESSAGES
        }
        if (tree.truncated) return InstagramScreen.UNKNOWN
        val profileHeader = tree.hasOnScreenId(viewport,
            "row_profile_header", "profile_header_full_name_above_vanity")
        val profileDetails = tree.hasOnScreenId(viewport,
            "profile_user_info_compose_view", "profile_header_follow_button", "profile_header_bio")
        if (profileHeader && profileDetails) return InstagramScreen.PROFILE

        // Bottom-tab selected flags can be stale. They name a blocked screen but never grant access.
        if (tree.hasSelectedId("feed_tab", "home_tab")) return InstagramScreen.HOME
        if (tree.hasSelectedId("clips_tab", "reels_tab")) return InstagramScreen.REELS
        if (tree.hasSelectedId("search_tab", "explore_tab")) return InstagramScreen.EXPLORE
        return InstagramScreen.UNKNOWN
    }
}
