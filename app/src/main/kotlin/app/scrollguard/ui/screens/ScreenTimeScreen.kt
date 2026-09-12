/*
 * Copyright 2026 Scroll Guard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package app.scrollguard.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.scrollguard.models.AppUsage
import app.scrollguard.models.DailyUsage
import app.scrollguard.models.ScreenTimeReport
import app.scrollguard.models.UsagePeriod
import app.scrollguard.ui.components.AppSection
import app.scrollguard.ui.components.AppSectionSwitcher
import app.scrollguard.ui.viewmodels.ScreenTimeState
import app.scrollguard.utils.ScreenTimeFormatting
import kotlin.math.abs

@Composable
fun ScreenTimeScreen(
    state: ScreenTimeState,
    onPeriodSelected: (UsagePeriod) -> Unit,
    onRefresh: () -> Unit,
    onOpenBlocker: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppSectionSwitcher(
            selected = AppSection.SCREEN_TIME,
            onSelected = { if (it == AppSection.BLOCKER) onOpenBlocker() },
        )

        if (!state.hasUsageAccess) {
            UsageAccessRequired(
                onOpenSettings = {
                    val intent = Intent(
                        Settings.ACTION_USAGE_ACCESS_SETTINGS,
                        Uri.parse("package:${context.packageName}"),
                    )
                    context.startActivity(intent)
                },
            )
        } else if (state.isLoading && state.report == null) {
            Spacer(modifier = Modifier.height(80.dp))
            CircularProgressIndicator()
        } else {
            state.report?.let { report ->
                ScreenTimeDashboard(
                    report = report,
                    onPeriodSelected = onPeriodSelected,
                    onRefresh = onRefresh,
                )
            }
            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun UsageAccessRequired(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "See your time clearly",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Allow Usage Access so Scroll Guard can calculate app time and percentages. The data stays on your phone.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Allow Usage Access")
            }
        }
    }
}

@Composable
private fun ScreenTimeDashboard(
    report: ScreenTimeReport,
    onPeriodSelected: (UsagePeriod) -> Unit,
    onRefresh: () -> Unit,
) {
    Text(
        text = if (report.period == UsagePeriod.DAY) "Today" else "Last 7 days",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = ScreenTimeFormatting.duration(report.totalMillis),
        style = MaterialTheme.typography.displayLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
        text = comparisonText(report),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(20.dp))
    PeriodSwitcher(report.period, onPeriodSelected)
    Spacer(modifier = Modifier.height(20.dp))

    WeeklyChart(report.lastSevenDays)
    Spacer(modifier = Modifier.height(16.dp))
    AppBreakdown(report.apps, report.totalMillis)

    TextButton(onClick = onRefresh) { Text("Refresh") }
}

@Composable
private fun PeriodSwitcher(selected: UsagePeriod, onSelected: (UsagePeriod) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        PeriodButton(
            text = "Day",
            selected = selected == UsagePeriod.DAY,
            onClick = { onSelected(UsagePeriod.DAY) },
            modifier = Modifier.weight(1f),
        )
        PeriodButton(
            text = "Week",
            selected = selected == UsagePeriod.WEEK,
            onClick = { onSelected(UsagePeriod.WEEK) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PeriodButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.padding(horizontal = 4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun WeeklyChart(days: List<DailyUsage>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text("Last 7 days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(18.dp))
            val max = days.maxOfOrNull { it.durationMillis }?.coerceAtLeast(1L) ?: 1L
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                days.forEach { day ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text(
                            text = chartDuration(day.durationMillis),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(
                                    (100f * day.durationMillis.toFloat() / max.toFloat())
                                        .coerceAtLeast(4f)
                                        .dp,
                                )
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
                        ) {
                            LinearProgressIndicator(
                                progress = { 1f },
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(day.dayLabel, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppBreakdown(apps: List<AppUsage>, totalMillis: Long) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text("Apps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (apps.isEmpty()) {
                Text(
                    text = "No app usage recorded yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            }
            val visibleApps = if (showAll) apps else apps.take(8)
            visibleApps.forEach { app ->
                Spacer(modifier = Modifier.height(18.dp))
                AppUsageRow(app, totalMillis)
            }
            if (apps.size > 8) {
                TextButton(onClick = { showAll = !showAll }) {
                    Text(if (showAll) "Show less" else "Show all ${apps.size} apps")
                }
            }
        }
    }
}

@Composable
private fun AppUsageRow(app: AppUsage, totalMillis: Long) {
    val context = LocalContext.current
    val icon: ImageBitmap? = remember(app.packageName) {
        try {
            context.packageManager.getApplicationIcon(app.packageName)
                .toBitmap(width = 48, height = 48)
                .asImageBitmap()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.size(38.dp),
            )
            Spacer(modifier = Modifier.size(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = app.displayName,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${ScreenTimeFormatting.duration(app.durationMillis)} · ${app.percentage}%",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Spacer(modifier = Modifier.height(7.dp))
            LinearProgressIndicator(
                progress = {
                    if (totalMillis <= 0L) 0f
                    else (app.durationMillis.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        }
    }
}

private fun chartDuration(millis: Long): String {
    val minutes = millis / 60_000L
    return if (minutes >= 60L) "${minutes / 60L}h" else "${minutes}m"
}

private fun comparisonText(report: ScreenTimeReport): String {
    val change = report.changePercentage ?: return "No previous usage to compare"
    val comparison = if (report.period == UsagePeriod.DAY) "yesterday" else "the previous week"
    return when {
        change > 0 -> "${abs(change)}% more than $comparison"
        change < 0 -> "${abs(change)}% less than $comparison"
        else -> "The same as $comparison"
    }
}
