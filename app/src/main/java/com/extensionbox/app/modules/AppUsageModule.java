package com.extensionbox.app.modules;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.PackageManager;

import com.extensionbox.app.Prefs;
import com.extensionbox.app.SystemAccess;

import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class AppUsageModule implements Module {

    private Context ctx;
    private boolean running = false;

    private String topApp = "—";
    private long topAppMs = 0;
    private long totalScreenMs = 0;

    @Override public String key() { return "app_usage"; }
    @Override public String name() { return "App Usage"; }
    @Override public String emoji() { return "📲"; }
    @Override public String description() { return "Daily per-app screen time"; }
    @Override public boolean defaultEnabled() { return false; }
    @Override public boolean alive() { return running; }
    @Override public int tickIntervalMs() { return Prefs.getInt(ctx, "appusage_interval", 60000); }

    @Override
    public void start(Context c, SystemAccess sys) {
        ctx = c;
        running = true;
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public void tick() {
        if (ctx == null) return;
        try {
            UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return;

            Calendar cal = Calendar.getInstance();
            long endTime = cal.getTimeInMillis();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long startTime = cal.getTimeInMillis();

            List<UsageStats> stats = usm.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY, startTime, endTime);

            if (stats == null || stats.isEmpty()) {
                topApp = "No permission";
                topAppMs = 0;
                totalScreenMs = 0;
                return;
            }

            String bestPackage = null;
            long bestMs = 0;
            long total = 0;
            PackageManager pm = ctx.getPackageManager();

            for (UsageStats s : stats) {
                long ms = s.getTotalTimeInForeground();
                if (ms <= 0) continue;
                total += ms;
                if (ms > bestMs) {
                    bestMs = ms;
                    bestPackage = s.getPackageName();
                }
            }

            totalScreenMs = total;
            topAppMs = bestMs;

            if (bestPackage != null) {
                try {
                    topApp = (String) pm.getApplicationLabel(pm.getApplicationInfo(bestPackage, 0));
                } catch (Exception e) {
                    topApp = bestPackage;
                }
            } else {
                topApp = "—";
            }
        } catch (Exception e) {
            topApp = "Error";
        }
    }

    private String formatMs(long ms) {
        long hours = TimeUnit.MILLISECONDS.toHours(ms);
        long mins = TimeUnit.MILLISECONDS.toMinutes(ms) % 60;
        if (hours > 0) return String.format(Locale.US, "%dh %dm", hours, mins);
        return String.format(Locale.US, "%dm", mins);
    }

    @Override
    public String compact() {
        return "📲 " + topApp + " " + formatMs(topAppMs);
    }

    @Override
    public String detail() {
        return "📲 Top: " + topApp + " (" + formatMs(topAppMs) + ")\n   Total today: " + formatMs(totalScreenMs);
    }

    @Override
    public LinkedHashMap<String, String> dataPoints() {
        LinkedHashMap<String, String> d = new LinkedHashMap<>();
        d.put("usage.top_app", topApp);
        d.put("usage.top_time", formatMs(topAppMs));
        d.put("usage.total", formatMs(totalScreenMs));
        return d;
    }

    @Override public void checkAlerts(Context ctx) {}
}
