/* Copyright 2026 Scroll Guard contributors. Licensed under the Apache License, Version 2.0. */
package app.scrollguard.services.detectors

internal enum class MediaRole { NONE, VIDEO, REEL }

/** Keep only fixed UI roles, never the description, captions or account content. */
internal object InstagramMediaSemantics {
    fun role(description: CharSequence?): MediaRole {
        val value = description?.toString()?.trim()?.lowercase(java.util.Locale.ROOT) ?: return MediaRole.NONE
        fun named(name: String) = value == name || value.startsWith("$name,") ||
            value.startsWith("$name:") || value.startsWith("$name.")
        return when {
            named("reel") || value == "watch reel" || value == "play reel" -> MediaRole.REEL
            named("video") || value == "play video" || value == "pause video" -> MediaRole.VIDEO
            else -> MediaRole.NONE
        }
    }
    fun isVideoView(className: String): Boolean = className == "android.widget.VideoView" ||
        className == "android.view.TextureView"
}
