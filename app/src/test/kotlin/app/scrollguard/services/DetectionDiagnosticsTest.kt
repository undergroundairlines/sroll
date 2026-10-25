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
    @Test fun safeScreenReplacesOldLockOutcome() {
        DetectionDiagnostics.clear()
        DetectionDiagnostics.report(DetectionResult("com.instagram.android", 1, 1,
            listOf("Home feed locked"), action = BlockAction.LOCK_FEED))
        DetectionDiagnostics.reportActionStatus("com.instagram.android", BlockAction.LOCK_FEED,
            DetectionActionStatus.TOUCH_BLOCKED)
        DetectionDiagnostics.report(DetectionResult("com.instagram.android", 0, 1,
            listOf("Messages allowed"), action = BlockAction.LOCK_FEED))
        val record = DetectionDiagnostics.records.value.getValue("com.instagram.android")
        assertEquals(DetectionActionStatus.BELOW_THRESHOLD, record.actionStatus)
        assertEquals(listOf("Messages allowed"), record.reasons)
    }

    @Test fun attachmentFailureCannotKeepAnEarlierSuccess() {
        DetectionDiagnostics.clear()
        val result = DetectionResult("com.instagram.android", 1, 1,
            listOf("Home feed locked"), action = BlockAction.LOCK_FEED)
        DetectionDiagnostics.report(result)
        DetectionDiagnostics.reportActionStatus(result.packageName, result.action, DetectionActionStatus.TOUCH_BLOCKED)
        DetectionDiagnostics.report(result)
        assertEquals(DetectionActionStatus.READY,
            DetectionDiagnostics.records.value.getValue(result.packageName).actionStatus)
        DetectionDiagnostics.reportActionStatus(result.packageName, result.action, DetectionActionStatus.FAILED)
        assertEquals(DetectionActionStatus.FAILED,
            DetectionDiagnostics.records.value.getValue(result.packageName).actionStatus)
    }
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
            DetectionActionStatus.COOLDOWN,
            DetectionDiagnostics.records.value.getValue("com.instagram.android").actionStatus,
        )
    }

    @Test fun lowerScoreOnTheSameActionReplacesAnEarlierBlockingResult() {
        DetectionDiagnostics.clear()
        val packageName = "com.google.android.youtube"
        DetectionDiagnostics.report(DetectionResult(packageName, 20, 7,
            listOf("Shorts viewer"), listOf("reel_watch_fragment"), BlockAction.BACK))
        DetectionDiagnostics.reportActionStatus(packageName, BlockAction.BACK, DetectionActionStatus.PERFORMED)
        DetectionDiagnostics.report(DetectionResult(packageName, 0, 7,
            listOf("Ordinary video"), listOf("watch_player"), BlockAction.BACK))
        val record = DetectionDiagnostics.records.value.getValue(packageName)
        assertEquals(0, record.score)
        assertEquals(listOf("Ordinary video"), record.reasons)
        assertEquals(listOf("watch_player"), record.identifiers)
        assertEquals(DetectionActionStatus.BELOW_THRESHOLD, record.actionStatus)
    }

    @Test fun aNewSampleDoesNotRetainOldActionOrCoverOutcomes() {
        val packageName = "com.instagram.android"
        for (oldStatus in listOf(DetectionActionStatus.PERFORMED, DetectionActionStatus.TOUCH_BLOCKED,
            DetectionActionStatus.FEED_SCROLL_SENT, DetectionActionStatus.FAILED)) {
            DetectionDiagnostics.clear()
            val result = DetectionResult(packageName, 8, 7,
                listOf("Visible Reel"), action = BlockAction.SKIP_REEL)
            DetectionDiagnostics.report(result)
            DetectionDiagnostics.reportActionStatus(packageName, result.action, oldStatus)
            DetectionDiagnostics.reportVideoCoverStatus(packageName, VideoCoverStatus.ADDED)
            DetectionDiagnostics.report(result)
            val record = DetectionDiagnostics.records.value.getValue(packageName)
            assertEquals(DetectionActionStatus.READY, record.actionStatus)
            assertEquals(VideoCoverStatus.NONE, record.videoCoverStatus)
        }
    }

    @Test fun aStatusForAnOldActionCannotReplaceTheLatestAction() {
        DetectionDiagnostics.clear()
        val packageName = "com.instagram.android"
        DetectionDiagnostics.report(DetectionResult(packageName, 8, 7,
            listOf("Old action"), action = BlockAction.SKIP_REEL))
        DetectionDiagnostics.report(DetectionResult(packageName, 1, 1,
            listOf("Current lock"), action = BlockAction.LOCK_FEED))
        DetectionDiagnostics.reportActionStatus(packageName, BlockAction.SKIP_REEL, DetectionActionStatus.PERFORMED)
        DetectionDiagnostics.reportVideoCoverStatus(packageName, VideoCoverStatus.ADDED)
        val record = DetectionDiagnostics.records.value.getValue(packageName)
        assertEquals(BlockAction.LOCK_FEED, record.action)
        assertEquals(DetectionActionStatus.READY, record.actionStatus)
        assertEquals(VideoCoverStatus.NONE, record.videoCoverStatus)
    }

    @Test fun aBelowThresholdSampleCannotAcquireAnOldBlockingOutcome() {
        DetectionDiagnostics.clear()
        val packageName = "com.instagram.android"
        DetectionDiagnostics.report(DetectionResult(packageName, 0, 7,
            listOf("No visible Reel"), action = BlockAction.SKIP_REEL))
        DetectionDiagnostics.reportActionStatus(packageName, BlockAction.SKIP_REEL, DetectionActionStatus.PERFORMED)
        DetectionDiagnostics.reportVideoCoverStatus(packageName, VideoCoverStatus.ADDED)
        val record = DetectionDiagnostics.records.value.getValue(packageName)
        assertEquals(DetectionActionStatus.BELOW_THRESHOLD, record.actionStatus)
        assertEquals(VideoCoverStatus.NONE, record.videoCoverStatus)
    }

    @Test fun cooldownAlwaysDescribesTheCurrentAttemptEvenAfterSuccess() {
        DetectionDiagnostics.clear()
        val packageName = "com.google.android.youtube"
        DetectionDiagnostics.report(DetectionResult(packageName, 20, 7,
            listOf("Shorts viewer"), action = BlockAction.BACK))
        DetectionDiagnostics.reportActionStatus(packageName, BlockAction.BACK, DetectionActionStatus.PERFORMED)
        DetectionDiagnostics.reportActionStatus(packageName, BlockAction.BACK, DetectionActionStatus.COOLDOWN)
        assertEquals(DetectionActionStatus.COOLDOWN,
            DetectionDiagnostics.records.value.getValue(packageName).actionStatus)
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
