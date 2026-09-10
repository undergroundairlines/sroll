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
 * Combines selected Reels navigation, viewer IDs, control groups, and layout shape. Normal inbox
 * and profile signals lower weak scores to avoid the original detector's fullscreen false positives.
 */
class InstagramReelsDetector : ShortFormContentDetector {

    override fun getPackageName(): String = "com.instagram.android"

    override fun detect(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?,
        resources: Resources,
    ): DetectionResult {
        if (rootNode == null) {
            return DetectionResult(getPackageName(), 0, 7, listOf("Waiting for Instagram interface"))
        }
        val tree = AccessibilityTreeSnapshot.from(rootNode)
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
                viewerId = tree.hasId("clips_viewer", "reels_viewer", "reel_viewer"),
                reelIds = tree.hasId("clips_", "reel_"),
                reelsControls = controlCount >= 3,
                verticalPager = tree.hasTallScrollableNode(),
                normalScreenId = tree.hasId("direct_inbox", "inbox", "profile_tab"),
                normalScreenLabel = tree.hasLabel("new message", "edit profile"),
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
