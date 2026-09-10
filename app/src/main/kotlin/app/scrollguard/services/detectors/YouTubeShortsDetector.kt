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
 * Detector for YouTube Shorts short-form content.
 *
 * Combines player IDs, selected navigation state, visible controls, and layout shape. Strong
 * normal-video signals reduce the score so ordinary videos are not blocked.
 */
class YouTubeShortsDetector : ShortFormContentDetector {

    override fun getPackageName(): String = "com.google.android.youtube"

    override fun detect(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?,
        resources: Resources,
    ): DetectionResult {
        if (rootNode == null) {
            return DetectionResult(getPackageName(), 0, 6, listOf("Waiting for YouTube interface"))
        }
        val tree = AccessibilityTreeSnapshot.from(rootNode)
        val controlCount = tree.labelGroupCount(
            setOf("comments", "comment"),
            setOf("remix", "use this sound", "sound"),
            setOf("dislike"),
            setOf("share"),
        )
        val scored = scoreYouTube(
            YouTubeSignals(
                progressBar = tree.hasId("reel_progress_bar"),
                playerId = tree.hasId("shorts_player", "reel_watch", "reel_player"),
                selectedTabId = tree.hasSelectedId("shorts_tab", "pivot_shorts"),
                selectedTabLabel = tree.hasSelectedLabel("shorts"),
                shortsControls = controlCount >= 3,
                verticalPager = tree.hasTallScrollableNode(),
                normalPlayer = tree.hasId("watch_player", "movie_player"),
                normalControls = tree.hasLabel("enter full screen", "chapters"),
            ),
        )

        Timber.v("[YouTube] score=${scored.score} reasons=${scored.reasons.joinToString()}")
        return DetectionResult(
            packageName = getPackageName(),
            score = scored.score,
            threshold = 6,
            reasons = scored.reasons,
            identifiers = tree.diagnosticIdentifiers(),
        )
    }
}
