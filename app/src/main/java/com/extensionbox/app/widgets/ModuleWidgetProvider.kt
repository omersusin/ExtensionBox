package com.extensionbox.app.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.Html
import android.widget.RemoteViews
import com.extensionbox.app.MainActivity
import com.extensionbox.app.MonitorService
import com.extensionbox.app.R
import com.extensionbox.app.ui.ModuleRegistry

/**
 * A single reusable AppWidgetProvider used for all module widgets.
 */
open class ModuleWidgetProvider : AppWidgetProvider() {

    // Subclass aliases
    class BatteryWidgetProvider : ModuleWidgetProvider()
    class CpuWidgetProvider : ModuleWidgetProvider()
    class RamWidgetProvider : ModuleWidgetProvider()
    class ScreenWidgetProvider : ModuleWidgetProvider()
    class SleepWidgetProvider : ModuleWidgetProvider()
    class NetworkWidgetProvider : ModuleWidgetProvider()
    class DataWidgetProvider : ModuleWidgetProvider()
    class UnlockWidgetProvider : ModuleWidgetProvider()
    class StorageWidgetProvider : ModuleWidgetProvider()
    class ConnectionWidgetProvider : ModuleWidgetProvider()
    class UptimeWidgetProvider : ModuleWidgetProvider()
    class StepWidgetProvider : ModuleWidgetProvider()
    class SpeedTestWidgetProvider : ModuleWidgetProvider()
    class HabitWidgetProvider : ModuleWidgetProvider()
    class AllInOneWidgetProvider : ModuleWidgetProvider()

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, widgetIds: IntArray) {
        val key = resolveKey(javaClass)
        for (id in widgetIds) {
            updateWidget(ctx, mgr, id, key)
        }
    }

    companion object {
        private fun resolveKey(cls: Class<*>): String {
            val name = cls.simpleName
            return when (name) {
                "BatteryWidgetProvider" -> "battery"
                "CpuWidgetProvider" -> "cpu"
                "RamWidgetProvider" -> "ram"
                "ScreenWidgetProvider" -> "battery" // Screen merged into battery
                "SleepWidgetProvider" -> "sleep"
                "NetworkWidgetProvider" -> "network"
                "DataWidgetProvider" -> "data"
                "UnlockWidgetProvider" -> "unlock"
                "StorageWidgetProvider" -> "storage"
                "ConnectionWidgetProvider" -> "connection"
                "UptimeWidgetProvider" -> "uptime"
                "StepWidgetProvider" -> "steps"
                "SpeedTestWidgetProvider" -> "speedtest"
                "HabitWidgetProvider" -> "habit"
                "AllInOneWidgetProvider" -> "all_in_one"
                else -> "battery"
            }
        }

        private fun updateWidget(ctx: Context, mgr: AppWidgetManager, widgetId: Int, key: String) {
            val views = RemoteViews(ctx.packageName, R.layout.widget_module)

            // Header
            if (key == "all_in_one") {
                views.setTextViewText(R.id.widgetTitle, "All-in-One Status")
            } else {
                val name = ModuleRegistry.nameFor(key)
                views.setTextViewText(R.id.widgetTitle, name)
            }

            // Data
            val body = StringBuilder()
            if (key == "all_in_one") {
                // Get data for ALL enabled modules, but only top 1-2 lines each
                for (i in 0 until ModuleRegistry.count()) {
                    val mKey = ModuleRegistry.keyAt(i)
                    if (com.extensionbox.app.Prefs.isModuleEnabled(ctx, mKey, ModuleRegistry.defAt(i))) {
                        val data = MonitorService.getModuleData(mKey)
                        if (data != null && data.isNotEmpty()) {
                            if (body.isNotEmpty()) body.append("<br>")
                            body.append("<u><b>").append(ModuleRegistry.nameFor(mKey)).append("</b></u><br>")
                            
                            var shown = 0
                            for ((rawKey, value) in data) {
                                if (shown >= 2) break // Only show top 2 items for each in All-in-One
                                shown++
                                val label = rawKey.substringAfterLast('.').replaceFirstChar { it.uppercase() }.replace("_", " ")
                                body.append("<b>").append(label).append(":</b> ").append(value).append("<br>")
                            }
                        }
                    }
                }
                if (body.isEmpty()) body.append("<i>No modules enabled</i>")
            } else {
                val data = MonitorService.getModuleData(key)
                if (data != null && data.isNotEmpty()) {
                    var shown = 0
                    for ((rawKey, value) in data) {
                        if (shown >= 6) break
                        shown++
                        val label = rawKey.substringAfterLast('.').replaceFirstChar { it.uppercase() }.replace("_", " ")
                        if (body.isNotEmpty()) body.append("<br>")
                        body.append("<b>").append(label).append(":</b> ").append(value)
                    }
                } else {
                    body.append("<i>No data — start monitoring</i>")
                }
            }
            views.setTextViewText(R.id.widgetBody, Html.fromHtml(body.toString(), Html.FROM_HTML_MODE_COMPACT))

            // Click -> open app
            val openIntent = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                ctx, widgetId, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, pi)

            mgr.updateAppWidget(widgetId, views)
        }

        @JvmStatic
        fun updateAllWidgets(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return

            val providers = arrayOf(
                BatteryWidgetProvider::class.java,
                CpuWidgetProvider::class.java,
                RamWidgetProvider::class.java,
                ScreenWidgetProvider::class.java,
                SleepWidgetProvider::class.java,
                NetworkWidgetProvider::class.java,
                DataWidgetProvider::class.java,
                UnlockWidgetProvider::class.java,
                StorageWidgetProvider::class.java,
                ConnectionWidgetProvider::class.java,
                UptimeWidgetProvider::class.java,
                StepWidgetProvider::class.java,
                SpeedTestWidgetProvider::class.java,
                HabitWidgetProvider::class.java,
                AllInOneWidgetProvider::class.java
            )

            for (providerCls in providers) {
                val key = resolveKey(providerCls)
                val comp = ComponentName(ctx, providerCls)
                val ids = mgr.getAppWidgetIds(comp)
                if (ids != null) {
                    for (id in ids) {
                        updateWidget(ctx, mgr, id, key)
                    }
                }
            }
        }
    }
}
