/*
 * Copyright 2026 Scroll Guard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package app.scrollguard.services

import android.content.Context

/** A local, optional daily target for total app time. Zero means no goal. */
class ScreenTimeGoalStore(context: Context) {
    private val preferences = context.getSharedPreferences("screen_time_goal", Context.MODE_PRIVATE)

    fun getMinutes(): Int = preferences.getInt("daily_minutes", 0).coerceIn(0, 720)

    fun setMinutes(minutes: Int) {
        preferences.edit().putInt("daily_minutes", minutes.coerceIn(0, 720)).apply()
    }
}
