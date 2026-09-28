/*
 * Copyright 2026 Scroll Guard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package app.scrollguard.services

import android.content.Context
import java.util.Calendar

data class BlockStats(
    val today: Int = 0,
    val total: Int = 0,
)

/** Counts successful blocker actions locally, without saving content or account names. */
class BlockStatsStore(context: Context) {
    private val preferences = context.getSharedPreferences("block_stats", Context.MODE_PRIVATE)

    fun record(nowMillis: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            val day = dayKey(nowMillis)
            val today = if (preferences.getString("day", null) == day) {
                preferences.getInt("today", 0)
            } else {
                0
            }
            preferences.edit()
                .putString("day", day)
                .putInt("today", today + 1)
                .putInt("total", preferences.getInt("total", 0) + 1)
                .apply()
        }
    }

    fun snapshot(nowMillis: Long = System.currentTimeMillis()): BlockStats = synchronized(lock) {
        BlockStats(
            today = if (preferences.getString("day", null) == dayKey(nowMillis)) {
                preferences.getInt("today", 0)
            } else {
                0
            },
            total = preferences.getInt("total", 0),
        )
    }

    private fun dayKey(nowMillis: Long): String = Calendar.getInstance().apply {
        timeInMillis = nowMillis
    }.let { "${it.get(Calendar.YEAR)}-${it.get(Calendar.DAY_OF_YEAR)}" }

    private companion object {
        val lock = Any()
    }
}
