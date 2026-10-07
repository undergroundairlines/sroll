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
    fun evaluate(
        mode: InstagramProtectionMode,
        tree: AccessibilityTreeSnapshot?,
        viewport: MediaBounds,
    ): InstagramScreen {
        if (mode == InstagramProtectionMode.APP_LOCK) return InstagramScreen.APP_LOCK
        if (tree == null || tree.truncated) return InstagramScreen.UNKNOWN

        // A visible video/viewer wins over cached profile or inbox nodes. A small preview does
        // not count as a viewer, but the Home feed remains blocked even without any Reel IDs.
        if (tree.hasVisibleContentId(viewport, 0.40f,
                "clips_viewer_view_pager", "reels_viewer") ||
            tree.hasVisibleContentId(viewport, 0.60f, "clips_video_container")) {
            return InstagramScreen.REELS
        }
        if (tree.hasOnScreenId(viewport, "row_feed_profile_header", "feed_recycler_view")) {
            if (mode == InstagramProtectionMode.SOCIAL &&
                tree.hasExactOnScreenId(viewport, "clips_video_container", "clips_media_component",
                    "clips_single_media_component")) return InstagramScreen.REELS
            if (mode == InstagramProtectionMode.SOCIAL &&
                tree.hasVisibleContentId(viewport, 0.30f, "feed_recycler_view")) return InstagramScreen.HOME_POSTS
            return InstagramScreen.HOME
        }
        if (tree.hasVisibleContentId(viewport, 0.30f, "explore_grid", "explore_recycler_view")) {
            return InstagramScreen.EXPLORE
        }
        if (tree.hasConversation(viewport) || tree.hasExactContentId(viewport, 0.30f,
                "direct_thread", "direct_inbox", "inbox_refreshable_thread_list_recyclerview")) {
            return InstagramScreen.MESSAGES
        }
        if (tree.hasVisibleContentId(viewport, 0.40f,
                "story_viewer", "stories_viewer", "reel_viewer")) {
            return InstagramScreen.STORY
        }
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
