package com.extensionbox.app.modules

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import com.extensionbox.app.Fmt
import com.extensionbox.app.Prefs
import com.extensionbox.app.R
import com.extensionbox.app.SystemAccess
import com.extensionbox.app.ui.components.SettingSlider
import com.extensionbox.app.ui.components.SettingSwitch
import java.util.LinkedHashMap

class RamModule : Module {
    private var ctx: Context? = null
    private var sys: SystemAccess? = null
    private var running = false

    private var ramUsed: Long = 0
    private var ramTotal: Long = 0
    private var ramAvail: Long = 0
    private var topProcs = listOf<Triple<String, String, String>>()

    override fun key(): String = "ram"
    override fun name(): String = ctx?.getString(R.string.ram_module_name) ?: "RAM"
    override fun description(): String = ctx?.getString(R.string.ram_module_description) ?: "Memory status and running processes"
    override fun defaultEnabled(): Boolean = true
    override fun alive(): Boolean = running
    override fun priority(): Int = 16
    override fun hasSettings(): Boolean = true

    override fun tickIntervalMs(): Int = ctx?.let { Prefs.getInt(it, "ram_interval", 5000) } ?: 5000

    override fun start(ctx: Context, sys: SystemAccess) {
        this.ctx = ctx
        this.sys = sys
        running = true
        tick()
    }

    override fun stop() {
        running = false
        sys = null
    }

    override fun tick() {
        try {
            val am = ctx?.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            ramTotal = mi.totalMem
            ramAvail = mi.availMem
            ramUsed = ramTotal - ramAvail
            
            sys?.let { s ->
                topProcs = s.getRunningProcesses()
            }
        } catch (ignored: Exception) {
        }
    }

    override fun compact(): String {
        val ramPct = if (ramTotal > 0) ramUsed * 100f / ramTotal else 0f
        return ctx?.getString(R.string.ram_module_compact_text, ramPct.toInt(), Fmt.bytes(ramUsed)) ?: ""
    }

    override fun detail(): String {
        val c = ctx ?: return ""
        val ramPct = if (ramTotal > 0) ramUsed * 100f / ramTotal else 0f
        val sb = StringBuilder()
        sb.append(c.getString(R.string.ram_module_usage, ramPct.toInt(), Fmt.bytes(ramUsed), Fmt.bytes(ramTotal)))
        sb.append(c.getString(R.string.ram_module_available, Fmt.bytes(ramAvail)))
        
        if (topProcs.isNotEmpty()) {
            sb.append(c.getString(R.string.ram_module_top_processes))
            topProcs.forEach { (name, cpu, mem) ->
                sb.append(c.getString(R.string.ram_module_process_line, name.take(15).padEnd(16), cpu, mem))
            }
        }
        
        return sb.toString()
    }

    override fun dataPoints(): LinkedHashMap<String, String> {
        val d = LinkedHashMap<String, String>()
        val ramPct = if (ramTotal > 0) ramUsed * 100f / ramTotal else 0f
        d["ram.used"] = Fmt.bytes(ramUsed)
        d["ram.total"] = Fmt.bytes(ramTotal)
        d["ram.available"] = Fmt.bytes(ramAvail)
        d["ram.percentage"] = "${ramPct.toInt()}%"
        return d
    }

    @androidx.compose.runtime.Composable
    override fun composableContent(ctx: android.content.Context, sys: com.extensionbox.app.SystemAccess) {
        if (topProcs.isEmpty()) {
            Text(
                ctx.getString(R.string.ram_module_no_process_data),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(ctx.getString(R.string.ram_module_process_header), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(ctx.getString(R.string.ram_module_cpu_header), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
                Text(ctx.getString(R.string.ram_module_ram_header), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
            }
            
            topProcs.take(5).forEach { (name, cpu, mem) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(cpu, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
                    Text(mem, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    override fun settingsContent(ctx: android.content.Context, sys: com.extensionbox.app.SystemAccess) {
        var interval by remember { 
            mutableStateOf(Prefs.getInt(ctx, "ram_interval", 5000).toFloat()) 
        }
        
        Column {
            SettingSlider(
                label = ctx.getString(R.string.ram_module_update_interval),
                value = interval,
                valueRange = 1000f..60000f,
                onValueChange = {
                    interval = it
                    Prefs.setInt(ctx, "ram_interval", it.toInt())
                },
                formatter = { ctx.getString(R.string.ram_module_interval_formatter, it.toInt() / 1000) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            var alertOn by remember { 
                mutableStateOf(Prefs.getBool(ctx, "cpu_ram_alert", false)) 
            }
            SettingSwitch(
                label = ctx.getString(R.string.ram_module_high_ram_alert),
                checked = alertOn,
                onCheckedChange = {
                    alertOn = it
                    Prefs.setBool(ctx, "cpu_ram_alert", it)
                }
            )
            
            if (alertOn) {
                var ramThresh by remember { mutableStateOf(Prefs.getInt(ctx, "cpu_ram_thresh", 90).toFloat()) }
                SettingSlider(
                    label = ctx.getString(R.string.ram_module_ram_alert_threshold),
                    value = ramThresh,
                    valueRange = 50f..98f,
                    onValueChange = {
                        ramThresh = it
                        Prefs.setInt(ctx, "cpu_ram_thresh", it.toInt())
                    },
                    formatter = { ctx.getString(R.string.ram_module_threshold_formatter, it.toInt()) }
                )
            }
        }
    }

    override fun checkAlerts(ctx: Context) {
        val alertOn = Prefs.getBool(ctx, "cpu_ram_alert", false)
        val thresh = Prefs.getInt(ctx, "cpu_ram_thresh", 90)
        val fired = Prefs.getBool(ctx, "cpu_ram_alert_fired", false)
        val ramPct = if (ramTotal > 0) ramUsed * 100f / ramTotal else 0f

        if (alertOn && ramPct >= thresh && !fired) {
            try {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(2003, NotificationCompat.Builder(ctx, "ebox_alerts")
                    .setSmallIcon(R.drawable.ic_notif)
                    .setContentTitle(ctx.getString(R.string.ram_module_high_ram_alert_title))
                    .setContentText(ctx.getString(R.string.ram_module_high_ram_alert_content, ramPct.toInt()))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true).build())
            } catch (ignored: Exception) {
            }
            Prefs.setBool(ctx, "cpu_ram_alert_fired", true)
        }
        if (fired && ramPct < thresh - 5) {
            Prefs.setBool(ctx, "cpu_ram_alert_fired", false)
        }
    }
}
