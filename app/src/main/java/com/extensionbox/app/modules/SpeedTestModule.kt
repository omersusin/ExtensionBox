package com.extensionbox.app.modules

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import kotlinx.coroutines.*
import java.net.URL
import java.util.LinkedHashMap
import kotlin.math.abs

class SpeedTestModule : Module {

    private var ctx: Context? = null
    private var running = false
    private var testing = false
    
    private var downKbps = 0L
    private var upKbps = 0L
    private var pingMs = 0L
    private var lastTestTime = 0L

    override fun key(): String = "speedtest"
    override fun name(): String = ctx?.getString(R.string.speed_test_module_name) ?: "Speed Test"
    override fun description(): String = ctx?.getString(R.string.speed_test_module_description) ?: "Periodic download and upload speed test"
    override fun defaultEnabled(): Boolean = false
    override fun alive(): Boolean = running
    override fun priority(): Int = 95
    override fun hasSettings(): Boolean = true

    override fun tickIntervalMs(): Int = ctx?.let { Prefs.getInt(it, "spd_interval", 60000) } ?: 60000

    override fun start(ctx: Context, sys: SystemAccess) {
        this.ctx = ctx
        running = true
    }

    override fun stop() {
        running = false
    }

    override fun tick() {
        val c = ctx ?: return
        if (testing) return

        val auto = Prefs.getBool(c, "spd_auto_test", true)
        if (!auto) return

        val now = System.currentTimeMillis()
        val freqMin = Prefs.getInt(c, "spd_test_freq", 60)
        if (now - lastTestTime < freqMin * 60 * 1000L) return

        // Daily limit check
        val testsToday = Prefs.getInt(c, "spd_tests_today", 0)
        val limit = Prefs.getInt(c, "spd_daily_limit", 10)
        if (testsToday >= limit) return

        // WiFi only check
        if (Prefs.getBool(c, "spd_wifi_only", true)) {
            if (!isWifiConnected(c)) return
        }

        runTest()
    }

