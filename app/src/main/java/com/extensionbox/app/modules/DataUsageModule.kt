package com.extensionbox.app.modules

import android.app.NotificationManager
import android.content.Context
import android.net.TrafficStats
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
import java.util.Calendar
import java.util.LinkedHashMap
import java.util.Locale

class DataUsageModule : Module {

    private var ctx: Context? = null
    private var running = false
    private var prevTotal = 0L
    private var prevMobile = 0L
    private var dailyTotal = 0L
    private var dailyWifi = 0L
    private var dailyMobile = 0L
    private var monthTotal = 0L
    private var monthWifi = 0L
    private var monthMobile = 0L

    override fun key(): String = "data"
    override fun name(): String = ctx?.getString(R.string.data_usage_module_name) ?: "Data Usage"
    override fun description(): String = ctx?.getString(R.string.data_usage_module_description) ?: "Daily & monthly, WiFi & mobile"
    override fun defaultEnabled(): Boolean = true
    override fun alive(): Boolean = running
    override fun priority(): Int = 50
    override fun hasSettings(): Boolean = true

    override fun tickIntervalMs(): Int = ctx?.let { Prefs.getInt(it, "dat_interval", 60000) } ?: 60000

    override fun start(ctx: Context, sys: SystemAccess) {
        this.ctx = ctx
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        prevTotal = if (rx != TrafficStats.UNSUPPORTED.toLong()) rx + tx else 0
        val mrx = TrafficStats.getMobileRxBytes()
        val mtx = TrafficStats.getMobileTxBytes()
        prevMobile = if (mrx != TrafficStats.UNSUPPORTED.toLong()) mrx + mtx else 0

        dailyTotal = Prefs.getLong(ctx, "dat_daily_total", 0)
        dailyWifi = Prefs.getLong(ctx, "dat_daily_wifi", 0)
        dailyMobile = Prefs.getLong(ctx, "dat_daily_mobile", 0)
        monthTotal = Prefs.getLong(ctx, "dat_month_total", 0)
        monthWifi = Prefs.getLong(ctx, "dat_month_wifi", 0)
        monthMobile = Prefs.getLong(ctx, "dat_month_mobile", 0)
        running = true
    }

    override fun stop() {
        running = false
    }

    override fun tick() {
        val c = ctx ?: return
        rollover()

        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        if (rx == TrafficStats.UNSUPPORTED.toLong()) return
        val total = rx + tx

        val mrx = TrafficStats.getMobileRxBytes()
        val mtx = TrafficStats.getMobileTxBytes()
        val mobile = if (mrx != TrafficStats.UNSUPPORTED.toLong()) mrx + mtx else 0

        if (prevTotal > 0 && total >= prevTotal) {
            val dt = total - prevTotal
            var dm = mobile - prevMobile
            if (dm < 0) dm = 0
            var dw = dt - dm
            if (dw < 0) dw = 0

            dailyTotal += dt
            dailyMobile += dm
            dailyWifi += dw
            monthTotal += dt
            monthMobile += dm
            monthWifi += dw

            Prefs.setLong(c, "dat_daily_total", dailyTotal)
            Prefs.setLong(c, "dat_daily_wifi", dailyWifi)
            Prefs.setLong(c, "dat_daily_mobile", dailyMobile)
            Prefs.setLong(c, "dat_month_total", monthTotal)
            Prefs.setLong(c, "dat_month_wifi", monthWifi)
            Prefs.setLong(c, "dat_month_mobile", monthMobile)
        }
        prevTotal = total
        prevMobile = mobile
    }

    private fun rollover() {
        val c = ctx ?: return
        val cal = Calendar.getInstance()
        val day = cal.get(Calendar.DAY_OF_YEAR)
        val lastDay = Prefs.getInt(c, "dat_last_day", -1)
        if (lastDay != -1 && lastDay != day) {
            dailyTotal = 0
            dailyWifi = 0
            dailyMobile = 0
            Prefs.setLong(c, "dat_daily_total", 0)
            Prefs.setLong(c, "dat_daily_wifi", 0)
            Prefs.setLong(c, "dat_daily_mobile", 0)
        }
        Prefs.setInt(c, "dat_last_day", day)

        val billingDay = Prefs.getInt(c, "dat_billing_day", 1)
        val dom = cal.get(Calendar.DAY_OF_MONTH)
        val lastBillCheck = Prefs.getInt(c, "dat_last_bill", -1)
        if (dom == billingDay && lastBillCheck != day) {
            monthTotal = 0
            monthWifi = 0
            monthMobile = 0
            Prefs.setLong(c, "dat_month_total", 0)
            Prefs.setLong(c, "dat_month_wifi", 0)
            Prefs.setLong(c, "dat_month_mobile", 0)
            Prefs.setBool(c, "dat_plan_alert_fired", false)
        }
        Prefs.setInt(c, "dat_last_bill", day)
    }

    override fun compact(): String = ctx?.getString(R.string.data_usage_module_compact_text, Fmt.bytes(dailyTotal)) ?: ""

