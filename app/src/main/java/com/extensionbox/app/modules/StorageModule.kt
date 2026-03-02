package com.extensionbox.app.modules

import android.app.NotificationManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import com.extensionbox.app.Fmt
import com.extensionbox.app.Prefs
import com.extensionbox.app.R
import com.extensionbox.app.SystemAccess
import com.extensionbox.app.ui.components.SettingSlider
import com.extensionbox.app.ui.components.SettingSwitch
import java.util.LinkedHashMap
import java.util.Locale

class StorageModule : Module {

    private var ctx: Context? = null
    private var running = false
    private var intUsed = 0L
    private var intFree = 0L
    private var intTotal = 0L
    
    // I/O Stats
    private var sys: SystemAccess? = null
    private var prevIoStats = mapOf<String, Long>()
    private var readSpeed = 0L
    private var writeSpeed = 0L
    private var prevTime = 0L

    override fun key(): String = "storage"
    override fun name(): String = ctx?.getString(R.string.storage_module_name) ?: "Storage"
    override fun description(): String = ctx?.getString(R.string.storage_module_description) ?: "Internal storage usage"
    override fun defaultEnabled(): Boolean = false
    override fun alive(): Boolean = running
    override fun priority(): Int = 85
    override fun hasSettings(): Boolean = true

    override fun tickIntervalMs(): Int = ctx?.let { Prefs.getInt(it, "sto_interval", 10000) } ?: 10000

    override fun start(ctx: Context, sys: SystemAccess) {
        this.ctx = ctx
        this.sys = sys
        prevTime = SystemClock.elapsedRealtime()
        if (sys.isEnhanced()) {
            prevIoStats = sys.getDiskIoStats()
        }
        running = true
    }

    override fun stop() {
        running = false
        sys = null
    }

    override fun tick() {
        try {
            val sf = StatFs(Environment.getDataDirectory().path)
            intTotal = sf.totalBytes
            intFree = sf.availableBytes
            intUsed = intTotal - intFree
            
            val now = SystemClock.elapsedRealtime()
            val dtMs = now - prevTime
            
            sys?.let { s ->
                if (s.isEnhanced() && dtMs > 0) {
                    val currentIo = s.getDiskIoStats()
                    var rDelta = 0L
                    var wDelta = 0L
                    
                    currentIo.forEach { (key, value) ->
                        val prev = prevIoStats[key] ?: value
                        if (key.endsWith("_read")) rDelta += (value - prev).coerceAtLeast(0)
                        if (key.endsWith("_write")) wDelta += (value - prev).coerceAtLeast(0)
                    }
                    
                    readSpeed = rDelta * 1000 / dtMs
                    writeSpeed = wDelta * 1000 / dtMs
                    prevIoStats = currentIo
                }
            }
            prevTime = now
        } catch (ignored: Exception) {
        }
    }

    override fun compact(): String = ctx?.getString(R.string.storage_module_compact_text, Fmt.bytes(intUsed), Fmt.bytes(intTotal)) ?: ""

    override fun detail(): String {
        val c = ctx ?: return ""
        val pct = if (intTotal > 0) intUsed * 100f / intTotal else 0f
        val sb = StringBuilder()
        sb.append(c.getString(R.string.storage_module_internal, Fmt.bytes(intUsed), Fmt.bytes(intTotal), pct))
        sb.append(c.getString(R.string.storage_module_free, Fmt.bytes(intFree)))
        if (readSpeed > 0 || writeSpeed > 0) {
            sb.append(c.getString(R.string.storage_module_io, Fmt.speed(readSpeed), Fmt.speed(writeSpeed)))
        }
        return sb.toString().trim()
    }

    override fun dataPoints(): LinkedHashMap<String, String> {
        val pct = if (intTotal > 0) intUsed * 100f / intTotal else 0f
        val d = LinkedHashMap<String, String>()
        d["storage.used"] = Fmt.bytes(intUsed)
        d["storage.free"] = Fmt.bytes(intFree)
        d["storage.total"] = Fmt.bytes(intTotal)
        d["storage.percentage"] = String.format(Locale.US, "%.1f%%", pct)
        if (readSpeed > 0 || writeSpeed > 0) {
            d["storage.io_read"] = Fmt.speed(readSpeed)
            d["storage.io_write"] = Fmt.speed(writeSpeed)
        }
        return d
    }

    @androidx.compose.runtime.Composable
    override fun settingsContent(ctx: android.content.Context, sys: com.extensionbox.app.SystemAccess) {
        var interval by remember { mutableStateOf(Prefs.getInt(ctx, "sto_interval", 10000).toFloat()) }
        
        Column {
            SettingSlider(
                label = ctx.getString(R.string.storage_module_update_interval),
                value = interval,
                onValueChange = {
                    interval = it
                    Prefs.setInt(ctx, "sto_interval", it.toInt())
                },
                valueRange = 10000f..600000f,
                formatter = { if (it >= 60000f) ctx.getString(R.string.storage_module_interval_formatter_minutes, it.toInt() / 60000) else ctx.getString(R.string.storage_module_interval_formatter_seconds, it.toInt() / 1000) }
            )

            androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            var stoAlert by remember { mutableStateOf(Prefs.getBool(ctx, "sto_low_alert", true)) }
            SettingSwitch(
                label = ctx.getString(R.string.storage_module_low_storage_alert),
                checked = stoAlert,
                onCheckedChange = {
                    stoAlert = it
                    Prefs.setBool(ctx, "sto_low_alert", it)
                }
            )
            if (stoAlert) {
                var stoThresh by remember { mutableStateOf(Prefs.getInt(ctx, "sto_low_thresh_mb", 1000).toFloat()) }
                SettingSlider(
                    label = ctx.getString(R.string.storage_module_low_alert_threshold),
                    value = stoThresh,
                    valueRange = 100f..5000f,
                    onValueChange = {
                        stoThresh = it
                        Prefs.setInt(ctx, "sto_low_thresh_mb", it.toInt())
                    },
                    formatter = { ctx.getString(R.string.storage_module_threshold_formatter, it.toInt()) }
                )
            }
        }
    }

    override fun checkAlerts(ctx: Context) {
        val on = Prefs.getBool(ctx, "sto_low_alert", true)
        val threshMb = Prefs.getInt(ctx, "sto_low_thresh_mb", 1000)
        val fired = Prefs.getBool(ctx, "sto_low_alert_fired", false)
        val threshBytes = threshMb * 1024L * 1024L

        if (on && intFree < threshBytes && intFree > 0 && !fired) {
            try {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(2007, NotificationCompat.Builder(ctx, "ebox_alerts")
                    .setSmallIcon(R.drawable.ic_notif)
                    .setContentTitle(ctx.getString(R.string.storage_module_low_storage_title))
                    .setContentText(ctx.getString(R.string.storage_module_low_storage_content, Fmt.bytes(intFree)))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true).build())
            } catch (ignored: Exception) {
            }
            Prefs.setBool(ctx, "sto_low_alert_fired", true)
        }
        if (fired && intFree > threshBytes + 500L * 1024 * 1024) {
            Prefs.setBool(ctx, "sto_low_alert_fired", false)
        }
    }
}
