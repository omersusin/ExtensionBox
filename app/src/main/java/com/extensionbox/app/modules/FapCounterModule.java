package com.extensionbox.app.modules;

import android.app.NotificationManager;
import android.content.Context;

import androidx.core.app.NotificationCompat;

import com.extensionbox.app.Prefs;
import com.extensionbox.app.R;
import com.extensionbox.app.SystemAccess;

import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Locale;

public class FapCounterModule implements Module {

    private Context ctx;
    private boolean running = false;

    @Override public String key() { return "fap"; }
    @Override public String name() { return "Fap Counter"; }
    @Override public String emoji() { return "🍆"; }
    @Override public String description() { return "Self-monitoring counter & streak tracker"; }
    @Override public boolean defaultEnabled() { return false; }
    @Override public boolean alive() { return running; }
    @Override public int priority() { return 100; }

    @Override
    public int tickIntervalMs() {
        return ctx != null ? Prefs.getInt(ctx, "fap_interval", 60000) : 60000;
    }

    @Override
    public void start(Context c, SystemAccess sys) {
        ctx = c;
        running = true;
        checkDayRollover();
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public void tick() {
        checkDayRollover();
    }

    /**
     * Called externally (e.g., from notification action or dashboard) to increment counter.
     */
    public void increment() {
        if (ctx == null) return;
        int today = Prefs.getInt(ctx, "fap_today", 0);
        today++;
        Prefs.setInt(ctx, "fap_today", today);

        // Update monthly total
        int monthly = Prefs.getInt(ctx, "fap_monthly", 0);
        Prefs.setInt(ctx, "fap_monthly", monthly + 1);

        // Update all-time total
        int allTime = Prefs.getInt(ctx, "fap_all_time", 0);
        Prefs.setInt(ctx, "fap_all_time", allTime + 1);

        // Reset streak (days without)
        Prefs.setInt(ctx, "fap_streak", 0);
        Prefs.setInt(ctx, "fap_last_day", getDayOfYear());
    }

    /**
     * Check if day changed and update streak/daily counter accordingly.
     */
    private void checkDayRollover() {
        if (ctx == null) return;
        int currentDay = getDayOfYear();
        int lastDay = Prefs.getInt(ctx, "fap_last_check_day", -1);

        if (lastDay == -1) {
            // First run
            Prefs.setInt(ctx, "fap_last_check_day", currentDay);
            return;
        }

        if (currentDay != lastDay) {
            // Day changed
            int todayCount = Prefs.getInt(ctx, "fap_today", 0);

            if (todayCount == 0) {
                // No faps yesterday → increase streak
                int streak = Prefs.getInt(ctx, "fap_streak", 0);
                Prefs.setInt(ctx, "fap_streak", streak + 1);
            }

            // Save yesterday's count
            Prefs.setInt(ctx, "fap_yesterday", todayCount);

            // Reset daily counter
            Prefs.setInt(ctx, "fap_today", 0);

            // Check month rollover
            int currentMonth = Calendar.getInstance().get(Calendar.MONTH);
            int lastMonth = Prefs.getInt(ctx, "fap_last_month", -1);
            if (lastMonth != -1 && currentMonth != lastMonth) {
                Prefs.setInt(ctx, "fap_prev_monthly", Prefs.getInt(ctx, "fap_monthly", 0));
                Prefs.setInt(ctx, "fap_monthly", 0);
            }
            Prefs.setInt(ctx, "fap_last_month", currentMonth);

            Prefs.setInt(ctx, "fap_last_check_day", currentDay);
        }
    }

    public int getTodayCount() {
        return ctx != null ? Prefs.getInt(ctx, "fap_today", 0) : 0;
    }

    private int getDayOfYear() {
        return Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
    }

    @Override
    public String compact() {
        int today = ctx != null ? Prefs.getInt(ctx, "fap_today", 0) : 0;
        int streak = ctx != null ? Prefs.getInt(ctx, "fap_streak", 0) : 0;
        if (streak > 0) return "🍆" + today + " 🔥" + streak + "d";
        return "🍆" + today + " today";
    }

    @Override
    public String detail() {
        if (ctx == null) return "🍆 No data";
        int today = Prefs.getInt(ctx, "fap_today", 0);
        int streak = Prefs.getInt(ctx, "fap_streak", 0);
        int monthly = Prefs.getInt(ctx, "fap_monthly", 0);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "🍆 Today: %d", today));
        if (streak > 0) sb.append(String.format(Locale.US, " • 🔥 Streak: %dd clean", streak));
        sb.append(String.format(Locale.US, "\n   Monthly: %d", monthly));
        return sb.toString();
    }

    @Override
    public LinkedHashMap<String, String> dataPoints() {
        LinkedHashMap<String, String> d = new LinkedHashMap<>();
        if (ctx == null) return d;

        int today = Prefs.getInt(ctx, "fap_today", 0);
        int yesterday = Prefs.getInt(ctx, "fap_yesterday", 0);
        int streak = Prefs.getInt(ctx, "fap_streak", 0);
        int monthly = Prefs.getInt(ctx, "fap_monthly", 0);
        int allTime = Prefs.getInt(ctx, "fap_all_time", 0);

        d.put("fap.today", String.valueOf(today));
        d.put("fap.yesterday", String.valueOf(yesterday));
        d.put("fap.streak", streak > 0 ? streak + " days 🔥" : "0");
        d.put("fap.monthly", String.valueOf(monthly));
        d.put("fap.all_time", String.valueOf(allTime));
        return d;
    }

    @Override
    public void checkAlerts(Context ctx) {
        int dailyLimit = Prefs.getInt(ctx, "fap_daily_limit", 0);
        if (dailyLimit <= 0) return;

        int today = Prefs.getInt(ctx, "fap_today", 0);
        boolean alertFired = Prefs.getBool(ctx, "fap_limit_fired", false);

        if (today >= dailyLimit && !alertFired) {
            fireAlert(ctx, 2010,
                    "🍆 Daily Limit Reached",
                    "You've reached your daily limit of " + dailyLimit + ". Take a break!");
            Prefs.setBool(ctx, "fap_limit_fired", true);
        }

        // Reset alert flag at day change
        if (today == 0 && alertFired) {
            Prefs.setBool(ctx, "fap_limit_fired", false);
        }
    }

    private void fireAlert(Context c, int id, String title, String body) {
        try {
            NotificationManager nm = (NotificationManager)
                    c.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationCompat.Builder b = new NotificationCompat.Builder(c, "ebox_alerts")
                    .setSmallIcon(R.drawable.ic_notif)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true);
            nm.notify(id, b.build());
        } catch (Exception ignored) {}
    }
}
