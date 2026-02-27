package com.extensionbox.app.modules

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.extensionbox.app.Prefs
import com.extensionbox.app.R
import com.extensionbox.app.SystemAccess
import com.extensionbox.app.ui.components.SettingSlider
import com.extensionbox.app.ui.screens.PrivacyEvent
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.LinkedHashMap

class PrivacyModule : Module {
    private var ctx: Context? = null
    private var sys: SystemAccess? = null
    private var running = false
    private var lastEvents = listOf<PrivacyEvent>()

    override fun key(): String = "privacy"
    override fun name(): String = ctx?.getString(R.string.privacy_module_name) ?: "Privacy Dashboard"
    override fun description(): String = ctx?.getString(R.string.privacy_module_description) ?: "Permission usage and force-revoke tools"
    override fun defaultEnabled(): Boolean = true
    override fun alive(): Boolean = running
    override fun priority(): Int = 90
    override fun hasSettings(): Boolean = true

    override fun start(ctx: Context, sys: SystemAccess) {
        this.ctx = ctx
        this.sys = sys
        running = true
    }

    override fun stop() {
        running = false
    }

    override fun tick() {
        val c = ctx ?: return
        val s = sys ?: return
        if (s.isEnhanced()) {
            lastEvents = s.getPrivacyHistory(c)
        }
    }

    override fun tickIntervalMs(): Int = ctx?.let { Prefs.getInt(it, "pri_interval", 60000) } ?: 60000

    override fun compact(): String {
        if (lastEvents.isEmpty()) return "No activity"
        val top = lastEvents.first()
        return ctx?.getString(R.string.privacy_module_compact_text, top.appLabel, top.opName) ?: ""
    }

    override fun detail(): String {
        if (lastEvents.isEmpty()) return ctx?.getString(R.string.privacy_module_no_activity) ?: "No activity"
        val sb = StringBuilder()
        sb.append(ctx?.getString(R.string.privacy_module_recent_activity)).append("\n")
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        lastEvents.take(5).forEach { 
            sb.append(" • ").append(it.appLabel).append(": ").append(it.opName)
              .append(" (").append(sdf.format(Date(it.lastAccessTime))).append(")\n")
        }
        return sb.toString()
    }

    override fun dataPoints(): LinkedHashMap<String, String> {
        val d = LinkedHashMap<String, String>()
        lastEvents.take(5).forEachIndexed { index, event ->
            d["privacy.event_$index"] = "${event.appLabel} accessed ${event.opName} at ${Date(event.lastAccessTime)}"
        }
        return d
    }

    @Composable
    override fun settingsContent(ctx: Context, sys: SystemAccess) {
        var interval by remember { mutableStateOf(Prefs.getInt(ctx, "pri_interval", 60000).toFloat()) }
        
        Column {
            SettingSlider(
                label = ctx.getString(R.string.privacy_module_update_interval),
                value = interval,
                valueRange = 30000f..300000f,
                onValueChange = {
                    interval = it
                    Prefs.setInt(ctx, "pri_interval", it.toInt())
                },
                formatter = { ctx.getString(R.string.privacy_module_interval_formatter, it.toInt() / 60000) }
            )
        }
    }

    override fun checkAlerts(ctx: Context) {
        // Optional: Compare lastEvents with previous tick to fire alerts for new access
    }
}
