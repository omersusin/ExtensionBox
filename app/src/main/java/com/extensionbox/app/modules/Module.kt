package com.extensionbox.app.modules

import android.content.Context
import com.extensionbox.app.SystemAccess
import java.util.LinkedHashMap

interface Module {
    fun key(): String
    fun name(): String
    fun description(): String
    fun defaultEnabled(): Boolean
    fun start(ctx: Context, sys: SystemAccess)
    fun stop()
    fun tick()
    fun tickIntervalMs(): Int
    fun compact(): String
    fun detail(): String
    fun dataPoints(): LinkedHashMap<String, String>
    fun alive(): Boolean
    fun checkAlerts(ctx: Context)
    fun reset() {}

    /**
     * Whether this module has configuration settings.
     */
    fun hasSettings(): Boolean = false

    /**
     * Optional Compose UI content for the module's dashboard card.
     */
    @androidx.compose.runtime.Composable
    fun dashboardContent(ctx: Context, sys: SystemAccess) {}

    /**
     * Optional Compose UI content for the module's detail view (e.g., sliders, toggles).
     */
    @androidx.compose.runtime.Composable
    fun composableContent(ctx: Context, sys: SystemAccess) {}

    /**
     * Optional Compose UI for module-specific settings/configuration.
     */
    @androidx.compose.runtime.Composable
    fun settingsContent(ctx: Context, sys: SystemAccess) {}

    /**
     * Priority for notification ordering. Lower = higher priority.
     * Battery=10, Screen=20, Sleep=30, Network=40, Data=50, Unlock=60, Steps=70, SpeedTest=80, etc.
     */
    fun priority(): Int

    fun vibrate(ctx: android.content.Context, pattern: LongArray = longArrayOf(0, 100)) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vm = ctx.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                vm.defaultVibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val v = ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
                v.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
            }
        } catch (ignored: Exception) {}
    }
}
