/*
 * Copyright 2026 Scroll Guard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package app.scrollguard.services

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import app.scrollguard.models.MediaBounds
import timber.log.Timber

/** Covers only a detected Reel's video surface. Touches pass through to Instagram's feed. */
internal class HomeReelMask(private val context: Context) {
    private val windows = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null
    private var bounds: MediaBounds? = null

    fun show(rect: MediaBounds): Boolean {
        if (rect.width <= 0 || rect.height <= 0) return false
        if (view != null && bounds == rect) return true
        val panel = view ?: FrameLayout(context).apply {
            setBackgroundColor(Color.rgb(24, 24, 24))
            addView(TextView(context).apply {
                text = "Reel hidden\nSwipe to the next post"
                setTextColor(Color.WHITE)
                textSize = 17f
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(-1, -1))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        val params = WindowManager.LayoutParams(
            rect.width, rect.height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = rect.left
            y = rect.top
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        return runCatching {
            val current = view
            if (current == null) windows.addView(panel, params)
            else windows.updateViewLayout(current, params)
            view = current ?: panel
            bounds = rect
            true
        }.getOrElse { error ->
            Timber.w(error, "Could not add or update the Home Reel cover")
            hide()
            false
        }
    }

    fun hide() {
        view?.let { runCatching { windows.removeViewImmediate(it) } }
        view = null
        bounds = null
    }

    val isShowing: Boolean get() = view != null
}
