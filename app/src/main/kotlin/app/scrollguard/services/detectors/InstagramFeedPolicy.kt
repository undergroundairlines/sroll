/* Copyright 2026 Scroll Guard contributors. Licensed under the Apache License, Version 2.0. */
package app.scrollguard.services.detectors

import app.scrollguard.models.InstagramProtectionMode
import app.scrollguard.models.MediaBounds

internal enum class InstagramScreen(val description: String, val allowed: Boolean = false) {
    HOME("Home feed locked"),
    HOME_POSTS("Home posts allowed — selective detection is best effort", true),
    HOME_REEL("Home video/Reel locked"),
    HOME_MEDIA_UNKNOWN("Unlabelled Home media locked"),
    REELS("Reels locked"),
    EXPLORE("Explore locked"),
    PROFILE("Profile allowed", true),
    MESSAGES("Messages allowed", true),
    STORY("Story allowed", true),
    OTHER_ALLOWED("Other Instagram screen allowed — selective mode", true),
    UNKNOWN("Unrecognised screen locked"),
    APP_LOCK("Instagram blocked completely"),
}

/** Feed lock uses an allowlist; selective mode blocks only positively identified feed surfaces. */
internal object InstagramFeedPolicy {
    fun reason(screen: InstagramScreen, tree: AccessibilityTreeSnapshot?, viewport: MediaBounds): String = when (screen) {
        InstagramScreen.MESSAGES -> if (tree?.hasConversation(viewport) == true)
            "Visible message history and editable composer in one content subtree"
            else "Large visible exact inbox/thread container"
        InstagramScreen.PROFILE -> "Visible profile header and separate profile details"
        InstagramScreen.STORY -> "Large current Story viewer; underlying Home is not the foreground screen"
        InstagramScreen.HOME -> "Home content or selected Home navigation; entire Home is locked"
        InstagramScreen.HOME_POSTS -> "Large visible Home container; no recognised on-screen Reel media (best effort)"
        InstagramScreen.HOME_REEL -> tree?.homeVideos(viewport)?.joinToString("; ") { "${it.reason}; bounds=${it.bounds}" }
            ?: "Current root unavailable; remembered Home shield retained pending a new classification"
        InstagramScreen.HOME_MEDIA_UNKNOWN -> "Conservative Home fallback: media type is unlabelled; guard remains until this render node is no longer visible"
        InstagramScreen.REELS -> "Visible Reel viewer or recognised Home Reel media"
        InstagramScreen.EXPLORE -> "Visible Explore content or selected Explore navigation"
        InstagramScreen.APP_LOCK -> "Whole-app preference; no interface exceptions"
        InstagramScreen.OTHER_ALLOWED -> "Selective mode: no positively identified Reels or Explore; unknown screens remain usable, including photo selection/editing"
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
        rememberedHomeMedia: Set<Int> = emptySet(),
    ): InstagramScreen {
        if (mode == InstagramProtectionMode.APP_LOCK) return InstagramScreen.APP_LOCK
        if (mode == InstagramProtectionMode.SOCIAL) return selective(tree, viewport, rememberedHomeMedia)
        if (tree == null) return InstagramScreen.UNKNOWN

        // A visible video/viewer wins over cached profile or inbox nodes. A small preview does
        // not count as a viewer, but the Home feed remains blocked even without any Reel IDs.
        if (tree.hasExternalMedia(viewport, 0.40f,
                "clips_viewer_view_pager", "reels_viewer", allowInlineMediaPreview = false) ||
            tree.hasExternalMedia(viewport, 0.60f, "clips_video_container")) {
            return InstagramScreen.REELS
        }
        // Story surfaces can overlay retained Home content. Require current viewer structure
        // (and controls or sibling drawing order when Home remains), never a selected tab.
        if (tree.hasForegroundStory(viewport) && tree.contentGapsAreSafe(InstagramScreen.STORY, viewport))
            return InstagramScreen.STORY
        if (tree.truncated && !tree.contentGapsAreSafe(InstagramScreen.MESSAGES, viewport))
            return InstagramScreen.UNKNOWN
        if (tree.hasHomeContent(viewport)) {
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

    private fun selective(tree: AccessibilityTreeSnapshot?, viewport: MediaBounds, remembered: Set<Int>): InstagramScreen {
        // A gallery/camera/editor can replace every chat anchor and use an embedded surface.
        // Selective mode must not turn absence of private UI identifiers into an app-wide lock.
        // This is deliberately weaker than feed lock, including when a root is unavailable.
        tree ?: return InstagramScreen.OTHER_ALLOWED
        val identified = evaluate(InstagramProtectionMode.FEED_LOCK, tree, viewport)
        if (identified in setOf(InstagramScreen.MESSAGES, InstagramScreen.STORY, InstagramScreen.PROFILE))
            return identified
        if (tree.hasExternalMedia(viewport, 0.40f, "clips_viewer_view_pager", "reels_viewer",
                allowInlineMediaPreview = false)) return InstagramScreen.REELS
        val homeMedia = tree.homeVideos(viewport, remembered)
        if (homeMedia.any { it.confirmedVideo }) return InstagramScreen.HOME_REEL
        if (homeMedia.isNotEmpty()) return InstagramScreen.HOME_MEDIA_UNKNOWN
        if (tree.hasExternalMedia(viewport, 0.60f, "clips_video_container") ||
            (tree.hasHomeContent(viewport) && tree.hasExactOnScreenId(viewport,
                "clips_video_container", "clips_media_component", "clips_single_media_component")))
            return InstagramScreen.REELS
        if (tree.hasVisibleContentId(viewport, 0.30f, "explore_grid", "explore_recycler_view"))
            return InstagramScreen.EXPLORE
        if (tree.hasHomeContent(viewport)) return InstagramScreen.HOME_POSTS
        // Selected tabs are commonly retained behind a gallery or camera. They are not proof
        // the feed is currently displayed. Missing/truncated trees also cannot prove that.
        return InstagramScreen.OTHER_ALLOWED
    }
}
