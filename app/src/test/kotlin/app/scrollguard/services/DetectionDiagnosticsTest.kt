/*
 * Copyright 2026 Scroll Guard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package app.scrollguard.services

import app.scrollguard.models.BlockAction
import app.scrollguard.models.DetectionActionStatus
import app.scrollguard.models.DetectionResult
import app.scrollguard.models.VideoCoverStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DetectionDiagnosticsTest {
    @Test
    fun coverFailureIsNotOverwrittenBySuccessfulScrollRequest() {
        DetectionDiagnostics.clear()
        DetectionDiagnostics.report(DetectionResult("com.instagram.android", 8, 7,
            listOf("Visible Home Reel"), action = BlockAction.SKIP_REEL))
        DetectionDiagnostics.reportVideoCoverStatus("com.instagram.android", VideoCoverStatus.FAILED)
        DetectionDiagnostics.reportActionStatus("com.instagram.android", BlockAction.SKIP_REEL,
            DetectionActionStatus.FEED_SCROLL_SENT)
        val record = DetectionDiagnostics.records.value.getValue("com.instagram.android")
        assertEquals(VideoCoverStatus.FAILED, record.videoCoverStatus)
        assertEquals(DetectionActionStatus.FEED_SCROLL_SENT, record.actionStatus)
    }

    @Test
    fun newHomeActionReplacesOlderHigherViewerScore() {
        DetectionDiagnostics.clear()
        DetectionDiagnostics.report(DetectionResult("com.instagram.android", 20, 7,
            listOf("Viewer"), action = BlockAction.BACK))
        DetectionDiagnostics.report(DetectionResult("com.instagram.android", 8, 7,
            listOf("Home"), action = BlockAction.SKIP_REEL))
        DetectionDiagnostics.reportVideoCoverStatus("com.instagram.android", VideoCoverStatus.ADDED)
        assertEquals(VideoCoverStatus.ADDED,
            DetectionDiagnostics.records.value.getValue("com.instagram.android").videoCoverStatus)
    }

    @Test
    fun recordsBlockingDecisionAndActionOutcome() {
        DetectionDiagnostics.clear()
        DetectionDiagnostics.report(
            DetectionResult(
                packageName = "com.instagram.android",
                score = 20,
                threshold = 7,
                reasons = listOf("Reel card in Home feed"),
                identifiers = listOf("row_feed_profile_header"),
                action = BlockAction.SKIP_REEL,
            ),
        )

        var record = DetectionDiagnostics.records.value.getValue("com.instagram.android")
        assertEquals(BlockAction.SKIP_REEL, record.action)
        assertEquals(DetectionActionStatus.READY, record.actionStatus)

        DetectionDiagnostics.reportActionStatus(
            "com.instagram.android",
            BlockAction.SKIP_REEL,
            DetectionActionStatus.PERFORMED,
        )

        record = DetectionDiagnostics.records.value.getValue("com.instagram.android")
        assertEquals(DetectionActionStatus.PERFORMED, record.actionStatus)

        DetectionDiagnostics.report(
            DetectionResult(
                packageName = "com.instagram.android",
                score = 20,
                threshold = 7,
                reasons = listOf("Reel card in Home feed"),
                action = BlockAction.SKIP_REEL,
            ),
        )
        DetectionDiagnostics.reportActionStatus(
            "com.instagram.android",
            BlockAction.SKIP_REEL,
            DetectionActionStatus.COOLDOWN,
        )
        assertEquals(
            DetectionActionStatus.PERFORMED,
            DetectionDiagnostics.records.value.getValue("com.instagram.android").actionStatus,
        )
    }

    @Test
    fun recordsWhenSignalsDoNotReachThreshold() {
        DetectionDiagnostics.clear()
        DetectionDiagnostics.report(
            DetectionResult(
                packageName = "com.instagram.android",
                score = 1,
                threshold = 7,
                reasons = listOf("Vertical media pager"),
                action = BlockAction.SKIP_REEL,
            ),
        )

        val record = DetectionDiagnostics.records.value.getValue("com.instagram.android")
        assertEquals(DetectionActionStatus.BELOW_THRESHOLD, record.actionStatus)
    }
}
