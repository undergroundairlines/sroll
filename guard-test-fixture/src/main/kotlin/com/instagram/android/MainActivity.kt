/* Copyright 2026 Scroll Guard contributors. Licensed under the Apache License, Version 2.0. */
package com.instagram.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Emulator-only UI. Deliberately has no clips/video identifiers on Home. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render(intent.getStringExtra("screen") ?: "home")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render(intent.getStringExtra("screen") ?: "home")
    }

    private fun render(screen: String) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 80, 0, 80)
        }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        fun label(textValue: String, viewId: Int = 0): TextView = TextView(this).apply {
            text = textValue
            if (viewId != 0) id = viewId
            textSize = 24f
            setPadding(20, 20, 20, 20)
        }
        when (screen) {
            "messages" -> {
                body.id = R.id.direct_thread
                body.addView(label("Fixture messages"))
                body.addView(Button(this).apply {
                    text = "Open contact profile"
                    setOnClickListener { render("profile") }
                })
            }
            "profile" -> {
                body.addView(label("Fixture profile", R.id.row_profile_header_imageview_frame_layout))
                body.addView(label("Profile details", R.id.profile_user_info_compose_view))
            }
            "story" -> {
                body.id = R.id.reel_viewer
                body.addView(label("Fixture story"))
            }
            else -> {
                var clicks = 0
                val status = label("Scroll: 0; clicks: 0", R.id.fixture_status)
                body.addView(status)
                val scroll = ScrollView(this)
                val posts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                if (screen == "home") posts.addView(label("Feed header", R.id.row_feed_profile_header))
                repeat(50) { index ->
                    posts.addView(label("Post $index").apply {
                        setPadding(20, 100, 20, 100)
                        setOnClickListener {
                            clicks++
                            status.text = "Scroll: ${scroll.scrollY}; clicks: $clicks"
                        }
                    })
                }
                scroll.addView(posts)
                scroll.setOnScrollChangeListener { _, _, y, _, _ ->
                    status.text = "Scroll: $y; clicks: $clicks"
                }
                body.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            }
        }
        val nav = LinearLayout(this)
        fun tab(textValue: String, viewId: Int, destination: String) {
            nav.addView(Button(this).apply {
                id = viewId
                text = textValue
                contentDescription = textValue
                isSelected = screen == destination
                setOnClickListener { render(destination) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        tab("Home", R.id.feed_tab, "home")
        tab("Messages", R.id.direct_tab, "messages")
        tab("Profile", R.id.profile_tab, "profile")
        root.addView(nav)
        setContentView(root)
    }
}
