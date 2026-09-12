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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.scrollguard.models.AppUsage
import app.scrollguard.models.ScreenTimeReport
import app.scrollguard.models.UsageBucket
import app.scrollguard.models.UsagePeriod
import app.scrollguard.ui.components.AppSection
import app.scrollguard.ui.components.AppSectionSwitcher
import app.scrollguard.ui.viewmodels.ScreenTimeState
import app.scrollguard.utils.ScreenTimeFormatting
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.abs

@Composable
fun ScreenTimeScreen(
    state: ScreenTimeState,
    onPeriodSelected: (UsagePeriod) -> Unit,
    onRefresh: () -> Unit,
    onOpenBlocker: () -> Unit,
) {
    var selectedPackage by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = selectedPackage != null) { selectedPackage = null }
    val selectedApp = state.report?.apps?.firstOrNull { it.packageName == selectedPackage }

    if (selectedPackage != null && selectedApp != null && state.report != null) {
        AppDetailScreen(
            app = selectedApp,
            report = state.report,
            onBack = { selectedPackage = null },
            onPeriodSelected = onPeriodSelected,
        )
        return
    }

    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
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
                    val direct = Intent(
                        Settings.ACTION_USAGE_ACCESS_SETTINGS,
                        Uri.parse("package:${context.packageName}"),
                    )
                    runCatching { context.startActivity(direct) }.getOrElse {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                },
            )
        } else if (state.isLoading && state.report == null) {
            Spacer(modifier = Modifier.height(80.dp))
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Building an exact usage history…")
        } else {
            state.report?.let { report ->
                ScreenTimeDashboard(
                    report = report,
                    onPeriodSelected = onPeriodSelected,
                    onRefresh = onRefresh,
                    onAppSelected = { selectedPackage = it.packageName },
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
                text = "Allow Usage Access so Scroll Guard can rebuild exact app sessions and percentages. Everything stays on your phone.",
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
    onAppSelected: (AppUsage) -> Unit,
) {
    Text(
        text = periodTitle(report.period),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = ScreenTimeFormatting.duration(report.totalMillis),
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
    )
    Text(
        text = comparisonText(report),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(18.dp))
    PeriodSwitcher(report.period, onPeriodSelected)
    Spacer(modifier = Modifier.height(16.dp))

    UsageSummary(report)
    Spacer(modifier = Modifier.height(14.dp))
    UsageBarChart(report.usageBuckets, report.chartTitle)
    Spacer(modifier = Modifier.height(14.dp))
    AppBreakdown(report.apps, report.totalMillis, onAppSelected)

    TextButton(onClick = onRefresh) { Text("Refresh exact data") }
}

@Composable
private fun UsageSummary(report: ScreenTimeReport) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SummaryCard(
            label = "Screen on",
            value = ScreenTimeFormatting.duration(report.screenOnMillis),
            modifier = Modifier.weight(1f),
        )
        SummaryCard(
            label = "Pickups",
            value = report.pickups.toString(),
            modifier = Modifier.weight(1f),
        )
    }
    if (report.period == UsagePeriod.ALL) {
        val locale = LocalConfiguration.current.locales[0]
        val date = SimpleDateFormat("d MMM yyyy", locale)
            .format(Date(report.trackingSinceMillis))
        Text(
            text = "Recorded history since $date",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PeriodSwitcher(selected: UsagePeriod, onSelected: (UsagePeriod) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        UsagePeriod.entries.forEach { period ->
            Button(
                onClick = { onSelected(period) },
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 4.dp,
                    vertical = 10.dp,
                ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected == period) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (selected == period) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ),
            ) {
                Text(
                    text = when (period) {
                        UsagePeriod.DAY -> "Day"
                        UsagePeriod.WEEK -> "Week"
                        UsagePeriod.MONTH -> "Month"
                        UsagePeriod.ALL -> "All"
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun UsageBarChart(buckets: List<UsageBucket>, title: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(14.dp))
            val maxDuration = buckets.maxOfOrNull { it.durationMillis }?.coerceAtLeast(1L) ?: 1L
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .height(150.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                buckets.forEachIndexed { index, bucket ->
                    val showEvery = when {
                        buckets.size <= 12 -> 1
                        buckets.size <= 24 -> 3
                        else -> 5
                    }
                    Column(
                        modifier = Modifier.width(if (buckets.size <= 7) 36.dp else 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        if (buckets.size <= 7) {
                            Text(
                                text = compactDuration(bucket.durationMillis),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Box(
                            modifier = Modifier
                                .width(if (buckets.size <= 7) 28.dp else 16.dp)
                                .height(
                                    (100f * bucket.durationMillis.toFloat() / maxDuration.toFloat())
                                        .coerceAtLeast(3f)
                                        .dp,
                                )
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (index % showEvery == 0) bucket.label else "",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppBreakdown(
    apps: List<AppUsage>,
    totalMillis: Long,
    onAppSelected: (AppUsage) -> Unit,
) {
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
                    text = "No app activity was recorded in this period.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            }
            val visibleApps = if (showAll) apps else apps.take(8)
            visibleApps.forEach { app ->
                Spacer(modifier = Modifier.height(10.dp))
                AppUsageRow(app, totalMillis, onClick = { onAppSelected(app) })
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
private fun AppUsageRow(app: AppUsage, totalMillis: Long, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.packageName)
        Spacer(modifier = Modifier.size(12.dp))
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

@Composable
private fun AppDetailScreen(
    app: AppUsage,
    report: ScreenTimeReport,
    onBack: () -> Unit,
    onPeriodSelected: (UsagePeriod) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onBack)
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
            Spacer(modifier = Modifier.width(10.dp))
            Text("All apps", style = MaterialTheme.typography.titleMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.packageName, size = 58)
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(app.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(periodTitle(report.period), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            ScreenTimeFormatting.duration(app.durationMillis),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "${app.percentage}% of your app time",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(20.dp))
        PeriodSwitcher(report.period, onPeriodSelected)
        Spacer(modifier = Modifier.height(16.dp))
        UsageBarChart(app.buckets, "${app.displayName} usage")
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun AppIcon(packageName: String, size: Int = 42) {
    val context = LocalContext.current
    val icon: ImageBitmap? = remember(packageName) {
        try {
            context.packageManager.getApplicationIcon(packageName)
                .toBitmap(width = 64, height = 64)
                .asImageBitmap()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }
    if (icon != null) {
        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(size.dp))
    } else {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(packageName.substringAfterLast('.').take(1).uppercase())
        }
    }
}

private fun periodTitle(period: UsagePeriod): String = when (period) {
    UsagePeriod.DAY -> "Today"
    UsagePeriod.WEEK -> "Last 7 days"
    UsagePeriod.MONTH -> "Last 30 days"
    UsagePeriod.ALL -> "All time"
}

private fun compactDuration(millis: Long): String {
    val minutes = millis / 60_000L
    return when {
        minutes >= 60L -> "${minutes / 60L}h"
        minutes > 0L -> "${minutes}m"
        else -> "0m"
    }
}

private fun comparisonText(report: ScreenTimeReport): String {
    if (report.period == UsagePeriod.ALL) return "Everything recorded on this phone"
    val change = report.changePercentage ?: return "No earlier data to compare"
    val comparison = when (report.period) {
        UsagePeriod.DAY -> "the same time yesterday"
        UsagePeriod.WEEK -> "the previous 7 days"
        UsagePeriod.MONTH -> "the previous 30 days"
        UsagePeriod.ALL -> return "Everything recorded on this phone"
    }
    return when {
        change > 0 -> "${abs(change)}% more than $comparison"
        change < 0 -> "${abs(change)}% less than $comparison"
        else -> "The same as $comparison"
    }
}
