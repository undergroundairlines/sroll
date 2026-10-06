/*
 * Copyright 2026 Scroll Guard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package app.scrollguard.services

import app.scrollguard.models.BlockAction
import app.scrollguard.models.DetectionActionStatus
import app.scrollguard.models.DetectionDiagnostic
import app.scrollguard.models.DetectionResult
import app.scrollguard.models.VideoCoverStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** In-memory, privacy-safe detector output. It never stores captions or account names. */
object DetectionDiagnostics {
    private const val SAMPLE_WINDOW_MILLIS = 60_000L

    private val _records = MutableStateFlow<Map<String, DetectionDiagnostic>>(emptyMap())
    val records = _records.asStateFlow()

    fun report(result: DetectionResult) {
        val now = System.currentTimeMillis()
        val record = DetectionDiagnostic(
            packageName = result.packageName,
            score = result.score,
            threshold = result.threshold,
            reasons = result.reasons,
            identifiers = result.identifiers,
            timestampMillis = now,
            action = result.action,
            actionStatus = if (result.shouldBlock) {
                DetectionActionStatus.READY
            } else {
                DetectionActionStatus.BELOW_THRESHOLD
            },
        )
        _records.update { current ->
            val previous = current[result.packageName]
            if (
                previous == null ||
                result.action == BlockAction.LOCK_FEED ||
                now - previous.timestampMillis > SAMPLE_WINDOW_MILLIS ||
                record.score >= previous.score ||
                (result.shouldBlock && result.action != previous.action)
            ) {
                val priorOutcome = previous?.actionStatus
                val preserveOutcome = result.action != BlockAction.LOCK_FEED && previous != null &&
                    now - previous.timestampMillis <= SAMPLE_WINDOW_MILLIS &&
                    previous.action == result.action &&
                    priorOutcome != null && isCompletedOutcome(priorOutcome)
                current + (result.packageName to if (preserveOutcome) {
                    record.copy(actionStatus = priorOutcome ?: record.actionStatus,
                        videoCoverStatus = previous?.videoCoverStatus ?: VideoCoverStatus.NONE)
                } else {
                    record
                })
            } else {
                current
            }
        }
    }

    fun reportActionStatus(
        packageName: String,
        action: BlockAction,
        status: DetectionActionStatus,
    ) {
        _records.update { current ->
            val previous = current[packageName] ?: return@update current
            if (previous.action != action) return@update current
            if (status == DetectionActionStatus.COOLDOWN &&
                isCompletedOutcome(previous.actionStatus)
            ) return@update current
            current + (packageName to previous.copy(actionStatus = status))
        }
    }

    fun clear() {
        _records.value = emptyMap()
    }

    fun reportVideoCoverStatus(packageName: String, status: VideoCoverStatus) {
        _records.update { current ->
            val previous = current[packageName] ?: return@update current
            if (previous.action != BlockAction.SKIP_REEL) return@update current
            current + (packageName to previous.copy(videoCoverStatus = status))
        }
    }

    private fun isCompletedOutcome(status: DetectionActionStatus): Boolean =
        status == DetectionActionStatus.PERFORMED ||
            status == DetectionActionStatus.TOUCH_BLOCKED ||
            status == DetectionActionStatus.FEED_SCROLL_SENT ||
            status == DetectionActionStatus.FAILED
}
