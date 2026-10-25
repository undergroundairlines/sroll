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

/** Latest privacy-safe, in-memory detector sample per app. Outcomes belong only to that sample. */
object DetectionDiagnostics {
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
        // A lower score or repeated screen is still a newer observation. Never carry
        // attachment/action success from a previous screen into the current sample.
        _records.update { current -> current + (result.packageName to record) }
    }

    fun reportActionStatus(
        packageName: String,
        action: BlockAction,
        status: DetectionActionStatus,
    ) {
        _records.update { current ->
            val previous = current[packageName] ?: return@update current
            if (previous.action != action || previous.score < previous.threshold) return@update current
            current + (packageName to previous.copy(actionStatus = status))
        }
    }

    fun clear() {
        _records.value = emptyMap()
    }

    fun reportVideoCoverStatus(packageName: String, status: VideoCoverStatus) {
        _records.update { current ->
            val previous = current[packageName] ?: return@update current
            if (previous.action != BlockAction.SKIP_REEL || previous.score < previous.threshold) return@update current
            current + (packageName to previous.copy(videoCoverStatus = status))
        }
    }
}
