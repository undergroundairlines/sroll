/*
 * Copyright 2026 Scroll Guard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package app.scrollguard.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.scrollguard.widgets.ScreenTimeWidgetUpdater

class ScreenTimeArchiveWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val repository = ScreenTimeRepository(applicationContext)
        if (!repository.hasUsageAccess()) return Result.success()
        return runCatching {
            repository.archiveCompletedDays()
            ScreenTimeWidgetUpdater.updateAll(applicationContext)
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
