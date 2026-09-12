/*
 * Copyright 2026 Scroll Guard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package app.scrollguard.services

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class ArchivedSummary(
    val screenOnMillis: Long,
    val pickups: Int,
)

/** Small local archive that preserves completed daily totals after Android prunes old events. */
class ScreenTimeArchive(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE app_daily (
                day_start INTEGER NOT NULL,
                package_name TEXT NOT NULL,
                foreground_ms INTEGER NOT NULL,
                PRIMARY KEY (day_start, package_name)
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE day_summary (
                day_start INTEGER PRIMARY KEY,
                screen_on_ms INTEGER NOT NULL,
                pickups INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun hasDay(dayStart: Long): Boolean {
        readableDatabase.query(
            "day_summary",
            arrayOf("day_start"),
            "day_start = ?",
            arrayOf(dayStart.toString()),
            null,
            null,
            null,
            "1",
        ).use { cursor -> return cursor.moveToFirst() }
    }

    fun latestDay(): Long? {
        readableDatabase.rawQuery("SELECT MAX(day_start) FROM day_summary", null).use { cursor ->
            if (!cursor.moveToFirst() || cursor.isNull(0)) return null
            return cursor.getLong(0)
        }
    }

    fun replaceDay(dayStart: Long, snapshot: ExactUsageSnapshot) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete(
                "app_daily",
                "day_start = ?",
                arrayOf(dayStart.toString()),
            )
            snapshot.durations.forEach { (packageName, durationMillis) ->
                writableDatabase.insertOrThrow(
                    "app_daily",
                    null,
                    ContentValues().apply {
                        put("day_start", dayStart)
                        put("package_name", packageName)
                        put("foreground_ms", durationMillis)
                    },
                )
            }
            writableDatabase.insertWithOnConflict(
                "day_summary",
                null,
                ContentValues().apply {
                    put("day_start", dayStart)
                    put("screen_on_ms", snapshot.screenOnMillis)
                    put("pickups", snapshot.pickups)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun appTotals(startDay: Long, endDayExclusive: Long): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        readableDatabase.rawQuery(
            """
            SELECT package_name, SUM(foreground_ms)
            FROM app_daily
            WHERE day_start >= ? AND day_start < ?
            GROUP BY package_name
            """.trimIndent(),
            arrayOf(startDay.toString(), endDayExclusive.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) result[cursor.getString(0)] = cursor.getLong(1)
        }
        return result
    }

    fun dailyTotals(startDay: Long, endDayExclusive: Long): Map<Long, Long> {
        val result = mutableMapOf<Long, Long>()
        readableDatabase.rawQuery(
            """
            SELECT day_start, SUM(foreground_ms)
            FROM app_daily
            WHERE day_start >= ? AND day_start < ?
            GROUP BY day_start
            """.trimIndent(),
            arrayOf(startDay.toString(), endDayExclusive.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) result[cursor.getLong(0)] = cursor.getLong(1)
        }
        return result
    }

    fun appDailyTotals(packageName: String, startDay: Long, endDayExclusive: Long): Map<Long, Long> {
        val result = mutableMapOf<Long, Long>()
        readableDatabase.query(
            "app_daily",
            arrayOf("day_start", "foreground_ms"),
            "package_name = ? AND day_start >= ? AND day_start < ?",
            arrayOf(packageName, startDay.toString(), endDayExclusive.toString()),
            null,
            null,
            "day_start ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) result[cursor.getLong(0)] = cursor.getLong(1)
        }
        return result
    }

    fun summary(startDay: Long, endDayExclusive: Long): ArchivedSummary {
        readableDatabase.rawQuery(
            """
            SELECT COALESCE(SUM(screen_on_ms), 0), COALESCE(SUM(pickups), 0)
            FROM day_summary
            WHERE day_start >= ? AND day_start < ?
            """.trimIndent(),
            arrayOf(startDay.toString(), endDayExclusive.toString()),
        ).use { cursor ->
            cursor.moveToFirst()
            return ArchivedSummary(cursor.getLong(0), cursor.getInt(1))
        }
    }

    private companion object {
        const val DATABASE_NAME = "screen_time_archive.db"
        const val DATABASE_VERSION = 1
    }
}
