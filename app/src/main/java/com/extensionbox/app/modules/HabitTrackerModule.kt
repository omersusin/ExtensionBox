package com.extensionbox.app.modules

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.extensionbox.app.Prefs
import com.extensionbox.app.R
import com.extensionbox.app.SystemAccess
import java.util.*

class HabitTrackerModule : Module {
    private var ctx: Context? = null
    private var running = false

    override fun key(): String = "habit"
    override fun name(): String = ctx?.getString(R.string.hab_counter_module_name) ?: "Habit Tracker"
    override fun description(): String = ctx?.getString(R.string.hab_counter_module_description) ?: "Self-monitoring counter & streak tracker"
    override fun defaultEnabled(): Boolean = false
    override fun alive(): Boolean = running
    override fun priority(): Int = 85

    override fun tickIntervalMs(): Int = ctx?.let { Prefs.getInt(it, "hab_interval", 60000) } ?: 60000

    override fun start(ctx: Context, sys: SystemAccess) {
        this.ctx = ctx
        running = true
        checkRollover()
    }

    override fun stop() {
        running = false
    }

    override fun tick() {
        checkRollover()
    }

    fun increment() {
        val c = ctx ?: return
        val today = Prefs.getInt(c, "hab_today", 0) + 1
        Prefs.setInt(c, "hab_today", today)
        
        val monthly = Prefs.getInt(c, "hab_monthly", 0) + 1
        Prefs.setInt(c, "hab_monthly", monthly)
        
        val allTime = Prefs.getInt(c, "hab_all_time", 0) + 1
        Prefs.setInt(c, "hab_all_time", allTime)
        
        Prefs.setInt(c, "hab_streak", 0)
        Prefs.setInt(c, "hab_last_day", getDayOfYear())
    }

    private fun checkRollover() {
        val c = ctx ?: return
        val currentDay = getDayOfYear()
        val lastDay = Prefs.getInt(c, "hab_last_check_day", -1)
        
        if (lastDay != currentDay) {
            Prefs.setInt(c, "hab_last_check_day", currentDay)
            val calendar = Calendar.getInstance()
            val currentMonth = calendar.get(Calendar.MONTH)
            
            val todayCount = Prefs.getInt(c, "hab_today", 0)
            if (todayCount == 0 && lastDay != -1) {
                val streak = Prefs.getInt(c, "hab_streak", 0)
                Prefs.setInt(c, "hab_streak", streak + 1)
            }
            Prefs.setInt(c, "hab_yesterday", todayCount)
            Prefs.setInt(c, "hab_today", 0)

            val lastMonth = Prefs.getInt(c, "hab_last_month", -1)
            if (lastMonth != currentMonth) {
                Prefs.setInt(c, "hab_prev_monthly", Prefs.getInt(c, "hab_monthly", 0))
                Prefs.setInt(c, "hab_monthly", 0)
                Prefs.setInt(c, "hab_last_month", currentMonth)
            }
            Prefs.setInt(c, "hab_last_check_day", currentDay)
        }
    }

    fun getTodayCount(): Int = ctx?.let { Prefs.getInt(it, "hab_today", 0) } ?: 0

    private fun getDayOfYear(): Int = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)

    override fun compact(): String {
        val c = ctx ?: return ""
        val today = Prefs.getInt(c, "hab_today", 0)
        val streak = Prefs.getInt(c, "hab_streak", 0)
        val showStreak = Prefs.getBool(c, "hab_show_streak", true)
        return if (streak > 0 && showStreak) c.getString(R.string.hab_counter_module_compact_streak, today, streak) else c.getString(R.string.hab_counter_module_compact_today, today)
    }

    override fun detail(): String {
        val c = ctx ?: return ""
        val sb = StringBuilder()
        val today = Prefs.getInt(c, "hab_today", 0)
        val streak = Prefs.getInt(c, "hab_streak", 0)
        val yesterday = Prefs.getInt(c, "hab_yesterday", 0)
        val monthly = Prefs.getInt(c, "hab_monthly", 0)
        val allTime = Prefs.getInt(c, "hab_all_time", 0)

        val showStreak = Prefs.getBool(c, "hab_show_streak", true)
        val showYesterday = Prefs.getBool(c, "hab_show_yesterday", true)
        val showAllTime = Prefs.getBool(c, "hab_show_all_time", true)

        sb.append(c.getString(R.string.hab_counter_module_today, today))
        if (streak > 0 && showStreak) sb.append(c.getString(R.string.hab_counter_module_streak, streak))
        if (showYesterday && yesterday >= 0) sb.append(c.getString(R.string.hab_counter_module_yesterday, yesterday))
        sb.append(c.getString(R.string.hab_counter_module_monthly, monthly))
        if (showAllTime) sb.append(c.getString(R.string.hab_counter_module_total, allTime))

        return sb.toString()
    }

    override fun dataPoints(): LinkedHashMap<String, String> {
        val c = ctx ?: return LinkedHashMap()
        val d = LinkedHashMap<String, String>()
        d["habit.today"] = Prefs.getInt(c, "hab_today", 0).toString()
        d["habit.yesterday"] = Prefs.getInt(c, "hab_yesterday", 0).toString()
        val streak = Prefs.getInt(c, "hab_streak", 0)
        d["habit.streak"] = if (streak > 0) c.getString(R.string.hab_counter_module_streak_days, streak) else "0"
        d["habit.monthly"] = Prefs.getInt(c, "hab_monthly", 0).toString()
        d["habit.all_time"] = Prefs.getInt(c, "hab_all_time", 0).toString()
        return d
    }

    @Composable
    override fun dashboardContent(ctx: Context, sys: SystemAccess) {
        Button(
            onClick = {
                val intent = android.content.Intent(ctx, com.extensionbox.app.MonitorService::class.java)
                    .setAction(com.extensionbox.app.MonitorService.ACTION_HABIT_INCREMENT)
                ctx.startService(intent)
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            shape = MaterialTheme.shapes.small,
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            Icon(
                Icons.Default.Add, 
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                ctx.getString(R.string.log_action),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }

    override fun checkAlerts(ctx: Context) {
        val dailyLimit = Prefs.getInt(ctx, "hab_daily_limit", 0)
        if (dailyLimit <= 0) return

        val today = Prefs.getInt(ctx, "hab_today", 0)
        val alertFired = Prefs.getBool(ctx, "hab_limit_fired", false)

        if (today >= dailyLimit && !alertFired) {
            fireAlert(ctx, 2010, ctx.getString(R.string.hab_counter_module_daily_limit_reached_title), ctx.getString(R.string.hab_counter_module_daily_limit_reached_content, dailyLimit))
            Prefs.setBool(ctx, "hab_limit_fired", true)
        } else if (today < dailyLimit) {
            Prefs.setBool(ctx, "hab_limit_fired", false)
        }
    }

    private fun fireAlert(ctx: Context, id: Int, title: String, content: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val n = androidx.core.app.NotificationCompat.Builder(ctx, "alerts")
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(id, n)
    }

    override fun reset() {
        ctx?.let { c ->
            Prefs.setInt(c, "hab_today", 0)
            Prefs.setInt(c, "hab_monthly", 0)
            Prefs.setInt(c, "hab_all_time", 0)
            Prefs.setInt(c, "hab_streak", 0)
            Prefs.setBool(c, "hab_limit_fired", false)
        }
    }
}
