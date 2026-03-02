package com.extensionbox.app.modules

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import com.extensionbox.app.Prefs
import com.extensionbox.app.R
import com.extensionbox.app.SystemAccess
import com.extensionbox.app.ui.components.SettingSlider
import com.extensionbox.app.ui.components.SettingSwitch
import java.util.LinkedHashMap

class UnlockModule : Module {

    private var ctx: Context? = null
    private var rcv: BroadcastReceiver? = null
    private var running = false
    private var dailyUnlocks = 0
    private var lastUnlockTime = 0L

    override fun key(): String = "unlock"
    override fun name(): String = ctx?.getString(R.string.unlock_module_name) ?: "Unlock Counter"
    override fun description(): String = ctx?.getString(R.string.unlock_module_description) ?: "Daily unlocks, detox tracking"
    override fun defaultEnabled(): Boolean = true
    override fun alive(): Boolean = running
    override fun priority(): Int = 60
    override fun hasSettings(): Boolean = true

    override fun tickIntervalMs(): Int = ctx?.let { Prefs.getInt(it, "ulk_interval", 10000) } ?: 10000

    override fun start(ctx: Context, sys: SystemAccess) {
        this.ctx = ctx
        dailyUnlocks = Prefs.getInt(ctx, "ulk_today", 0)
        
        rcv = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_USER_PRESENT) {
                    val now = System.currentTimeMillis()
                    val debounce = Prefs.getInt(ctx, "ulk_debounce", 5000)
                    if (now - lastUnlockTime > debounce) {
                        dailyUnlocks++
                        Prefs.setInt(ctx, "ulk_today", dailyUnlocks)
                        lastUnlockTime = now
                    }
                }
            }
        }
        ctx.registerReceiver(rcv, IntentFilter(Intent.ACTION_USER_PRESENT))
        running = true
    }

    override fun stop() {
        if (rcv != null && ctx != null) {
            try {
                ctx?.unregisterReceiver(rcv!!)
            } catch (ignored: Exception) {
            }
        }
        rcv = null
        running = false
    }

    override fun tick() {
        ctx?.let { dailyUnlocks = Prefs.getInt(it, "ulk_today", 0) }
    }

    override fun compact(): String = ctx?.getString(R.string.unlock_module_compact_text, dailyUnlocks) ?: ""

    override fun detail(): String {
        val c = ctx ?: return ""
        val y = c.let { Prefs.getInt(it, "ulk_yesterday", 0) } ?: 0
        var s = c.getString(R.string.unlock_module_today, dailyUnlocks)
        if (y > 0) {
            val diff = dailyUnlocks - y
            val pct = if (y > 0) kotlin.math.abs(diff * 100 / y) else 0
            val cmp = if (diff <= 0) c.getString(R.string.unlock_module_less_than_yesterday, pct) else c.getString(R.string.unlock_module_more_than_yesterday, pct)
            s += "\n   $cmp"
        }
        return s
    }

    override fun dataPoints(): LinkedHashMap<String, String> {
        val d = LinkedHashMap<String, String>()
        d["unlock.today"] = dailyUnlocks.toString()
        d["unlock.yesterday"] = (ctx?.let { Prefs.getInt(it, "ulk_yesterday", 0) } ?: 0).toString()
        return d
    }

    @androidx.compose.runtime.Composable
    override fun settingsContent(ctx: android.content.Context, sys: com.extensionbox.app.SystemAccess) {
        var interval by remember { mutableStateOf(Prefs.getInt(ctx, "ulk_interval", 10000).toFloat()) }
        
        Column {
            SettingSlider(
                label = ctx.getString(R.string.unlock_module_update_interval),
                value = interval,
                onValueChange = {
                    interval = it
                    Prefs.setInt(ctx, "ulk_interval", it.toInt())
                },
                valueRange = 1000f..60000f,
                formatter = { "${it.toInt() / 1000}s" }
            )

            androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            var ulLimit by remember { mutableStateOf(Prefs.getInt(ctx, "ulk_daily_limit", 0).toFloat()) }
            SettingSlider(
                label = ctx.getString(R.string.unlock_module_daily_unlock_limit),
                value = ulLimit,
                valueRange = 0f..200f,
                onValueChange = {
                    ulLimit = it
                    Prefs.setInt(ctx, "ulk_daily_limit", it.toInt())
                },
                formatter = { if (it == 0f) ctx.getString(R.string.unlock_module_no_limit) else "${it.toInt()}${ctx.getString(R.string.unlock_module_times_unit)}" }
            )
            if (ulLimit > 0) {
                var limitAlert by remember { mutableStateOf(Prefs.getBool(ctx, "ulk_limit_alert", true)) }
                SettingSwitch(
                    label = ctx.getString(R.string.unlock_module_alert_on_limit),
                    checked = limitAlert,
                    onCheckedChange = {
                        limitAlert = it
                        Prefs.setBool(ctx, "ulk_limit_alert", it)
                    }
                )
            }
            var debounce by remember { mutableStateOf(Prefs.getInt(ctx, "ulk_debounce", 5000).toFloat()) }
            SettingSlider(
                label = ctx.getString(R.string.unlock_module_debounce),
                value = debounce,
                valueRange = 0f..30000f,
                onValueChange = {
                    debounce = it
                    Prefs.setInt(ctx, "ulk_debounce", it.toInt())
                },
                formatter = { "${it.toInt()}${ctx.getString(R.string.unlock_module_ms_unit)}" }
            )
        }
    }

    override fun checkAlerts(ctx: Context) {
        val limit = Prefs.getInt(ctx, "ulk_daily_limit", 0)
        if (limit > 0 && dailyUnlocks >= limit) {
            val alertOn = Prefs.getBool(ctx, "ulk_limit_alert", true)
            val fired = Prefs.getBool(ctx, "ulk_limit_fired", false)
            if (alertOn && !fired) {
                try {
                    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(2006, NotificationCompat.Builder(ctx, "ebox_alerts")
                        .setSmallIcon(R.drawable.ic_notif)
                        .setContentTitle(ctx.getString(R.string.unlock_module_limit_reached_title))
                        .setContentText(ctx.getString(R.string.unlock_module_limit_reached_content, dailyUnlocks))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true).build())
                } catch (ignored: Exception) {
                }
                Prefs.setBool(ctx, "ulk_limit_fired", true)
            }
        }
    }

    override fun reset() {
        dailyUnlocks = 0
        ctx?.let { Prefs.setInt(it, "ulk_today", 0) }
    }
}
