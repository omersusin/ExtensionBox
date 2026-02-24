package com.extensionbox.app.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.extensionbox.app.MonitorService
import com.extensionbox.app.Prefs
import com.extensionbox.app.db.ModuleDataEntity
import com.extensionbox.app.ui.ModuleRegistry
import com.extensionbox.app.ui.viewmodel.DashboardViewModel
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel = viewModel()) {
    val context = LocalContext.current
    val isRunning by viewModel.isRunning.collectAsState()
    val activeCount by viewModel.activeCount.collectAsState()
    val dashData by viewModel.dashData.collectAsState()
    val historyData by viewModel.historyData.collectAsState()
    val visibleModules by viewModel.visibleModules.collectAsState()

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.updateOrder(from.index, to.index)
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp, top = 16.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "status_header") {
                SystemPulseHero(isRunning, activeCount, dashData, context)
            }

            if (!isRunning) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Engine is offline.\nTap the pulse to start.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(visibleModules, key = { it }) { key ->
                    val data = dashData[key]
                    if (data != null) {
                        ReorderableItem(reorderableState, key = key) { isDragging ->
                            val elevation by animateDpAsState(if (isDragging) 12.dp else 0.dp, label = "elevation")
                            
                            KernelCard(
                                key = key,
                                data = data,
                                history = historyData[key] ?: emptyList(),
                                isExpanded = viewModel.isExpanded(key),
                                onExpandToggle = { viewModel.toggleExpansion(key) },
                                reorderableItemScope = this,
                                modifier = Modifier
                                    .graphicsLayer {
                                        this.shadowElevation = elevation.toPx()
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SystemPulseHero(isRunning: Boolean, activeCount: Int, dashData: Map<String, Map<String, String>>, context: android.content.Context) {
    val battery = dashData["battery"]?.get("battery.level")?.removeSuffix("%")?.toIntOrNull() ?: 0
    val temp = dashData["battery"]?.get("battery.temp") ?: "--"
    val cpu = dashData["cpu_ram"]?.get("cpu.usage")?.removeSuffix("%")?.toIntOrNull() ?: 0

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = if (isRunning) "System Pulse" else "System Idle",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isRunning) "All systems operational" else "Engine paused",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        if (isRunning) {
                            val intent = Intent(context, MonitorService::class.java).setAction(MonitorService.ACTION_STOP)
                            context.startService(intent)
                        } else {
                            val intent = Intent(context, MonitorService::class.java)
                            ContextCompat.startForegroundService(context, intent)
                        }
                    },
                    modifier = Modifier.size(48.dp).background(
                        if (isRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    )
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PulseIndicator(label = "Battery", value = "$battery%", progress = battery / 100f, color = MaterialTheme.colorScheme.primary)
                PulseIndicator(label = "CPU Load", value = "$cpu%", progress = cpu / 100f, color = MaterialTheme.colorScheme.secondary)
                PulseIndicator(label = "Thermal", value = temp, progress = 0.4f, color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
fun PulseIndicator(label: String, value: String, progress: Float, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
            CircularProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 6.dp,
                color = color,
                trackColor = color.copy(alpha = 0.1f),
                strokeCap = StrokeCap.Round
            )
            Text(text = value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun KernelCard(
    key: String, 
    data: Map<String, String>, 
    history: List<ModuleDataEntity>,
    isExpanded: Boolean, 
    onExpandToggle: () -> Unit,
    reorderableItemScope: ReorderableCollectionItemScope,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val icon = ModuleRegistry.iconFor(key)
    val name = ModuleRegistry.nameFor(key)
    val primaryValue = data.values.firstOrNull() ?: ""

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onExpandToggle() },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
        border = if (isExpanded) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                with(reorderableItemScope) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp).draggableHandle(),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }
                Spacer(Modifier.width(12.dp))
                
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                
                Spacer(Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (!isExpanded) {
                        Text(text = primaryValue, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    }
                }

                if (history.isNotEmpty() && !isExpanded) {
                    Box(modifier = Modifier.width(60.dp).height(30.dp)) {
                        Sparkline(
                            points = extractPoints(key, history),
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            strokeWidth = 1.5.dp
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 8.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    if (history.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ) {
                            Box(modifier = Modifier.padding(12.dp)) {
                                Sparkline(
                                    points = extractPoints(key, history),
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.primary,
                                    fillGradient = true
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // Stats Grid
                    val items = data.toList()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items.chunked(2).forEach { rowItems ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                rowItems.forEach { pair ->
                                    val label = pair.first.substringAfterLast('.').replace("_", " ").replaceFirstChar { it.uppercase() }
                                    StatItem(label = label, value = pair.second, modifier = Modifier.weight(1f))
                                }
                                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    if (key == "fap") {
                        Spacer(Modifier.height(16.dp))
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
                            Text("Log Action")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, modifier: Modifier) {
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

fun extractPoints(key: String, history: List<ModuleDataEntity>): List<Float> {
    return history.mapNotNull { entity ->
        val raw = when (key) {
            "battery" -> entity.data["battery.level"]?.removeSuffix("%")
            "cpu_ram" -> entity.data["cpu.usage"]?.removeSuffix("%")
            "network" -> entity.data["net.down_speed"]?.substringBefore(" ")
            else -> null
        }
        raw?.toFloatOrNull()
    }
}

@Composable
fun Sparkline(
    points: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: androidx.compose.ui.unit.Dp = 2.dp,
    fillGradient: Boolean = false
) {
    if (points.size < 2) return
    val min = points.minOrNull() ?: 0f
    val max = points.maxOrNull() ?: 1f
    val range = if (max - min == 0f) 1f else max - min

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val stepX = width / (points.size - 1)

        val path = Path()
        points.forEachIndexed { index, value ->
            val x = index * stepX
            val y = height - ((value - min) / range * height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        if (fillGradient) {
            val fillPath = Path().apply {
                addPath(path)
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.3f), Color.Transparent)
                )
            )
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}