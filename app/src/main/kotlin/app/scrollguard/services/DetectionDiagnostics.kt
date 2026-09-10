/*
 * Copyright 2026 Scroll Guard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package app.scrollguard.services

import app.scrollguard.models.DetectionDiagnostic
import app.scrollguard.models.DetectionResult
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
        )
        _records.update { current ->
            val previous = current[result.packageName]
            if (
                previous == null ||
                now - previous.timestampMillis > SAMPLE_WINDOW_MILLIS ||
                record.score >= previous.score
            ) {
                current + (result.packageName to record)
            } else {
                current
            }
        }
    }

    fun clear() {
        _records.value = emptyMap()
    }
}
