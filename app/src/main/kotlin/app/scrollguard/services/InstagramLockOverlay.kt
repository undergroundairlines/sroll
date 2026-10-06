/* Copyright 2026 Scroll Guard contributors. Licensed under the Apache License, Version 2.0. */
package app.scrollguard.services

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.scrollguard.R
import app.scrollguard.models.MediaBounds
import timber.log.Timber

internal enum class InstagramDestination { MESSAGES, PROFILE }
internal data class InstagramLockPanel(
    val bounds: MediaBounds,
    val wholeApp: Boolean,
    val canOpenMessages: Boolean,
    val canOpenProfile: Boolean,
)

/** A touchable full-window shield. No touches or swipes pass through to the feed. */
internal class InstagramLockOverlay(
    private val context: Context,
    private val onNavigate: (InstagramDestination) -> Unit,
    private val onLeave: () -> Unit,
) {
    private val windows = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null
    private var lastPanel: InstagramLockPanel? = null
    private var title: TextView? = null
    private var explanation: TextView? = null
    private var messages: Button? = null
    private var profile: Button? = null
    val isShowing: Boolean get() = view != null

    fun show(panel: InstagramLockPanel): Boolean {
        if (panel.bounds.width <= 0 || panel.bounds.height <= 0) return false
        if (view != null && lastPanel == panel) return true
        val root = view ?: buildView()
        val rect = panel.bounds
        val params = WindowManager.LayoutParams(
            rect.width, rect.height, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = rect.left
            y = rect.top
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            // No FLAG_NOT_TOUCHABLE: that was the old cover's scrolling bypass.
        }
        return runCatching {
            updateLabels(panel)
            if (view == null) windows.addView(root, params)
            else if (lastPanel?.bounds != panel.bounds) windows.updateViewLayout(root, params)
            view = root
            lastPanel = panel
            true
        }.getOrElse {
            Timber.e(it, "Instagram touch shield could not attach")
            hide()
            false
        }
    }

    fun navigationFailed() {
        explanation?.text = "Couldn't open that screen. Your feed stays locked.\nYou can leave Instagram."
    }

    fun hide() {
        view?.let { runCatching { windows.removeViewImmediate(it) } }
        view = null
        lastPanel = null
        title = null
        explanation = null
        messages = null
        profile = null
    }

    private fun updateLabels(panel: InstagramLockPanel) {
        title?.text = if (panel.wholeApp) "Instagram is locked" else "Your feed is locked"
        explanation?.text = if (panel.wholeApp) {
            "You chose to block the whole app. Your time is yours."
        } else {
            "Home, Reels and Explore are off limits.\nOpen messages or your profile with a purpose."
        }
        messages?.visibility = if (panel.wholeApp) View.GONE else View.VISIBLE
        profile?.visibility = if (panel.wholeApp) View.GONE else View.VISIBLE
        messages?.isEnabled = panel.canOpenMessages
        profile?.isEnabled = panel.canOpenProfile
        messages?.alpha = if (panel.canOpenMessages) 1f else 0.4f
        profile?.alpha = if (panel.canOpenProfile) 1f else 0.4f
    }

    private fun buildView(): View {
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.rgb(12, 12, 12))
            isClickable = true
        }
        // Register a minimal touch-catching window before cold-start TextView/Button work.
        root.post {
            if (view === root) buildContent(root)
        }
        return root
    }

    private fun buildContent(root: FrameLayout) {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(64), dp(28), dp(64))
        }
        fun label(value: String, size: Float, color: Int) = TextView(context).apply {
            text = value
            textSize = size
            gravity = Gravity.CENTER
            setTextColor(color)
            setPadding(0, dp(10), 0, dp(10))
        }
        content.addView(label("SCROLL GUARD", 13f, Color.rgb(255, 75, 80)).apply {
            letterSpacing = 0.2f
            setTypeface(null, Typeface.BOLD)
        })
        title = label("Your feed is locked", 30f, Color.WHITE).apply {
            id = R.id.guard_title
            setTypeface(null, Typeface.BOLD)
        }.also { content.addView(it) }
        explanation = label("", 17f, Color.rgb(190, 190, 190)).also { content.addView(it) }
        fun button(value: String, viewId: Int, primary: Boolean, action: () -> Unit): Button {
            val button = Button(context).apply {
                id = viewId
                text = value
                isAllCaps = false
                textSize = 16f
                minHeight = dp(52)
                setTextColor(if (primary) Color.BLACK else Color.WHITE)
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(if (primary) Color.rgb(255, 75, 80) else Color.rgb(38, 38, 38))
                }
                setOnClickListener { action() }
            }
            content.addView(button, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            return button
        }
        messages = button("Open messages", R.id.guard_messages, true) { onNavigate(InstagramDestination.MESSAGES) }
        profile = button("Open my profile", R.id.guard_profile, false) { onNavigate(InstagramDestination.PROFILE) }
        button("Leave Instagram", R.id.guard_leave, false, onLeave)
        content.addView(label("The feed stays locked until you change protection in Scroll Guard.",
            12f, Color.rgb(140, 140, 140)))
        root.addView(ScrollView(context).apply {
            isFillViewport = true
            addView(content)
        }, FrameLayout.LayoutParams(-1, -1))
        lastPanel?.let(::updateLabels)
    }
}
