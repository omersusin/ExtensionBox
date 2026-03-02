package com.extensionbox.app.modules

import android.content.Context
import android.os.SystemClock
import com.extensionbox.app.Fmt
import com.extensionbox.app.Prefs
import com.extensionbox.app.R
import com.extensionbox.app.SystemAccess
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import kotlin.math.abs

class UptimeModule : Module {

    private var ctx: Context? = null
    private var running = false
    private var bootTimestamp: Long = 0

    override fun key(): String = "uptime"
    override fun name(): String = ctx?.getString(R.string.uptime_module_name) ?: "Uptime"
    override fun description(): String = ctx?.getString(R.string.uptime_module_description) ?: "Device uptime since boot"
    override fun defaultEnabled(): Boolean = false
    override fun alive(): Boolean = running
    override fun priority(): Int = 95

    override fun tickIntervalMs(): Int = ctx?.let { Prefs.getInt(it, "upt_interval", 60000) } ?: 60000

    override fun start(ctx: Context, sys: SystemAccess) {
        this.ctx = ctx
        bootTimestamp = System.currentTimeMillis() - SystemClock.elapsedRealtime()

        var reboots = Prefs.getInt(ctx, "upt_reboot_count", 0)
        val lastBoot = Prefs.getLong(ctx, "upt_last_boot", 0)
        if (abs(bootTimestamp - lastBoot) > 60000) {
            reboots++
            Prefs.setInt(ctx, "upt_reboot_count", reboots)
            Prefs.setLong(ctx, "upt_last_boot", bootTimestamp)
        }
        running = true
    }

    override fun stop() {
        running = false
    }

    override fun tick() {}

    override fun compact(): String = ctx?.getString(R.string.uptime_module_compact_text, Fmt.duration(SystemClock.elapsedRealtime())) ?: ""

    override fun detail(): String {
        val c = ctx ?: return ""
        val bootStr = SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date(bootTimestamp))
        val reboots = c.let { Prefs.getInt(it, "upt_reboot_count", 0) } ?: 0
        return c.getString(R.string.uptime_module_uptime, Fmt.duration(SystemClock.elapsedRealtime())) +
               c.getString(R.string.uptime_module_last_boot, bootStr) +
               c.getString(R.string.uptime_module_reboots_tracked, reboots)
    }

    override fun dataPoints(): LinkedHashMap<String, String> {
        val bootStr = SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date(bootTimestamp))
        val reboots = ctx?.let { Prefs.getInt(it, "upt_reboot_count", 0) } ?: 0
        val d = LinkedHashMap<String, String>()
        d["uptime.duration"] = Fmt.duration(SystemClock.elapsedRealtime())
        d["uptime.boot_time"] = bootStr
        d["uptime.reboots"] = reboots.toString()
        return d
    }

    override fun checkAlerts(ctx: Context) {}
}
