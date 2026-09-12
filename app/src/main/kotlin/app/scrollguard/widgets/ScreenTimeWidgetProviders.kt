/*
 * Copyright 2026 Scroll Guard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package app.scrollguard.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import app.scrollguard.MainActivity
import app.scrollguard.R
import app.scrollguard.models.AppUsage
import app.scrollguard.models.UsagePeriod
import app.scrollguard.services.ScreenTimeRepository
import app.scrollguard.utils.ScreenTimeFormatting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs

class SmallScreenTimeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        ScreenTimeWidgetUpdater.update(context, manager, appWidgetIds, wide = false)
    }
}

class WideScreenTimeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        ScreenTimeWidgetUpdater.update(context, manager, appWidgetIds, wide = true)
    }
}

object ScreenTimeWidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        update(
            context,
            manager,
            manager.getAppWidgetIds(ComponentName(context, SmallScreenTimeWidgetProvider::class.java)),
            wide = false,
        )
        update(
            context,
            manager,
            manager.getAppWidgetIds(ComponentName(context, WideScreenTimeWidgetProvider::class.java)),
            wide = true,
        )
    }

    fun update(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
        wide: Boolean,
    ) {
        if (appWidgetIds.isEmpty()) return
        val appContext = context.applicationContext
        scope.launch {
            val repository = ScreenTimeRepository(appContext)
            val report = if (repository.hasUsageAccess()) {
                runCatching { repository.load(UsagePeriod.DAY) }.getOrNull()
            } else {
                null
            }

            appWidgetIds.forEach { widgetId ->
                val layout = if (wide) R.layout.widget_screen_time_wide else R.layout.widget_screen_time_small
                val views = RemoteViews(appContext.packageName, layout)
                views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(appContext))

                if (report == null) {
                    views.setTextViewText(R.id.widget_total, "Tap to set up")
                    views.setTextViewText(R.id.widget_change, "Usage Access needed")
                    if (wide) setWideApps(views, emptyList())
                } else {
                    views.setTextViewText(
                        R.id.widget_total,
                        ScreenTimeFormatting.duration(report.totalMillis),
                    )
                    views.setTextViewText(R.id.widget_change, widgetComparison(report.changePercentage))
                    if (wide) setWideApps(views, report.apps.take(3))
                }
                manager.updateAppWidget(widgetId, views)
            }
        }
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_SCREEN_TIME, true)
        }
        return PendingIntent.getActivity(
            context,
            201,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun setWideApps(views: RemoteViews, apps: List<AppUsage>) {
        val rowIds = intArrayOf(R.id.widget_app_1, R.id.widget_app_2, R.id.widget_app_3)
        rowIds.forEachIndexed { index, viewId ->
            val app = apps.getOrNull(index)
            views.setViewVisibility(viewId, if (app == null) View.GONE else View.VISIBLE)
            if (app != null) {
                views.setTextViewText(
                    viewId,
                    "${app.displayName}  ${ScreenTimeFormatting.duration(app.durationMillis)} · ${app.percentage}%",
                )
            }
        }
    }

    private fun widgetComparison(change: Int?): String {
        return when {
            change == null -> "Today"
            change > 0 -> "↑ ${abs(change)}% vs yesterday"
            change < 0 -> "↓ ${abs(change)}% vs yesterday"
            else -> "Same as yesterday"
        }
    }
}
