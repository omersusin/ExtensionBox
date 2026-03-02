package com.extensionbox.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector

data class ModuleDef(
    val key: String,
    val icon: ImageVector,
    val name: String,
    val description: String,
    val defaultEnabled: Boolean
)

object ModuleRegistry {

    private val MODULES = listOf(
        ModuleDef("battery", Icons.Rounded.BatteryChargingFull, "Battery & Screen", "Battery health, power and screen usage", true),
        ModuleDef("app_usage", Icons.Rounded.Apps, "App Usage", "Time spent in each application", true),
        ModuleDef("cpu", Icons.Rounded.Memory, "CPU", "CPU usage, frequency and temperature", true),
        ModuleDef("ram", Icons.Rounded.Memory, "RAM", "Memory status and running processes", true),
        ModuleDef("sleep", Icons.Rounded.Bedtime, "Deep Sleep", "CPU sleep vs awake ratio", true),
        ModuleDef("network", Icons.Rounded.NetworkCheck, "Network Speed", "Real-time download/upload speed", true),
        ModuleDef("data", Icons.Rounded.DataUsage, "Data Usage", "Daily & monthly, WiFi & mobile", true),
        ModuleDef("unlock", Icons.Rounded.LockOpen, "Unlock Counter", "Daily unlocks, detox tracking", true),
        ModuleDef("storage", Icons.Rounded.Storage, "Storage", "Internal storage usage", false),
        ModuleDef("connection", Icons.Rounded.SettingsInputAntenna, "Connection Info", "WiFi, cellular, VPN status", false),
        ModuleDef("uptime", Icons.Rounded.History, "Uptime", "Device uptime since boot", false),
        ModuleDef("steps", Icons.AutoMirrored.Rounded.DirectionsWalk, "Step Counter", "Steps and distance", false),
        ModuleDef("speedtest", Icons.Rounded.Speed, "Speed Test", "Periodic download/upload speed test", false),
        ModuleDef("habit", Icons.Rounded.Favorite, "Habit Tracker", "Self-monitoring counter & streak", false),
        ModuleDef("privacy", Icons.Rounded.Shield, "Privacy Dashboard", "Permission usage and force-revoke tools", false)
    )

    fun keyAt(i: Int): String = MODULES[i].key
    fun iconAt(i: Int): ImageVector = MODULES[i].icon
    fun nameAt(i: Int): String = MODULES[i].name
    fun descAt(i: Int): String = MODULES[i].description
    fun defAt(i: Int): Boolean = MODULES[i].defaultEnabled
    fun count(): Int = MODULES.size

    fun iconFor(key: String): ImageVector {
        return MODULES.find { it.key == key }?.icon ?: Icons.Rounded.Extension
    }

    fun nameFor(key: String): String {
        return MODULES.find { it.key == key }?.name ?: key
    }

    fun getModule(key: String): com.extensionbox.app.modules.Module? {
        return when (key) {
            "battery" -> com.extensionbox.app.modules.BatteryModule()
            "cpu" -> com.extensionbox.app.modules.CpuModule()
            "ram" -> com.extensionbox.app.modules.RamModule()
            "app_usage" -> com.extensionbox.app.modules.AppUsageModule()
            "sleep" -> com.extensionbox.app.modules.SleepModule()
            "network" -> com.extensionbox.app.modules.NetworkModule()
            "data" -> com.extensionbox.app.modules.DataUsageModule()
            "unlock" -> com.extensionbox.app.modules.UnlockModule()
            "storage" -> com.extensionbox.app.modules.StorageModule()
            "connection" -> com.extensionbox.app.modules.ConnectionModule()
            "uptime" -> com.extensionbox.app.modules.UptimeModule()
            "steps" -> com.extensionbox.app.modules.StepModule()
            "speedtest" -> com.extensionbox.app.modules.SpeedTestModule()
            "habit" -> com.extensionbox.app.modules.HabitTrackerModule()
            "privacy" -> com.extensionbox.app.modules.PrivacyModule()
            else -> null
        }
    }
}
