/* Copyright 2026 Scroll Guard contributors. Licensed under the Apache License, Version 2.0. */
package com.instagram.android

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider

/** Synthetic unavailable virtual child, while the actual chat composer/history stay present. */
internal class TransientTreeProbe(context: Context) : View(context) {
    private val handler = Handler(Looper.getMainLooper())
    private var unavailable = false
    var changeCount = 0
        private set
    private val restore = Runnable {
        unavailable = false
        changed()
    }
    private val provider = object : AccessibilityNodeProvider() {
        @Suppress("DEPRECATION")
        override fun createAccessibilityNodeInfo(virtualViewId: Int): AccessibilityNodeInfo? {
            if (virtualViewId == HOST_VIEW_ID) return AccessibilityNodeInfo.obtain(this@TransientTreeProbe).apply {
                this@TransientTreeProbe.onInitializeAccessibilityNodeInfo(this)
                addChild(this@TransientTreeProbe, 1)
            }
            if (virtualViewId != 1 || unavailable) return null
            return AccessibilityNodeInfo.obtain().apply {
                setSource(this@TransientTreeProbe, 1)
                setParent(this@TransientTreeProbe)
                packageName = context.packageName
                className = "android.view.View"
                isVisibleToUser = isShown
                isEnabled = true
                val position = IntArray(2).also(this@TransientTreeProbe::getLocationOnScreen)
                setBoundsInScreen(Rect(position[0], position[1], position[0] + width, position[1] + height))
            }
        }
    }

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }
    override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider = provider

    fun unavailableDuringScroll() {
        handler.removeCallbacks(restore)
        unavailable = true
        changeCount++
        changed()
        handler.postDelayed(restore, 180L)
    }

    @Suppress("DEPRECATION")
    private fun changed() = sendAccessibilityEventUnchecked(
        AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED).apply {
            contentChangeTypes = AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE
        },
    )

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }
}
