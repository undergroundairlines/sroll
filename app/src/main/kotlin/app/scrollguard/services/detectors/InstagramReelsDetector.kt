/*
 * Copyright 2025 Atick Faisal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.scrollguard.services.detectors

import android.content.res.Resources
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.scrollguard.models.DetectionResult
import timber.log.Timber

/**
 * Detector for Instagram Reels short-form content.
 *
 * Combines selected Reels navigation, viewer IDs, control groups, and layout shape. Instagram's
 * legacy "reel" identifiers refer to Stories, so those are explicitly excluded.
 */
class InstagramReelsDetector : ShortFormContentDetector {

    override fun getPackageName(): String = "com.instagram.android"

    override fun detect(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?,
        resources: Resources,
    ): DetectionResult {
        val eventSource = runCatching { event.source }.getOrNull()
        val instagramRoots = listOfNotNull(eventSource, rootNode).filter { node ->
            node.packageName?.toString() == getPackageName() &&
                (rootNode == null || node.windowId == rootNode.windowId)
        }.distinct()
        if (instagramRoots.isEmpty()) {
            return DetectionResult(getPackageName(), 0, 7, listOf("Waiting for Instagram interface"))
        }
        // The event source often contains controls for a Home-feed Reel that Instagram omits
        // from the full window tree. Merge both, while rejecting Android's overview window.
        val tree = AccessibilityTreeSnapshot.from(*instagramRoots.toTypedArray())
        return detectTree(tree, resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
    }

    internal fun detectRoot(rootNode: AccessibilityNodeInfo, resources: Resources): DetectionResult =
        detectTree(AccessibilityTreeSnapshot.from(rootNode), resources.displayMetrics.widthPixels,
            resources.displayMetrics.heightPixels)

    internal fun detectTree(tree: AccessibilityTreeSnapshot, screenWidth: Int, screenHeight: Int): DetectionResult {
        val controlCount = tree.labelGroupCount(
            setOf("comment", "comments"),
            setOf("send", "share"),
            setOf("audio", "use audio", "original audio"),
            setOf("remix"),
        )
        val selectedHome = tree.hasSelectedId("feed_tab", "home_tab") ||
            tree.hasSelectedLabel("home")
        val storyViewer = tree.hasVisibleId("story_viewer", "stories_viewer", "reel_viewer")
        val profileScreen = tree.hasVisibleId(
            "row_profile_header",
            "profile_tab_layout",
            "profile_tab_icon_view",
            "profile_user_info",
            "profile_header_follow",
        )
        // Instagram exposes this author row only when media is rendered as a card in Home.
        // Do not treat its `profile_header` suffix as the user's actual profile screen.
        val homeFeedContext = tree.hasVisibleId("row_feed_profile_header")
        val directScreen = tree.hasVisibleId("direct_inbox", "direct_thread", "inbox_refreshable_thread_list")
        val reelIds = tree.hasVisibleId("clips_", "reels_")
        val verticalPager = tree.hasTallScrollableNode()
        val reelBounds = tree.homeReelMediaBounds(screenWidth, screenHeight)
        // Preloaded Reel controls can appear in the accessibility tree while a normal post is
        // showing. The user's Home capture also reports Reels navigation selected. A feed
        // author row plus an on-screen Reel video is stronger evidence than that tab flag.
        val homeFeedReel = (selectedHome || homeFeedContext) && !profileScreen && !storyViewer &&
            !directScreen && reelBounds != null
        val scored = scoreInstagram(
            InstagramSignals(
                selectedTabId = tree.hasSelectedId("clips_tab", "reels_tab"),
                selectedTabLabel = tree.hasSelectedLabel("reels"),
                viewerId = tree.hasVisibleId("clips_viewer", "reels_viewer"),
                reelIds = reelIds,
                reelsControls = controlCount >= 3,
                verticalPager = verticalPager,
                storyViewer = storyViewer,
                homeTabSelected = selectedHome,
                normalScreenId = tree.hasVisibleId(
                    "direct_inbox",
                    "inbox",
                    "profile_tab",
                    "feed_tab",
                    "home_tab",
                ),
                normalScreenLabel = tree.hasLabel("new message", "edit profile"),
                profileScreen = profileScreen,
                homeFeedReel = homeFeedReel,
            ),
        )

        Timber.v("[Instagram] score=${scored.score} reasons=${scored.reasons.joinToString()}")
        // Home can preload an entire Reels viewer. Never navigate Back from a normal feed
        // card just because those background controls reached the viewer score threshold.
        val guardedHome = (selectedHome || homeFeedContext) && !homeFeedReel && !profileScreen && !storyViewer
        val allowedScreen = profileScreen || storyViewer || directScreen
        return DetectionResult(
            packageName = getPackageName(),
            score = if (guardedHome || allowedScreen) 0 else scored.score,
            threshold = 7,
            reasons = when {
                allowedScreen -> listOf("Profile, Story or message screen allowed")
                guardedHome -> listOf("Home feed: no visible Reel video")
                tree.truncated -> scored.reasons + "Interface scan reached its limit"
                else -> scored.reasons
            },
            identifiers = tree.diagnosticIdentifiers(),
            action = if (homeFeedReel) {
                app.scrollguard.models.BlockAction.SKIP_REEL
            } else {
                app.scrollguard.models.BlockAction.BACK
            },
            reelBounds = if (homeFeedReel) reelBounds else null,
        )
    }
}