    override fun detail(): String {
        val sb = StringBuilder()
        val c = ctx
        val breakdown = if (c != null) Prefs.getBool(c, "dat_show_breakdown", true) else true

        if (breakdown) {
            sb.append(c?.getString(R.string.data_usage_module_today_with_breakdown, Fmt.bytes(dailyTotal), Fmt.bytes(dailyWifi), Fmt.bytes(dailyMobile)))
        } else {
            sb.append(c?.getString(R.string.data_usage_module_today_simple, Fmt.bytes(dailyTotal)))
        }

        sb.append(c?.getString(R.string.data_usage_module_month, Fmt.bytes(monthTotal)))

        val planMb = if (c != null) Prefs.getInt(c, "dat_plan_limit", 0) else 0
        if (planMb > 0) {
            val planBytes = planMb * 1024L * 1024L
            val pct = monthTotal * 100f / planBytes
            sb.append(String.format(Locale.US, c?.getString(R.string.data_usage_module_month_plan_usage) ?: "", Fmt.bytes(planBytes), pct))
        }
        return sb.toString()
    }

    override fun dataPoints(): LinkedHashMap<String, String> {
        val d = LinkedHashMap<String, String>()
        d["data.today_total"] = Fmt.bytes(dailyTotal)
        d["data.today_wifi"] = Fmt.bytes(dailyWifi)
        d["data.today_mobile"] = Fmt.bytes(dailyMobile)
        d["data.month_total"] = Fmt.bytes(monthTotal)
        return d
    }

    @androidx.compose.runtime.Composable
    override fun settingsContent(ctx: android.content.Context, sys: com.extensionbox.app.SystemAccess) {
        var interval by remember { mutableStateOf(Prefs.getInt(ctx, "dat_interval", 60000).toFloat()) }
        
        Column {
            SettingSlider(
                label = ctx.getString(R.string.data_usage_module_update_interval),
                value = interval,
                onValueChange = {
                    interval = it
                    Prefs.setInt(ctx, "dat_interval", it.toInt())
                },
                valueRange = 10000f..600000f,
                formatter = { if (it >= 60000f) "${it.toInt() / 60000}m" else "${it.toInt() / 1000}s" }
            )

            androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            var split by remember { mutableStateOf(Prefs.getBool(ctx, "dat_show_breakdown", true)) }
            SettingSwitch(
                label = ctx.getString(R.string.data_usage_module_wifi_mobile_split),
                checked = split,
                onCheckedChange = {
                    split = it
                    Prefs.setBool(ctx, "dat_show_breakdown", it)
                }
            )
            var planLimit by remember { mutableStateOf(Prefs.getInt(ctx, "dat_plan_limit", 0).toFloat()) }
            SettingSlider(
                label = ctx.getString(R.string.data_usage_module_monthly_plan_limit),
                value = planLimit,
                valueRange = 0f..10000f,
                onValueChange = {
                    planLimit = it
                    Prefs.setInt(ctx, "dat_plan_limit", it.toInt())
                },
                formatter = { if (it == 0f) ctx.getString(R.string.data_usage_module_no_limit) else "${it.toInt()}${ctx.getString(R.string.data_usage_module_megabytes)}" }
            )
            if (planLimit > 0) {
                var alertPct by remember { mutableStateOf(Prefs.getInt(ctx, "dat_plan_alert_pct", 90).toFloat()) }
                SettingSlider(
                    label = ctx.getString(R.string.data_usage_module_plan_alert_percentage),
                    value = alertPct,
                    valueRange = 50f..100f,
                    onValueChange = {
                        alertPct = it
                        Prefs.setInt(ctx, "dat_plan_alert_pct", it.toInt())
                    },
                    formatter = { "${it.toInt()}%" }
                )
            }
            var billingDay by remember { mutableStateOf(Prefs.getInt(ctx, "dat_billing_day", 1).toFloat()) }
            SettingSlider(
                label = ctx.getString(R.string.data_usage_module_billing_day),
                value = billingDay,
                valueRange = 1f..31f,
                onValueChange = {
                    billingDay = it
                    Prefs.setInt(ctx, "dat_billing_day", it.toInt())
                },
                formatter = { "${it.toInt()}" }
            )
        }
    }

    override fun checkAlerts(ctx: Context) {
        val planMb = Prefs.getInt(ctx, "dat_plan_limit", 0)
        if (planMb <= 0) return
        val alertPct = Prefs.getInt(ctx, "dat_plan_alert_pct", 90)
        val fired = Prefs.getBool(ctx, "dat_plan_alert_fired", false)
        val planBytes = planMb * 1024L * 1024L
        val pct = monthTotal * 100f / planBytes

        if (pct >= alertPct && !fired) {
            try {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(2005, NotificationCompat.Builder(ctx, "ebox_alerts")
                    .setSmallIcon(R.drawable.ic_notif)
                    .setContentTitle(ctx.getString(R.string.data_usage_module_plan_warning_title))
                    .setContentText(ctx.getString(R.string.data_usage_module_plan_warning_content, pct, Fmt.bytes(planBytes)))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true).build())
            } catch (ignored: Exception) {
            }
            Prefs.setBool(ctx, "dat_plan_alert_fired", true)
        }
    }
}
