package com.extensionbox.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.extensionbox.app.MonitorService
import com.extensionbox.app.Prefs
import com.extensionbox.app.R
import com.extensionbox.app.db.ModuleDataEntity
import com.extensionbox.app.ui.ModuleRegistry
import com.extensionbox.app.ui.components.*
import com.extensionbox.app.ui.viewmodel.DashboardViewModel
import kotlin.math.max

@Composable
fun ModuleDetailScreen(moduleKey: String, viewModel: DashboardViewModel) {
    val context = LocalContext.current
    val dashData = viewModel.dashData.collectAsState().value
    val historyData = viewModel.historyData.collectAsState().value
    val sysAccess = viewModel.sysAccess.collectAsState().value
    var fapLocalUpdateTick by remember { mutableStateOf(0) }

    val baseData = dashData[moduleKey] ?: emptyMap()
    val data =
            remember(moduleKey, baseData, fapLocalUpdateTick) {
                if (moduleKey != "fap") {
                    baseData
                } else {
                    baseData.toMutableMap().apply {
                        put("fap.today", Prefs.getInt(context, "fap_today", 0).toString())
                        put("fap.yesterday", Prefs.getInt(context, "fap_yesterday", 0).toString())
                        val streak = Prefs.getInt(context, "fap_streak", 0)
                        put("fap.streak", if (streak > 0) "${streak}d" else "0")
                        put("fap.monthly", Prefs.getInt(context, "fap_monthly", 0).toString())
                        put("fap.all_time", Prefs.getInt(context, "fap_all_time", 0).toString())
                    }
                }
            }
    val history = historyData[moduleKey] ?: emptyList()
    val appUsageData = dashData["app_usage"] ?: emptyMap()
    val module = com.extensionbox.app.ui.ModuleRegistry.getModule(moduleKey)
    val scrollState = rememberScrollState()

    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .verticalScroll(scrollState)
                            .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (history.isNotEmpty()) {
            Surface(
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                                Icons.Default.Timeline,
                                null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                                stringResource(id = R.string.history_15m),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxSize()) {
                        Sparkline(
                                points = extractPoints(moduleKey, history),
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.primary,
                                fillGradient = true,
                                animate = true
                        )
                    }
                }
            }
        }

        if (moduleKey == "battery") {
            AppUsageBatterySection(appUsageData = appUsageData, batteryHistory = history)
        }

        if (data.isNotEmpty()) {
            Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            ) {
                Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                            text = stringResource(id = R.string.real_time_metrics),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                    )

                    val items = data.toList()
                    items.chunked(2).forEach { rowItems ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            rowItems.forEach { pair ->
                                val label =
                                        pair.first
                                                .substringAfterLast('.')
                                                .replace("_", " ")
                                                .replaceFirstChar { it.uppercase() }
                                StatItem(
                                        label = label,
                                        value = pair.second,
                                        modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }

                    if (module != null && sysAccess != null) {
                        module.composableContent(context, sysAccess)
                    }
                }
            }
        }

        val hasSettings = module?.hasSettings() == true
        val isFap = moduleKey == "fap"

        if (module != null && sysAccess != null && (hasSettings || isFap)) {
            Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            ) {
                Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                                Icons.Default.Settings,
                                null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                                text = stringResource(id = R.string.extension_settings),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (hasSettings) {
                        module.settingsContent(context, sysAccess)
                    }

                    ModuleNotificationDataPointSettings(
                            moduleKey = moduleKey,
                            dataKeys = (data.keys + module.dataPoints().keys).toList()
                    )

                    if (isFap) {
                        Button(
                                onClick = {
                                    val intent =
                                            Intent(context, MonitorService::class.java)
                                                    .setAction("com.extensionbox.app.FAP_INCREMENT")
                                    context.startService(intent)
                                    fapLocalUpdateTick += 1
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(id = R.string.log_action))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
private fun AppUsageBatterySection(
        appUsageData: Map<String, String>,
        batteryHistory: List<ModuleDataEntity>
) {
    val appRows =
            remember(appUsageData) {
                appUsageData
                        .filterKeys { it.startsWith("usage.") }
                        .mapNotNull { (key, value) ->
                            val appName = key.removePrefix("usage.").trim()
                            val durationMs = parseDurationToMillis(value)
                            if (appName.isNotEmpty() && durationMs > 0) {
                                AppUsageRow(
                                        appName = appName,
                                        durationText = value,
                                        durationMs = durationMs
                                )
                            } else {
                                null
                            }
                        }
                        .sortedByDescending { it.durationMs }
                        .take(8)
            }

    if (appRows.isEmpty()) return

    val batteryDrop = remember(batteryHistory) { estimateBatteryDropPercent(batteryHistory) }
    val totalUsageMs = appRows.sumOf { it.durationMs }

    Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    ) {
        Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                    text = "App Usage & Battery Impact",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
            )

            val dropText =
                    if (batteryDrop > 0f) String.format("%.1f%%", batteryDrop) else "No drop yet"
            Text(
                    text = "Estimated from battery drop in this history window: $dropText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            appRows.forEach { row ->
                val usageRatio =
                        if (totalUsageMs > 0L) row.durationMs.toFloat() / totalUsageMs.toFloat()
                        else 0f
                val estimatedBattery = batteryDrop * usageRatio
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = row.appName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                                text = "Usage: ${row.durationText}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                            text =
                                    if (batteryDrop > 0f) String.format("~%.1f%%", estimatedBattery)
                                    else "—",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

private data class AppUsageRow(val appName: String, val durationText: String, val durationMs: Long)

private fun parseDurationToMillis(value: String): Long {
    val pattern = Regex("(\\d+)\\s*([dhms])")
    val matches = pattern.findAll(value.lowercase())
    var totalMs = 0L
    matches.forEach { match ->
        val amount = match.groupValues[1].toLongOrNull() ?: 0L
        when (match.groupValues[2]) {
            "d" -> totalMs += amount * 24L * 60L * 60L * 1000L
            "h" -> totalMs += amount * 60L * 60L * 1000L
            "m" -> totalMs += amount * 60L * 1000L
            "s" -> totalMs += amount * 1000L
        }
    }
    return totalMs
}

private fun estimateBatteryDropPercent(history: List<ModuleDataEntity>): Float {
    if (history.size < 2) return 0f
    val first = history.first().data["battery.level"]?.removeSuffix("%")?.trim()?.toFloatOrNull()
    val last = history.last().data["battery.level"]?.removeSuffix("%")?.trim()?.toFloatOrNull()
    if (first == null || last == null) return 0f
    return max(0f, first - last)
}

@Composable
private fun ModuleNotificationDataPointSettings(moduleKey: String, dataKeys: List<String>) {
    val context = LocalContext.current
    val normalizedKeys =
            remember(dataKeys) { dataKeys.map { it.substringAfterLast('.') }.distinct().sorted() }

    if (normalizedKeys.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
                text = "Notification Fields",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
        )

        normalizedKeys.forEach { rawKey ->
            var isVisible by
                    remember(moduleKey, rawKey) {
                        mutableStateOf(
                                Prefs.isModuleDataPointVisibleInNotif(context, moduleKey, rawKey)
                        )
                    }

            Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                        text = rawKey.replace("_", " ").replaceFirstChar { it.uppercase() },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                )

                Switch(
                        checked = isVisible,
                        onCheckedChange = { enabled ->
                            isVisible = enabled
                            Prefs.setModuleDataPointVisibleInNotif(
                                    context,
                                    moduleKey,
                                    rawKey,
                                    enabled
                            )
                        }
                )
            }
        }
    }
}
