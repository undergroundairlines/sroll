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
        val instagramRoots = listOfNotNull(rootNode, eventSource).filter { node ->
            node.packageName?.toString() == getPackageName()
        }.distinct()
        if (instagramRoots.isEmpty()) {
            return DetectionResult(getPackageName(), 0, 7, listOf("Waiting for Instagram interface"))
        }
        // The event source often contains controls for a Home-feed Reel that Instagram omits
        // from the full window tree. Merge both, while rejecting Android's overview window.
        val tree = AccessibilityTreeSnapshot.from(*instagramRoots.toTypedArray())
        val controlCount = tree.labelGroupCount(
            setOf("comment", "comments"),
            setOf("send", "share"),
            setOf("audio", "use audio", "original audio"),
            setOf("remix"),
        )
        val scored = scoreInstagram(
            InstagramSignals(
                selectedTabId = tree.hasSelectedId("clips_tab", "reels_tab"),
                selectedTabLabel = tree.hasSelectedLabel("reels"),
                viewerId = tree.hasVisibleId("clips_viewer", "reels_viewer"),
                reelIds = tree.hasVisibleId("clips_", "reels_"),
                reelsControls = controlCount >= 3,
                verticalPager = tree.hasTallScrollableNode(),
                storyViewer = tree.hasVisibleId(
                    "story_viewer",
                    "stories_viewer",
                    "reel_viewer",
                ),
                homeTabSelected = tree.hasSelectedId("feed_tab", "home_tab") ||
                    tree.hasSelectedLabel("home"),
                normalScreenId = tree.hasVisibleId(
                    "direct_inbox",
                    "inbox",
                    "profile_tab",
                    "feed_tab",
                    "home_tab",
                ),
                normalScreenLabel = tree.hasLabel("new message", "edit profile"),
                profileScreen = tree.hasVisibleId(
                    "profile_header",
                    "row_profile_header",
                    "profile_user_info",
                    "profile_header_follow",
                ),
            ),
        )

        Timber.v("[Instagram] score=${scored.score} reasons=${scored.reasons.joinToString()}")
        return DetectionResult(
            packageName = getPackageName(),
            score = scored.score,
            threshold = 7,
            reasons = scored.reasons,
            identifiers = tree.diagnosticIdentifiers(),
        )
    }
}
