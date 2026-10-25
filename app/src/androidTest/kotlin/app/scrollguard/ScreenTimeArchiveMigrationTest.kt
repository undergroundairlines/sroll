/* Copyright 2026 Scroll Guard contributors. Licensed under the Apache License, Version 2.0. */
package app.scrollguard

import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.scrollguard.services.ExactUsageSnapshot
import app.scrollguard.services.ScreenTimeArchive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real SQLite upgrade regression in an isolated private test database. */
@RunWith(AndroidJUnit4::class)
class ScreenTimeArchiveMigrationTest {
    @Test fun versionOneTotalsSurviveUpgradeAndIncompleteReplacement() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseFile = File(base.cacheDir, "archive-upgrade-${System.nanoTime()}.db")
        val context = object : ContextWrapper(base) {
            override fun getDatabasePath(name: String): File = databaseFile
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
                SQLiteDatabase.openOrCreateDatabase(databaseFile, factory)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?,
                errorHandler: android.database.DatabaseErrorHandler?): SQLiteDatabase =
                SQLiteDatabase.openOrCreateDatabase(databaseFile.absolutePath, factory, errorHandler)
        }
        try {
            SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { db ->
                db.execSQL("CREATE TABLE app_daily(day_start INTEGER NOT NULL, package_name TEXT NOT NULL, foreground_ms INTEGER NOT NULL, PRIMARY KEY(day_start,package_name))")
                db.execSQL("CREATE TABLE day_summary(day_start INTEGER PRIMARY KEY, screen_on_ms INTEGER NOT NULL, pickups INTEGER NOT NULL)")
                db.execSQL("INSERT INTO app_daily VALUES(1000,'com.instagram.android',5000)")
                db.execSQL("INSERT INTO day_summary VALUES(1000,10000,3)")
                db.version = 1
            }
            ScreenTimeArchive(context).use { archive ->
                assertEquals(5000L, archive.appTotals(1000, 2000)["com.instagram.android"])
                assertEquals(10000L, archive.summary(1000, 2000).screenOnMillis)
                assertFalse(archive.hasCompleteDay(1000))
                archive.replaceDay(1000, ExactUsageSnapshot(mapOf("com.instagram.android" to 1L),
                    emptyList(), 1L, 0, historyComplete = false))
                assertEquals(5000L, archive.appTotals(1000, 2000)["com.instagram.android"])
                assertEquals(3, archive.summary(1000, 2000).pickups)
                archive.replaceDay(1000, ExactUsageSnapshot(mapOf("com.instagram.android" to 4000L),
                    emptyList(), 8000L, 2, historyComplete = true))
                assertEquals(4000L, archive.appTotals(1000, 2000)["com.instagram.android"])
                assertTrue(archive.hasCompleteDay(1000))
            }
        } finally {
            SQLiteDatabase.deleteDatabase(databaseFile)
        }
    }
}
