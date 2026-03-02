package com.extensionbox.app.modules

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.extensionbox.app.Fmt
import com.extensionbox.app.Prefs
import com.extensionbox.app.R
import com.extensionbox.app.SystemAccess
import com.extensionbox.app.ui.components.SettingSlider
import java.util.*

class AppUsageModule : Module {
    private var ctx: Context? = null
    private var running = false
    private var usageMap = mutableMapOf<String, Long>()

    override fun key(): String = "app_usage"
    override fun name(): String = ctx?.getString(R.string.app_usage_module_name) ?: "App Usage"
    override fun description(): String = ctx?.getString(R.string.app_usage_module_description) ?: "Time spent in each application"
    override fun defaultEnabled(): Boolean = true
    override fun alive(): Boolean = running
    override fun priority(): Int = 22
    override fun hasSettings(): Boolean = true

    override fun tickIntervalMs(): Int = ctx?.let { Prefs.getInt(it, "usage_interval", 30000) } ?: 30000

    override fun start(ctx: Context, sys: SystemAccess) {
        this.ctx = ctx
        running = true
        tick()
    }

    override fun stop() {
        running = false
    }

    override fun tick() {
        val c = ctx ?: return
        val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startTime = calendar.timeInMillis

        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, now)
        if (stats != null) {
            val newMap = mutableMapOf<String, Long>()
            for (s in stats) {
                if (s.totalTimeInForeground > 0) {
                    val pkg = s.packageName
                    newMap[pkg] = (newMap[pkg] ?: 0L) + s.totalTimeInForeground
                }
            }
            usageMap = newMap.toList().sortedByDescending { it.second }.take(10).toMap().toMutableMap()
        }
    }

    override fun compact(): String {
        val c = ctx ?: return ""
        val top = usageMap.maxByOrNull { it.value }
        val pm = ctx?.packageManager
        val name = top?.let { pkg -> 
            try { pm?.getApplicationLabel(pm.getApplicationInfo(pkg.key, 0))?.toString() } catch (e: Exception) { pkg.key.substringAfterLast('.') }
        }
        return if (name != null) c.getString(R.string.app_usage_module_compact_text, name, Fmt.duration(top.value)) else c.getString(R.string.app_usage_module_no_usage_data)
    }

    override fun detail(): String {
        val c = ctx ?: return ""
        val sb = StringBuilder()
        val pm = ctx?.packageManager
        sb.append(c.getString(R.string.app_usage_module_todays_app_usage))
        if (usageMap.isEmpty()) {
            sb.append(c.getString(R.string.app_usage_module_no_data_permission))
        } else {
            usageMap.toList().sortedByDescending { it.second }.forEach { (pkg, time) ->
                val name = try { pm?.getApplicationLabel(pm.getApplicationInfo(pkg, 0))?.toString() ?: pkg.substringAfterLast('.') } catch (e: Exception) { pkg.substringAfterLast('.') }
                sb.append(c.getString(R.string.app_usage_module_usage_line, name.padEnd(16), Fmt.duration(time)))
            }
        }
        return sb.toString()
    }

    override fun dataPoints(): LinkedHashMap<String, String> {
        val d = LinkedHashMap<String, String>()
        val pm = ctx?.packageManager
        usageMap.forEach { (pkg, time) ->
            val name = try { pm?.getApplicationLabel(pm.getApplicationInfo(pkg, 0))?.toString() ?: pkg.substringAfterLast('.') } catch (e: Exception) { pkg.substringAfterLast('.') }
            d["usage.$name"] = Fmt.duration(time)
        }
        return d
    }

    override fun checkAlerts(ctx: Context) {}

    @androidx.compose.runtime.Composable
    override fun dashboardContent(ctx: Context, sys: SystemAccess) {
        val hasPermission = remember { mutableStateOf(checkPermission(ctx)) }
        if (!hasPermission.value) return

        val sortedUsage = usageMap.toList().sortedByDescending { it.second }
        if (sortedUsage.isEmpty()) return

        val totalTime = sortedUsage.sumOf { it.second }
        val sortedTop = sortedUsage.take(5)
        val maxUsage = sortedTop.first().second.toFloat()
        val pm = ctx.packageManager

        Column(
            modifier = Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Total Usage Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text("Total Today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Fmt.duration(totalTime), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                }
                Text("${sortedUsage.size} Apps Used", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Simple Multi-Bar Graph
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                val colors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.secondary,
                    MaterialTheme.colorScheme.tertiary,
                    MaterialTheme.colorScheme.error,
                    MaterialTheme.colorScheme.primaryContainer
                )
                sortedTop.forEachIndexed { index, (_, time) ->
                    val weight = time.toFloat() / totalTime.toFloat()
                    if (weight > 0.01f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(weight)
                                .background(colors[index % colors.size])
                        )
                    }
                }
            }

            // App List
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                sortedTop.forEach { (pkg, time) ->
                    val appName = remember(pkg) {
                        try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
                        catch (e: Exception) { pkg.substringAfterLast('.') }
                    }
                    val icon = remember(pkg) {
                        try {
                            val drawable = pm.getApplicationIcon(pkg)
                            val bitmap = android.graphics.Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, android.graphics.Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(bitmap)
                            drawable.setBounds(0, 0, canvas.width, canvas.height)
                            drawable.draw(canvas)
                            bitmap.asImageBitmap()
                        } catch (e: Exception) { null }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (icon != null) {
                            Image(
                                bitmap = icon,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp).clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(appName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text(Fmt.duration(time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { time.toFloat() / maxUsage },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    override fun settingsContent(ctx: android.content.Context, sys: com.extensionbox.app.SystemAccess) {
        var interval by remember { mutableStateOf(Prefs.getInt(ctx, "usage_interval", 30000).toFloat()) }
        SettingSlider(
            label = ctx.getString(R.string.app_usage_module_update_interval),
            value = interval,
            onValueChange = {
                interval = it
                Prefs.setInt(ctx, "usage_interval", it.toInt())
            },
            valueRange = 10000f..300000f,
            formatter = { if (it >= 60000f) ctx.getString(R.string.app_usage_module_interval_formatter_minutes, it.toInt() / 60000) else ctx.getString(R.string.app_usage_module_interval_formatter_seconds, it.toInt() / 1000) }
        )
    }

    @androidx.compose.runtime.Composable
    override fun composableContent(ctx: Context, sys: SystemAccess) {
        val hasPermission = remember { mutableStateOf(checkPermission(ctx)) }
        
        if (!hasPermission.value) {
            Button(
                onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(ctx.getString(R.string.app_usage_module_grant_usage_access))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                dashboardContent(ctx, sys)
            }
        }
    }

    private fun checkPermission(ctx: Context): Boolean {
        val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), ctx.packageName)
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }
}
