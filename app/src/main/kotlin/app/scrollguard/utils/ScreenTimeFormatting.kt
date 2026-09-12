/*
 * Copyright 2026 Scroll Guard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package app.scrollguard.utils

import kotlin.math.roundToInt

object ScreenTimeFormatting {
    fun duration(millis: Long): String {
        val totalMinutes = (millis.coerceAtLeast(0L) / 60_000L)
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return when {
            hours > 0L && minutes > 0L ->
                "${hours}h ${minutes.toString().padStart(2, '0')}m"
            hours > 0L -> "${hours}h"
            minutes > 0L -> "${minutes}m"
            millis > 0L -> "<1m"
            else -> "0m"
        }
    }

    fun percentage(partMillis: Long, totalMillis: Long): Int {
        if (partMillis <= 0L || totalMillis <= 0L) return 0
        return ((partMillis.toDouble() / totalMillis.toDouble()) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
    }

    fun changePercentage(currentMillis: Long, previousMillis: Long): Int? {
        if (previousMillis <= 0L) return null
        return (((currentMillis - previousMillis).toDouble() / previousMillis.toDouble()) * 100.0)
            .roundToInt()
    }
}
