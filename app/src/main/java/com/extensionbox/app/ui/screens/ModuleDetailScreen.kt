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
import com.extensionbox.app.ui.ModuleRegistry
import com.extensionbox.app.ui.components.*
import com.extensionbox.app.ui.viewmodel.DashboardViewModel

@Composable
fun ModuleDetailScreen(
    moduleKey: String,
    viewModel: DashboardViewModel
) {
    val context = LocalContext.current
    val dashData = viewModel.dashData.collectAsState().value
    val historyData = viewModel.historyData.collectAsState().value
    val sysAccess = viewModel.sysAccess.collectAsState().value

    val data = dashData[moduleKey] ?: emptyMap()
    val history = historyData[moduleKey] ?: emptyList()
    val module = com.extensionbox.app.ui.ModuleRegistry.getModule(moduleKey)
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (history.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timeline, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(id = R.string.history_15m), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
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
                                val label = pair.first.substringAfterLast('.').replace("_", " ").replaceFirstChar { it.uppercase() }
                                StatItem(label = label, value = pair.second, modifier = Modifier.weight(1f))
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
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
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
                                val intent = Intent(context, MonitorService::class.java)
                                    .setAction("com.extensionbox.app.FAP_INCREMENT")
                                context.startService(intent)
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
private fun ModuleNotificationDataPointSettings(
    moduleKey: String,
    dataKeys: List<String>
) {
    val context = LocalContext.current
    val normalizedKeys = remember(dataKeys) {
        dataKeys
            .map { it.substringAfterLast('.') }
            .distinct()
            .sorted()
    }

    if (normalizedKeys.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Notification Fields",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        normalizedKeys.forEach { rawKey ->
            var isVisible by remember(moduleKey, rawKey) {
                mutableStateOf(Prefs.isModuleDataPointVisibleInNotif(context, moduleKey, rawKey))
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
                        Prefs.setModuleDataPointVisibleInNotif(context, moduleKey, rawKey, enabled)
                    }
                )
            }
        }
    }
}
