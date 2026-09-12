/*
 * Copyright 2026 Scroll Guard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package app.scrollguard.utils

/** Pure timing rules for the persistent Strict Mode unlock countdown. */
object StrictModePolicy {
    const val UNLOCK_DELAY_MILLIS = 30L * 60L * 1000L
    const val STRICT_MODE_TARGET = "__strict_mode__"

    fun unlockAt(nowMillis: Long): Long = nowMillis + UNLOCK_DELAY_MILLIS

    fun remainingSeconds(unlockAtMillis: Long, nowMillis: Long): Long {
        if (unlockAtMillis <= nowMillis) return 0L
        return (unlockAtMillis - nowMillis + 999L) / 1000L
    }

    fun isExpired(unlockAtMillis: Long, nowMillis: Long): Boolean =
        unlockAtMillis > 0L && nowMillis >= unlockAtMillis
}
