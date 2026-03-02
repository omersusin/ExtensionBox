package com.extensionbox.app.modules

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.extensionbox.app.Fmt
import com.extensionbox.app.Prefs
import com.extensionbox.app.R
import com.extensionbox.app.SystemAccess
import com.extensionbox.app.ui.components.SettingSlider
import com.extensionbox.app.ui.components.SettingSwitch
import java.util.LinkedHashMap
import java.util.Locale

class StepModule : Module, SensorEventListener {

    private var ctx: Context? = null
    private var sm: SensorManager? = null
    private var running = false
    private var dailySteps = 0L
    private var startSteps = -1L

    override fun key(): String = "steps"
    override fun name(): String = ctx?.getString(R.string.step_module_name) ?: "Step Counter"
    override fun description(): String = ctx?.getString(R.string.step_module_description) ?: "Steps and distance"
    override fun defaultEnabled(): Boolean = false
    override fun alive(): Boolean = running
    override fun priority(): Int = 90
    override fun hasSettings(): Boolean = true

    override fun tickIntervalMs(): Int = ctx?.let { Prefs.getInt(it, "stp_interval", 30000) } ?: 30000

    override fun start(ctx: Context, sys: SystemAccess) {
        this.ctx = ctx
        dailySteps = Prefs.getLong(ctx, "stp_today", 0)
        sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val s = sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (s != null) {
            sm?.registerListener(this, s, SensorManager.SENSOR_DELAY_UI)
            running = true
        }
    }

    override fun stop() {
        sm?.unregisterListener(this)
        sm = null
        running = false
    }

    override fun tick() {
        ctx?.let { dailySteps = Prefs.getLong(it, "stp_today", 0) }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val total = event.values[0].toLong()
            if (startSteps < 0) {
                // First event since start
                startSteps = total
                return
            }
            val delta = total - startSteps
            if (delta > 0) {
                dailySteps += delta
                startSteps = total
                ctx?.let { Prefs.setLong(it, "stp_today", dailySteps) }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun compact(): String = ctx?.getString(R.string.step_module_compact_text, Fmt.number(dailySteps)) ?: ""

    override fun detail(): String {
        val sb = StringBuilder()
        val c = ctx ?: return ""
        val goal = if (c != null) Prefs.getInt(c, "stp_goal", 10000) else 10000
        val pct = dailySteps * 100f / goal
        
        sb.append(c.getString(R.string.step_module_steps, Fmt.number(dailySteps), Fmt.number(goal.toLong()), pct))
        
        if (c != null && Prefs.getBool(c, "stp_show_distance", true)) {
            val stride = Prefs.getInt(c, "stp_stride_cm", 75)
            val distKm = dailySteps * stride / 100000f
            sb.append(String.format(Locale.US, c.getString(R.string.step_module_distance), distKm))
        }

        if (c != null && Prefs.getBool(c, "stp_show_yesterday", true)) {
            val y = Prefs.getLong(c, "stp_yesterday", 0)
            if (y > 0) sb.append(c.getString(R.string.step_module_yesterday, Fmt.number(y)))
        }
        return sb.toString().trim()
    }

    override fun dataPoints(): LinkedHashMap<String, String> {
        val d = LinkedHashMap<String, String>()
        d["steps.today"] = Fmt.number(dailySteps)
        d["steps.yesterday"] = (ctx?.let { Prefs.getLong(it, "stp_yesterday", 0) } ?: 0).toString()
        return d
    }

    @androidx.compose.runtime.Composable
    override fun settingsContent(ctx: android.content.Context, sys: com.extensionbox.app.SystemAccess) {
        var interval by remember { mutableStateOf(Prefs.getInt(ctx, "stp_interval", 30000).toFloat()) }
        
        Column {
            SettingSlider(
                label = ctx.getString(R.string.step_module_update_interval),
                value = interval,
                onValueChange = {
                    interval = it
                    Prefs.setInt(ctx, "stp_interval", it.toInt())
                },
                valueRange = 5000f..300000f,
                formatter = { if (it >= 60000f) "${it.toInt() / 60000}m" else "${it.toInt() / 1000}s" }
            )

            androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            var goal by remember { mutableStateOf(Prefs.getInt(ctx, "stp_goal", 10000).toFloat()) }
            SettingSlider(
                label = ctx.getString(R.string.step_module_daily_goal),
                value = goal,
                valueRange = 0f..30000f,
                onValueChange = {
                    goal = it
                    Prefs.setInt(ctx, "stp_goal", it.toInt())
                },
                formatter = { if (it == 0f) ctx.getString(R.string.step_module_no_goal) else "${it.toInt()}${ctx.getString(R.string.step_module_steps_unit)}" }
            )
            var stride by remember { mutableStateOf(Prefs.getInt(ctx, "stp_stride_cm", 75).toFloat()) }
            SettingSlider(
                label = ctx.getString(R.string.step_module_step_length),
                value = stride,
                valueRange = 30f..120f,
                onValueChange = {
                    stride = it
                    Prefs.setInt(ctx, "stp_stride_cm", it.toInt())
                },
                formatter = { "${it.toInt()}${ctx.getString(R.string.step_module_cm_unit)}" }
            )
            var showDistance by remember { mutableStateOf(Prefs.getBool(ctx, "stp_show_distance", true)) }
            SettingSwitch(
                label = ctx.getString(R.string.step_module_show_distance),
                checked = showDistance,
                onCheckedChange = {
                    showDistance = it
                    Prefs.setBool(ctx, "stp_show_distance", it)
                }
            )
            var showGoal by remember { mutableStateOf(Prefs.getBool(ctx, "stp_show_goal", true)) }
            SettingSwitch(
                label = ctx.getString(R.string.step_module_show_goal),
                checked = showGoal,
                onCheckedChange = {
                    showGoal = it
                    Prefs.setBool(ctx, "stp_show_goal", it)
                }
            )
            var showYesterday by remember { mutableStateOf(Prefs.getBool(ctx, "stp_show_yesterday", true)) }
            SettingSwitch(
                label = ctx.getString(R.string.step_module_show_yesterday),
                checked = showYesterday,
                onCheckedChange = {
                    showYesterday = it
                    Prefs.setBool(ctx, "stp_show_yesterday", it)
                }
            )
        }
    }

    override fun checkAlerts(ctx: Context) {}

    override fun reset() {
        dailySteps = 0
        ctx?.let { Prefs.setLong(it, "stp_today", 0) }
    }
}
