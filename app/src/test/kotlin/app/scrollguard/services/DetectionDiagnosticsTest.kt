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
import org.junit.Assert.assertEquals
import org.junit.Test

class DetectionDiagnosticsTest {
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
            DetectionActionStatus.PERFORMED,
        )

        record = DetectionDiagnostics.records.value.getValue("com.instagram.android")
        assertEquals(DetectionActionStatus.PERFORMED, record.actionStatus)
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
