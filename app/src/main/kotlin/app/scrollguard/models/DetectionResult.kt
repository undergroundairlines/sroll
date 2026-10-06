/*
 * Copyright 2025 Atick Faisal
 * Modifications copyright 2026 Scroll Guard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package app.scrollguard.models

/** Screen coordinates; kept independent of Android so detector geometry is testable. */
data class MediaBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    fun intersect(other: MediaBounds): MediaBounds? {
        val clipped = MediaBounds(maxOf(left, other.left), maxOf(top, other.top),
            minOf(right, other.right), minOf(bottom, other.bottom))
        return clipped.takeIf { it.width > 0 && it.height > 0 }
    }
}

enum class VideoCoverStatus { NONE, ADDED, FAILED }

enum class BlockAction {
    BACK,
    HOME,
    SKIP_REEL,
    LOCK_FEED,
}

enum class DetectionActionStatus {
    BELOW_THRESHOLD,
    READY,
    COOLDOWN,
    FEED_SCROLL_SENT,
    PERFORMED,
    FAILED,
    TOUCH_BLOCKED,
}

data class DetectionResult(
    val packageName: String,
    val score: Int,
    val threshold: Int,
    val reasons: List<String>,
    val identifiers: List<String> = emptyList(),
    val action: BlockAction = BlockAction.BACK,
    val reelBounds: MediaBounds? = null,
) {
    val shouldBlock: Boolean
        get() = score >= threshold
}

data class DetectionDiagnostic(
    val packageName: String,
    val score: Int,
    val threshold: Int,
    val reasons: List<String>,
    val identifiers: List<String>,
    val timestampMillis: Long,
    val action: BlockAction,
    val actionStatus: DetectionActionStatus,
    val videoCoverStatus: VideoCoverStatus = VideoCoverStatus.NONE,
)
