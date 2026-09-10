/*
 * Copyright 2026 Scroll Guard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package app.scrollguard.services.detectors

import android.content.res.Resources
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.scrollguard.models.BlockAction
import app.scrollguard.models.DetectionResult

class TikTokDetector : ShortFormContentDetector {
    override fun getPackageName(): String = "com.zhiliaoapp.musically"

    override fun detect(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?,
        resources: Resources,
    ): DetectionResult = DetectionResult(
        packageName = getPackageName(),
        score = 10,
        threshold = 1,
        reasons = listOf("TikTok opened"),
        identifiers = emptyList(),
        action = BlockAction.HOME,
    )
}