    private fun runTest() {
        testing = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Very basic speed test logic - usually would use a dedicated library or multiple chunks
                val start = System.currentTimeMillis()
                val conn = URL("https://www.google.com").openConnection()
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                pingMs = System.currentTimeMillis() - start
                
                val input = conn.getInputStream()
                val buffer = ByteArray(8192)
                var bytesRead = 0
                var totalRead = 0L
                val testStart = System.currentTimeMillis()
                
                // Read for max 3 seconds or 5MB
                while (System.currentTimeMillis() - testStart < 3000 && totalRead < 5 * 1024 * 1024) {
                    bytesRead = input.read(buffer)
                    if (bytesRead == -1) break
                    totalRead += bytesRead
                }
                input.close()
                
                val duration = System.currentTimeMillis() - testStart
                if (duration > 0) {
                    downKbps = totalRead * 8 / duration // bits per ms = kbps
                }
                
                lastTestTime = System.currentTimeMillis()
                ctx?.let { 
                    val count = Prefs.getInt(it, "spd_tests_today", 0)
                    Prefs.setInt(it, "spd_tests_today", count + 1)
                }
            } catch (ignored: Exception) {
            } finally {
                testing = false
            }
        }
    }

    private fun isWifiConnected(c: Context): Boolean {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(net)
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    override fun compact(): String = if (testing) ctx?.getString(R.string.speed_test_module_testing) ?: "" else ctx?.getString(R.string.speed_test_module_compact_text, Fmt.speed(downKbps * 128)) ?: ""

    override fun detail(): String {
        val c = ctx ?: return ""
        val sb = StringBuilder()
        sb.append(c.getString(R.string.speed_test_module_name))
        if (testing) sb.append(c.getString(R.string.speed_test_module_running))
        sb.append(c.getString(R.string.speed_test_module_download, Fmt.speed(downKbps * 128)))
        if (upKbps > 0) sb.append(c.getString(R.string.speed_test_module_upload, Fmt.speed(upKbps * 128)))
        
        if (c != null && Prefs.getBool(c, "spd_show_ping", true)) {
            sb.append(c.getString(R.string.speed_test_module_latency, pingMs))
        }
        
        if (lastTestTime > 0) {
            val ago = (System.currentTimeMillis() - lastTestTime) / 60000
            sb.append(c.getString(R.string.speed_test_module_last_test, ago))
        }
        
        return sb.toString()
    }

    override fun dataPoints(): LinkedHashMap<String, String> {
        val d = LinkedHashMap<String, String>()
        d["speedtest.download"] = Fmt.speed(downKbps * 128)
        if (upKbps > 0) d["speedtest.upload"] = Fmt.speed(upKbps * 128)
        d["speedtest.ping"] = "${pingMs}ms"
        return d
    }

    @androidx.compose.runtime.Composable
    override fun settingsContent(ctx: android.content.Context, sys: com.extensionbox.app.SystemAccess) {
        var interval by remember { mutableStateOf(Prefs.getInt(ctx, "spd_interval", 60000).toFloat()) }
        
        Column {
            SettingSlider(
                label = ctx.getString(R.string.speed_test_module_update_interval),
                value = interval,
                onValueChange = {
                    interval = it
                    Prefs.setInt(ctx, "spd_interval", it.toInt())
                },
                valueRange = 10000f..300000f,
                formatter = { ctx.getString(R.string.speed_test_module_interval_formatter, it.toInt() / 1000) }
            )

            androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            var autoTest by remember { mutableStateOf(Prefs.getBool(ctx, "spd_auto_test", true)) }
            SettingSwitch(
                label = ctx.getString(R.string.speed_test_module_auto_test),
                checked = autoTest,
                onCheckedChange = {
                    autoTest = it
                    Prefs.setBool(ctx, "spd_auto_test", it)
                }
            )
            if (autoTest) {
                var freq by remember { mutableStateOf(Prefs.getInt(ctx, "spd_test_freq", 60).toFloat()) }
                SettingSlider(
                    label = ctx.getString(R.string.speed_test_module_test_frequency),
                    value = freq,
                    valueRange = 15f..240f,
                    steps = 15,
                    onValueChange = {
                        freq = it
                        Prefs.setInt(ctx, "spd_test_freq", it.toInt())
                    },
                    formatter = { ctx.getString(R.string.speed_test_module_frequency_formatter, it.toInt()) }
                )
            }
            var dailyLimit by remember { mutableStateOf(Prefs.getInt(ctx, "spd_daily_limit", 10).toFloat()) }
            SettingSlider(
                label = ctx.getString(R.string.speed_test_module_daily_test_limit),
                value = dailyLimit,
                valueRange = 1f..100f,
                onValueChange = {
                    dailyLimit = it
                    Prefs.setInt(ctx, "spd_daily_limit", it.toInt())
                },
                formatter = { ctx.getString(R.string.speed_test_module_limit_formatter, it.toInt()) }
            )
            var wifiOnly by remember { mutableStateOf(Prefs.getBool(ctx, "spd_wifi_only", true)) }
            SettingSwitch(
                label = ctx.getString(R.string.speed_test_module_wifi_only),
                checked = wifiOnly,
                onCheckedChange = {
                    wifiOnly = it
                    Prefs.setBool(ctx, "spd_wifi_only", it)
                }
            )
            var showPing by remember { mutableStateOf(Prefs.getBool(ctx, "spd_show_ping", true)) }
            SettingSwitch(
                label = ctx.getString(R.string.speed_test_module_show_ping),
                checked = showPing,
                onCheckedChange = {
                    showPing = it
                    Prefs.setBool(ctx, "spd_show_ping", it)
                }
            )
        }
    }

    override fun checkAlerts(ctx: Context) {}

    override fun reset() {
        ctx?.let { Prefs.setInt(it, "spd_tests_today", 0) }
    }
}
