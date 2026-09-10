/*
 * Copyright 2025 Atick Faisal
 * Modifications copyright 2026 Scroll Guard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package app.scrollguard.models

enum class BlockAction {
    BACK,
    HOME,
}

data class DetectionResult(
    val packageName: String,
    val score: Int,
    val threshold: Int,
    val reasons: List<String>,
    val identifiers: List<String> = emptyList(),
    val action: BlockAction = BlockAction.BACK,
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
)
