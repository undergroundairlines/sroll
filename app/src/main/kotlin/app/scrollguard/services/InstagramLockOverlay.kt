/* Copyright 2026 Scroll Guard contributors. Licensed under the Apache License, Version 2.0. */
package app.scrollguard.services

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.SystemClock
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
    val sharedReel: Boolean = false,
    val socialMode: Boolean = false,
    val canWatchShared: Boolean = false,
    val keyboardBounds: MediaBounds? = null,
)

/** A touchable full-window shield. No touches or swipes pass through to the feed. */
internal class InstagramLockOverlay(
    private val context: Context,
    private val onNavigate: (InstagramDestination) -> Unit,
    private val onLeave: () -> Unit,
    private val onSharedBack: () -> Unit,
    private val onWatchShared: () -> Unit,
) {
    private val windows = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null
    private var lastPanel: InstagramLockPanel? = null
    private var title: TextView? = null
    private var explanation: TextView? = null
    private var messages: Button? = null
    private var profile: Button? = null
    private var watchShared: Button? = null
    private var attachRequestedAt = 0L
    private val extraShields = mutableListOf<View>()
    val isShowing: Boolean get() = view != null || extraShields.isNotEmpty()
    val isAttached: Boolean get() = view?.let { it.isAttachedToWindow && it.isShown && it.width > 0 && it.height > 0 } == true &&
        extraShields.all { it.isAttachedToWindow && it.isShown && it.width > 0 && it.height > 0 }
    var status: String = "Removed"
        private set

    fun show(panel: InstagramLockPanel): Boolean {
        if (panel.bounds.width <= 0 || panel.bounds.height <= 0) return false
        val regions = ShieldGeometry.regions(panel.bounds, panel.keyboardBounds)
        if (regions.isEmpty()) { hide(); return false }
        if (view?.visibility == View.GONE) {
            hide()
            if (isShowing) return false
        }
        if (isShowing && (lastPanel?.sharedReel != panel.sharedReel || lastPanel?.bounds != panel.bounds ||
                lastPanel?.keyboardBounds != panel.keyboardBounds)) {
            hide()
            if (isShowing) return false
        }
        if (view != null && lastPanel == panel) {
            if (!isAttached && SystemClock.uptimeMillis() - attachRequestedAt > 3000L) {
                val pending = view?.let { "attached=${it.isAttachedToWindow}, shown=${it.isShown}, size=${it.width}x${it.height}" }
                hide()
                status = "Attachment/layout failed ($pending); leaving Instagram"
                return false
            }
            status = if (isAttached) "Attached; touchable" else "Attachment/layout pending"
            return true
        }
        val root = view ?: buildView()
        val rect = regions.maxBy { it.width.toLong() * it.height }
        fun parameters(area: MediaBounds) = WindowManager.LayoutParams(
            area.width, area.height, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            if (panel.sharedReel) PixelFormat.TRANSLUCENT else PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = area.left
            y = area.top
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            // No FLAG_NOT_TOUCHABLE: that was the old cover's scrolling bypass.
        }
        return runCatching {
            updateLabels(panel)
            if (view == null) {
                view = root
                attachRequestedAt = SystemClock.uptimeMillis()
                windows.addView(root, parameters(rect))
                regions.filter { it != rect }.forEach { area ->
                    val extra = FrameLayout(context).apply {
                        isClickable = true
                        setBackgroundColor(if (panel.sharedReel) Color.TRANSPARENT else Color.rgb(12, 12, 12))
                    }
                    extraShields.add(extra)
                    windows.addView(extra, parameters(area))
                }
            }
            view = root
            lastPanel = panel
            status = if (isAttached) "Attached; touchable" else "Attachment/layout pending"
            true
        }.getOrElse {
            Timber.e(it, "Instagram touch shield could not attach")
            hide()
            status = "Failed to attach"
            false
        }
    }

    fun navigationFailed() {
        explanation?.text = "Couldn't open that screen. Your feed stays locked.\nYou can leave Instagram."
    }

    fun hide() {
        removeExtraShields()
        val removed = view?.let { runCatching { windows.removeViewImmediate(it) }.isSuccess } ?: true
        if (!removed && view?.isAttachedToWindow == true) {
            // Keep the removal handle and retry on the next foreground check. Hide immediately.
            view?.visibility = View.GONE
            view?.let { root -> runCatching {
                val params = root.layoutParams as WindowManager.LayoutParams
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                windows.updateViewLayout(root, params)
            } }
            status = "Removal failed; hidden; retry pending"
            return
        }
        view = null
        lastPanel = null
        title = null
        explanation = null
        messages = null
        profile = null
        watchShared = null
        status = if (removed && extraShields.isEmpty()) "Removed" else "Removal failed; hidden; retry pending"
    }

    private fun removeExtraShields() {
        val iterator = extraShields.iterator()
        while (iterator.hasNext()) {
            val extra = iterator.next()
            if (runCatching { windows.removeViewImmediate(extra) }.isSuccess || !extra.isAttachedToWindow) iterator.remove()
            else {
                extra.visibility = View.GONE
                runCatching {
                    val params = extra.layoutParams as WindowManager.LayoutParams
                    params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    windows.updateViewLayout(extra, params)
                }
            }
        }
    }

    private fun updateLabels(panel: InstagramLockPanel) {
        title?.text = if (panel.wholeApp) "Instagram is locked" else "Your feed is locked"
        explanation?.text = if (panel.wholeApp) {
            "You chose to block the whole app. Your time is yours."
        } else {
            if (panel.socialMode) "This Reel or Explore screen is locked.\nMessages, Stories and recognised posts stay usable."
            else "The entire Home feed, Reels and Explore are locked, including ordinary posts.\nOpen messages or your profile."
        }
        messages?.visibility = if (panel.wholeApp) View.GONE else View.VISIBLE
        profile?.visibility = if (panel.wholeApp) View.GONE else View.VISIBLE
        messages?.isEnabled = panel.canOpenMessages
        profile?.isEnabled = panel.canOpenProfile
        messages?.alpha = if (panel.canOpenMessages) 1f else 0.4f
        profile?.alpha = if (panel.canOpenProfile) 1f else 0.4f
        watchShared?.visibility = if (panel.canWatchShared && !panel.wholeApp) View.VISIBLE else View.GONE
    }

    private fun buildView(): View {
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.rgb(12, 12, 12))
            isClickable = true
        }
        // First lay out the minimal touch-catching window. A plain View.post before addView
        // can run expensive cold-start font/button work BEFORE the first window layout.
        root.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int,
                oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                if (right > left && bottom > top) {
                    root.removeOnLayoutChangeListener(this)
                    root.post { if (view === root) buildContent(root) }
                }
            }
        })
        return root
    }

    private fun buildContent(root: FrameLayout) {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        if (lastPanel?.sharedReel == true) {
            root.setBackgroundColor(Color.TRANSPARENT)
            val controls = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(8), dp(16), dp(16))
                setBackgroundColor(Color.rgb(12, 12, 12))
                addView(TextView(context).apply {
                    text = "Shared Reel · scrolling and Instagram taps are locked"
                    setTextColor(Color.WHITE)
                    textSize = 14f
                })
                addView(Button(context).apply {
                    id = R.id.guard_shared_back
                    text = "Back to messages"
                    isAllCaps = false
                    setOnClickListener { onSharedBack() }
                })
            }
            root.addView(controls, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
            return
        }
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
        watchShared = button("Watch without scrolling", R.id.guard_watch_shared, false, onWatchShared)
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
