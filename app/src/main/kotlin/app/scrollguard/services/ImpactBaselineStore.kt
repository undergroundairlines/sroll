/*
 * Copyright 2026 Scroll Guard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package app.scrollguard.services

import android.content.Context
import org.json.JSONObject

/** Freezes the pre-install baseline before Android eventually prunes those usage events. */
class ImpactBaselineStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(installTimeMillis: Long): Map<String, Long>? {
        if (preferences.getLong(KEY_INSTALL_TIME, -1L) != installTimeMillis) return null
        val encoded = preferences.getString(KEY_DURATIONS, null) ?: return null
        return runCatching {
            val json = JSONObject(encoded)
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val packageName = keys.next()
                    put(packageName, json.getLong(packageName))
                }
            }
        }.getOrNull()
    }

    fun save(installTimeMillis: Long, durations: Map<String, Long>) {
        val json = JSONObject()
        durations.forEach { (packageName, durationMillis) ->
            json.put(packageName, durationMillis)
        }
        preferences.edit()
            .putLong(KEY_INSTALL_TIME, installTimeMillis)
            .putString(KEY_DURATIONS, json.toString())
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "impact_baseline"
        const val KEY_INSTALL_TIME = "install_time"
        const val KEY_DURATIONS = "durations"
    }
}
