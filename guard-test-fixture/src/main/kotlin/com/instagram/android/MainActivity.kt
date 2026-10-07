/* Copyright 2026 Scroll Guard contributors. Licensed under the Apache License, Version 2.0. */
package com.instagram.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Emulator-only structural scenarios. These resource IDs do not assert Instagram compatibility. */
class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var currentScreen = "home"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render(savedInstanceState?.getString("screen") ?: intent.getStringExtra("screen") ?: "home")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handler.removeCallbacksAndMessages(null)
        setIntent(intent)
        render(intent.getStringExtra("screen") ?: "home")
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("screen", currentScreen)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Synthetic fixture uses an explicit screen stack")
    override fun onBackPressed() {
        when (currentScreen) {
            "conversation", "shared_reel" -> render(if (currentScreen == "shared_reel") "conversation" else "messages")
            "messages", "profile", "story" -> render("home")
            else -> super.onBackPressed()
        }
    }

    private fun render(screen: String, suppressChildEvents: Boolean = false) {
        currentScreen = screen
        val root = object : LinearLayout(this) {
            override fun requestSendAccessibilityEvent(child: View, event: AccessibilityEvent): Boolean =
                !suppressChildEvents && super.requestSendAccessibilityEvent(child, event)

            override fun sendAccessibilityEventUnchecked(event: AccessibilityEvent) {
                if (!suppressChildEvents) super.sendAccessibilityEventUnchecked(event)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 80, 0, 80)
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // Expose screen containers just as real accessible list/viewer containers are exposed.
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        fun label(textValue: String, viewId: Int = 0): TextView = TextView(this).apply {
            text = textValue
            if (viewId != 0) id = viewId
            textSize = 24f
            setPadding(20, 20, 20, 20)
        }
        fun action(textValue: String, parent: LinearLayout = body, viewId: Int = 0, action: () -> Unit) {
            parent.addView(Button(this).apply {
                if (viewId != 0) id = viewId
                isAllCaps = false
                text = textValue
                setOnClickListener { action() }
            })
        }
        when (screen) {
            "messages" -> {
                body.id = R.id.direct_inbox
                body.addView(label("Fixture messages"))
                action("Open conversation") { render("conversation") }
                action("Open contact profile") { render("profile") }
                // Delay until the click event is over, then suppress descendant events.
                // The platform may still issue its own events; this is a watchdog exercise.
                action("Silent return Home") {
                    handler.postDelayed({ render("home", suppressChildEvents = true) }, 700L)
                }
            }
            "conversation", "conversation_immediate" -> {
                body.addView(label("Fixture conversation"))
                val history = LinearLayout(this).apply {
                    id = R.id.message_list
                    orientation = LinearLayout.VERTICAL
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                }
                history.addView(label("Synthetic message history"))
                action("Open shared Reel", history,
                    if (screen == "conversation_immediate") R.id.fixture_shared_reel_no_source else R.id.direct_shared_reel) {
                    // Keep the clicked structural source observable until TYPE_VIEW_CLICKED
                    // is delivered. A real app can destroy it first; production must fail
                    // closed then. This delay tests only the supported, observable case.
                    if (screen == "conversation_immediate") render("shared_reel")
                    else handler.postDelayed({ render("shared_reel") }, 250L)
                }
                action("Open contact profile", history) { render("profile") }
                body.addView(history, LinearLayout.LayoutParams(-1, 0, 1f))
                val sent = label("Sent: 0", R.id.fixture_sent_status)
                body.addView(sent)
                val composer = EditText(this).apply {
                    id = R.id.row_thread_composer_edittext
                    hint = "Message"
                    setSingleLine()
                }
                body.addView(composer)
                var sendCount = 0
                body.addView(Button(this).apply {
                    id = R.id.row_thread_composer_button_send
                    text = "Send"
                    setOnClickListener {
                        if (composer.text.isNotEmpty()) {
                            sendCount++
                            composer.text.clear()
                            sent.text = "Sent: $sendCount"
                        }
                    }
                })
            }
            "profile" -> {
                body.addView(label("Fixture profile", R.id.row_profile_header_imageview_frame_layout))
                body.addView(label("Profile details", R.id.profile_user_info_compose_view))
                action("Open profile Reel") { render("reels") }
            }
            "story" -> {
                body.id = R.id.reel_viewer
                body.addView(label("Fixture story"))
            }
            "reels", "shared_reel" -> {
                body.id = R.id.clips_viewer_view_pager
                body.addView(label(if (screen == "shared_reel") "Fixture shared Reel" else "Fixture Reels"))
                addFeed(body, label("Scroll: 0; clicks: 0", R.id.fixture_status),
                    media = true, ordinaryHome = false)
            }
            else -> {
                val status = label("Scroll: 0; clicks: 0", R.id.fixture_status)
                if (screen == "explore") body.id = R.id.explore_grid
                if (screen == "cached_profile") body.addView(label("Cached header", R.id.row_profile_header_imageview_frame_layout))
                if (screen == "truncated") {
                    repeat(1_300) {
                        body.addView(label("Synthetic node $it").apply {
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                        }, LinearLayout.LayoutParams(-1, 1))
                    }
                }
                if (screen == "stale_profile") {
                    // Cached but hidden safety nodes must never unlock a live feed.
                    body.addView(label("Cached header", R.id.row_profile_header_imageview_frame_layout).apply {
                        visibility = View.INVISIBLE
                    })
                    body.addView(label("Cached profile", R.id.profile_user_info_compose_view).apply {
                        visibility = View.INVISIBLE
                    })
                }
                addFeed(body, status, media = screen == "home_reel", ordinaryHome = screen == "home" || screen == "home_reel")
            }
        }
        val nav = LinearLayout(this)
        fun tab(textValue: String, viewId: Int, destination: String) {
            nav.addView(Button(this).apply {
                id = viewId
                text = textValue
                contentDescription = textValue
                isSelected = screen == destination || (screen == "selected_profile" && destination == "profile")
                // ACTION_CLICK still returns true for this no-op; protection must remain.
                setOnClickListener { if (screen != "navigation_noop") render(destination) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        tab("Home", R.id.feed_tab, "home")
        tab("Messages", R.id.direct_tab, "messages")
        tab("Profile", R.id.profile_tab, "profile")
        root.addView(nav)
        if (screen == "empty_tree") root.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        setContentView(root)
        if (screen == "event_storm") {
            // Sustained callbacks must not occupy the service's main Looper ahead of layout.
            repeat(120) { index ->
                handler.postDelayed({
                    if (currentScreen == screen) root.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
                }, index * 35L)
            }
        }
    }

    private fun addFeed(body: LinearLayout, status: TextView, media: Boolean, ordinaryHome: Boolean) {
        var clicks = 0
        body.addView(status)
        val scroll = ScrollView(this).apply { if (ordinaryHome) id = R.id.feed_recycler_view }
        val posts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (ordinaryHome) posts.addView(TextView(this).apply {
            id = R.id.row_feed_profile_header
            text = "Feed header"
            setPadding(20, 20, 20, 20)
        })
        repeat(50) { index ->
            posts.addView(TextView(this).apply {
                text = "Post $index"
                textSize = 24f
                if (media && index == 0) id = R.id.clips_video_container
                setPadding(20, if (media && index == 0) 300 else 100,
                    20, if (media && index == 0) 300 else 100)
                setOnClickListener {
                    clicks++
                    status.text = "Scroll: ${scroll.scrollY}; clicks: $clicks"
                }
            })
        }
        scroll.addView(posts)
        scroll.setOnScrollChangeListener { _, _, y, _, _ -> status.text = "Scroll: $y; clicks: $clicks" }
        body.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    }
}
