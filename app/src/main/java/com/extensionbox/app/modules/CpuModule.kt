package com.extensionbox.app.modules

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.extensionbox.app.Fmt
import com.extensionbox.app.Prefs
import com.extensionbox.app.R
import com.extensionbox.app.SystemAccess
import com.extensionbox.app.ui.components.SettingSlider
import java.util.LinkedHashMap
import java.util.Locale

class CpuModule : Module {
    private var ctx: Context? = null
    private var sys: SystemAccess? = null
    private var running = false

    private var prevCpuTimes: LongArray? = null
    private var cpuUsage = -1f
    private var cpuTemp = Float.NaN
    
    private var coreFreqs = listOf<Long>()
    private var coreGovs = listOf<String>()
    private var clusterInfo = listOf<Triple<String, String, Long>>()
    private var gpuLoad = -1
    private var gpuFreq = 0L

    override fun key(): String = "cpu"
    override fun name(): String = ctx?.getString(R.string.cpu_module_name) ?: "CPU"
    override fun description(): String = ctx?.getString(R.string.cpu_module_description) ?: "CPU usage, frequency and temperature"
    override fun defaultEnabled(): Boolean = true
    override fun alive(): Boolean = running
    override fun priority(): Int = 15
    override fun hasSettings(): Boolean = true

    override fun tickIntervalMs(): Int = ctx?.let { Prefs.getInt(it, "cpu_interval", 5000) } ?: 5000

    override fun start(ctx: Context, sys: SystemAccess) {
        this.ctx = ctx
        this.sys = sys
        val a = readCpuTimes()
        if (a != null) {
            SystemClock.sleep(250)
            val b = readCpuTimes()
            val u = calcCpuUsage(a, b)
            if (u >= 0f) cpuUsage = u
            prevCpuTimes = b ?: a
        } else {
            prevCpuTimes = null
            sys.readCpuUsageFallback().let { if (it >= 0f) cpuUsage = it }
        }

        cpuTemp = sys.readCpuTemp()
        running = true
    }

    override fun stop() {
        running = false
        sys = null
    }

    override fun tick() {
        val current = readCpuTimes()
        val u = calcCpuUsage(prevCpuTimes, current)
        if (u >= 0f) {
            cpuUsage = u
        } else {
            sys?.readCpuUsageFallback()?.let { if (it >= 0f) cpuUsage = it }
        }

        if (current != null) prevCpuTimes = current
        sys?.let { s ->
            s.readCpuTemp().let { cpuTemp = it }
            coreFreqs = s.getCpuCoreFrequencies()
            coreGovs = s.getCpuGovernors()
            clusterInfo = s.getCpuClusterInfo()
            val gpu = s.getGpuData()
            gpuLoad = gpu.first
            gpuFreq = gpu.second
        }
    }

    private fun calcCpuUsage(prev: LongArray?, curr: LongArray?): Float {
        if (prev == null || curr == null) return -1f
        if (prev.size < 5 || curr.size < 5) return -1f

        val prevIdle = prev[3] + prev[4]
        val currIdle = curr[3] + curr[4]

        var prevTotal = 0L
        for (v in prev) prevTotal += v
        var currTotal = 0L
        for (v in curr) currTotal += v

        val totalDiff = currTotal - prevTotal
        val idleDiff = currIdle - prevIdle

        if (totalDiff <= 0) return -1f

        val usage = (totalDiff - idleDiff) * 100f / totalDiff.toFloat()
        return usage.coerceIn(0f, 100f)
    }

    private fun readCpuTimes(): LongArray? {
        val s = sys ?: return null
        return try {
            val line = s.readSysFile("/proc/stat")
            if (line != null && line.startsWith("cpu ")) {
                val parts = line.substring(4).trim().split(Regex("\\s+"))
                val times = LongArray(parts.size.coerceAtMost(7))
                for (i in times.indices) {
                    times[i] = parts[i].toLong()
                }
                times
            } else null
        } catch (ignored: Exception) {
            null
        }
    }

    override fun compact(): String {
        val cpuStr = if (cpuUsage < 0f) "--" else "${cpuUsage.toInt()}%"
        val tempStr = if (!cpuTemp.isNaN()) ctx?.getString(R.string.cpu_module_temperature, Fmt.temp(cpuTemp)) else ""
        return ctx?.getString(R.string.cpu_module_compact_text, cpuStr, tempStr) ?: ""
    }

    override fun detail(): String {
        val sb = StringBuilder()
        val c = ctx ?: return ""
        val cpuStr = if (cpuUsage < 0f) "--" else String.format(Locale.US, "%.1f%%", cpuUsage)
        sb.append(c.getString(R.string.cpu_module_usage, cpuStr))
        if (!cpuTemp.isNaN()) {
            sb.append(c.getString(R.string.cpu_module_temperature, Fmt.temp(cpuTemp)))
        }
        sb.append("\n")
        
        if (gpuLoad >= 0) {
            sb.append(c.getString(R.string.cpu_module_gpu_load, gpuLoad))
            if (gpuFreq > 0) sb.append(c.getString(R.string.cpu_module_gpu_freq, gpuFreq / 1_000_000))
            sb.append("\n")
        }

        if (clusterInfo.isNotEmpty()) {
            clusterInfo.forEach { (label, gov, max) ->
                sb.append("   $label: $gov (${max / 1000}MHz)\n")
            }
        }

        if (coreFreqs.isNotEmpty()) {
            sb.append(c.getString(R.string.cpu_module_cores))
            coreFreqs.forEachIndexed { i, f ->
                if (f > 0) sb.append(c.getString(R.string.cpu_module_core_freq, f/1000))
            }
            sb.append("\n")
        }

        return sb.toString()
    }

    override fun dataPoints(): LinkedHashMap<String, String> {
        val d = LinkedHashMap<String, String>()
        d["cpu.usage"] = if (cpuUsage < 0f) "N/A" else String.format(Locale.US, "%.1f%%", cpuUsage)
        d["cpu.temperature"] = if (!cpuTemp.isNaN()) Fmt.temp(cpuTemp) else "—"

        if (gpuLoad >= 0) {
            d["gpu.load"] = "$gpuLoad%"
            if (gpuFreq > 0) d["gpu.frequency"] = "${gpuFreq / 1_000_000}MHz"
        }

        clusterInfo.forEach { (label, gov, _) ->
            val key = label.lowercase(Locale.US).replace(" ", "_")
            d["cpu.$key"] = gov
        }

        coreFreqs.forEachIndexed { i, f ->
            if (f > 0) d["cpu.core$i"] = "${f/1000}MHz"
        }
        
        return d
    }

    override fun checkAlerts(ctx: Context) {}

    @androidx.compose.runtime.Composable
    override fun settingsContent(ctx: android.content.Context, sys: com.extensionbox.app.SystemAccess) {
        var interval by remember { 
            mutableStateOf(Prefs.getInt(ctx, "cpu_interval", 5000).toFloat()) 
        }

        Column {
            SettingSlider(
                label = ctx.getString(R.string.cpu_module_update_interval),
                value = interval,
                onValueChange = { 
                    interval = it
                    Prefs.setInt(ctx, "cpu_interval", it.toInt())
                },
                valueRange = 1000f..10000f,
                steps = 8,
                formatter = { "${it.toInt()}ms" }
            )
        }
    }
}
