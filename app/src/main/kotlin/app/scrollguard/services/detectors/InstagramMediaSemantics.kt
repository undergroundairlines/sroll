/* Copyright 2026 Scroll Guard contributors. Licensed under the Apache License, Version 2.0. */
package app.scrollguard.services.detectors

internal enum class MediaRole { NONE, VIDEO, REEL, PHOTO }

/** Keep only fixed UI roles, never the description, captions or account content. */
internal object InstagramMediaSemantics {
    fun role(description: CharSequence?): MediaRole {
        val value = description?.toString()?.trim()?.lowercase(java.util.Locale.ROOT) ?: return MediaRole.NONE
        fun named(name: String) = value == name || value.startsWith("$name,") ||
            value.startsWith("$name:") || value.startsWith("$name.")
        return when {
            named("reel") || value == "watch reel" || value == "play reel" -> MediaRole.REEL
            named("video") || value.startsWith("video by ") || value == "play video" || value == "pause video" -> MediaRole.VIDEO
            named("photo") || value.startsWith("photo by ") || named("image") -> MediaRole.PHOTO
            else -> MediaRole.NONE
        }
    }
    fun isVideoView(className: String): Boolean = className == "android.widget.VideoView"
    fun isUnlabelledSurfaceClass(className: String): Boolean = className == "android.view.View" ||
        className == "android.view.TextureView"
}
