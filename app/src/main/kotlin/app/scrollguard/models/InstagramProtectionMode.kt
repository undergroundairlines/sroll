/* Copyright 2026 Scroll Guard contributors. Licensed under the Apache License, Version 2.0. */
package app.scrollguard.models

enum class InstagramProtectionMode {
    FEED_LOCK,
    APP_LOCK;

    companion object {
        fun fromStored(value: String?): InstagramProtectionMode =
            entries.firstOrNull { it.name == value } ?: FEED_LOCK
    }
}
