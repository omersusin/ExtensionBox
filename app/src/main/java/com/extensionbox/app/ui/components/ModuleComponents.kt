package com.extensionbox.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.extensionbox.app.db.ModuleDataEntity

@Composable
fun StatItem(label: String, value: String, modifier: Modifier) {
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SettingSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
    formatter: (Float) -> String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = formatter(value),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun SettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.8f)
        )
    }
}

fun extractPoints(key: String, history: List<ModuleDataEntity>): List<Float> {
    return history.mapNotNull { entity ->
        when (key) {
            "battery"    -> entity.data["battery.level"]?.removeSuffix("%")?.toFloatOrNull()
            "cpu"        -> entity.data["cpu.usage"]?.removeSuffix("%")?.toFloatOrNull()
            "ram"        -> entity.data["ram.percentage"]?.removeSuffix("%")?.toFloatOrNull()
            "sleep"      -> entity.data["sleep.deep_percentage"]?.removeSuffix("%")?.toFloatOrNull()
            "network"    -> entity.data["net.download"]?.let { parseSpeedToKbps(it) }
            "data"       -> entity.data["data.today_total"]?.let { parseBytesToKb(it) }
            "unlock"     -> entity.data["unlock.today"]?.toFloatOrNull()
            "storage"    -> entity.data["storage.percentage"]?.removeSuffix("%")?.toFloatOrNull()
            "connection" -> entity.data["conn.rssi"]?.substringBefore(" ")?.toFloatOrNull()?.let { -it }
            "screen"     -> entity.data["screen.on_time"]?.let { parseDurationToMinutes(it) }
            "uptime"     -> entity.data["uptime.duration"]?.let { parseDurationToMinutes(it) }
            "steps"      -> entity.data["steps.today"]?.replace(",", "")?.toFloatOrNull()
            "speedtest"  -> entity.data["speedtest.download"]?.substringBefore(" ")?.toFloatOrNull()
            "habit"      -> entity.data["habit.today"]?.toFloatOrNull()
            else         -> null
        }
    }
}

fun parseSpeedToKbps(speed: String): Float? {
    val parts = speed.trim().split(" ")
    val value = parts.getOrNull(0)?.toFloatOrNull() ?: return null
    val unit = parts.getOrNull(1) ?: return value
    return when {
        unit.startsWith("MB") -> value * 1024f
        unit.startsWith("KB") -> value
        unit.startsWith("B")  -> value / 1024f
        else -> value
    }
}

fun parseBytesToKb(bytes: String): Float? {
    val parts = bytes.trim().split(" ")
    val value = parts.getOrNull(0)?.toFloatOrNull() ?: return null
    val unit = parts.getOrNull(1) ?: return value
    return when {
        unit.startsWith("GB") -> value * 1024f * 1024f
        unit.startsWith("MB") -> value * 1024f
        unit.startsWith("KB") -> value
        unit.startsWith("B")  -> value / 1024f
        else -> value
    }
}

fun parseDurationToMinutes(duration: String): Float? {
    var total = 0f
    Regex("""(\d+)d""").find(duration)?.groupValues?.get(1)?.toFloatOrNull()?.let { total += it * 1440f }
    Regex("""(\d+)h""").find(duration)?.groupValues?.get(1)?.toFloatOrNull()?.let { total += it * 60f }
    Regex("""(\d+)m""").find(duration)?.groupValues?.get(1)?.toFloatOrNull()?.let { total += it }
    Regex("""(\d+)s""").find(duration)?.groupValues?.get(1)?.toFloatOrNull()?.let { total += it / 60f }
    return if (total > 0f) total else null
}

@Composable
fun Sparkline(
    points: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: androidx.compose.ui.unit.Dp = 2.dp,
    fillGradient: Boolean = false,
    animate: Boolean = false
) {
    if (points.size < 2) return
    val min = points.minOrNull() ?: 0f
    val max = points.maxOrNull() ?: 1f
    val range = max - min

    val drawProgress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animate) {
            drawProgress.snapTo(0f)
            drawProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
            )
        }
    }
    val progress = drawProgress.value

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val stepX = width / (points.size - 1)

        val path = Path()
        points.forEachIndexed { index, value ->
            val x = index * stepX
            val y = if (range == 0f) {
                if (value > 0f) 0f else height
            } else {
                height - ((value - min) / range * height)
            }
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        clipRect(right = width * progress) {
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
}