/*
 * Copyright 2026 Scroll Guard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package app.scrollguard.services.detectors

internal data class ScoredSignals(
    val score: Int,
    val reasons: List<String>,
)

internal data class YouTubeSignals(
    val progressBar: Boolean = false,
    val playerId: Boolean = false,
    val selectedTabId: Boolean = false,
    val selectedTabLabel: Boolean = false,
    val shortsControls: Boolean = false,
    val verticalPager: Boolean = false,
    val normalPlayer: Boolean = false,
    val normalControls: Boolean = false,
)

internal fun scoreYouTube(signals: YouTubeSignals): ScoredSignals = buildScore {
    addIf(signals.progressBar, 10, "Shorts progress bar")
    addIf(signals.playerId, 7, "Shorts player identifier")
    addIf(signals.selectedTabId, 7, "Shorts tab selected")
    addIf(signals.selectedTabLabel, 6, "Shorts navigation selected")
    addIf(signals.shortsControls, 3, "Shorts control group")
    addIf(signals.verticalPager, 1, "Vertical media pager")
    subtractIf(signals.normalPlayer, 3)
    subtractIf(signals.normalControls, 3)
}

internal data class InstagramSignals(
    val selectedTabId: Boolean = false,
    val selectedTabLabel: Boolean = false,
    val viewerId: Boolean = false,
    val reelIds: Boolean = false,
    val reelsControls: Boolean = false,
    val verticalPager: Boolean = false,
    val storyViewer: Boolean = false,
    val homeTabSelected: Boolean = false,
    val normalScreenId: Boolean = false,
    val normalScreenLabel: Boolean = false,
)

internal fun scoreInstagram(signals: InstagramSignals): ScoredSignals = buildScore {
    addIf(signals.selectedTabId, 8, "Reels tab selected")
    addIf(signals.selectedTabLabel, 7, "Reels navigation selected")
    addIf(signals.viewerId, 7, "Reels viewer identifier")
    addIf(signals.reelIds, 2, "Reel interface identifiers")
    addIf(signals.reelsControls, 3, "Reels control group")
    addIf(signals.verticalPager, 1, "Vertical media pager")
    subtractIf(signals.storyViewer, 10)
    subtractIf(signals.homeTabSelected, 10)
    subtractIf(signals.normalScreenId && score < 7, 5)
    subtractIf(signals.normalScreenLabel && score < 7, 5)
}

private class ScoreBuilder {
    var score: Int = 0
        private set
    val reasons = mutableListOf<String>()

    fun addIf(condition: Boolean, points: Int, reason: String) {
        if (condition) {
            score += points
            reasons += reason
        }
    }

    fun subtractIf(condition: Boolean, points: Int) {
        if (condition) score -= points
    }
}

private fun buildScore(block: ScoreBuilder.() -> Unit): ScoredSignals {
    val builder = ScoreBuilder().apply(block)
    return ScoredSignals(builder.score.coerceAtLeast(0), builder.reasons)
}
