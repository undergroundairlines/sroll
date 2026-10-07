/* Copyright 2026 Scroll Guard contributors. Licensed under the Apache License, Version 2.0. */
package app.scrollguard.models

enum class InstagramProtectionMode(val strength: Int) {
    FEED_LOCK(1),
    APP_LOCK(2),
    SOCIAL(0);

    companion object {
        fun fromStored(value: String?): InstagramProtectionMode =
            entries.firstOrNull { it.name == value } ?: FEED_LOCK
    }
}
