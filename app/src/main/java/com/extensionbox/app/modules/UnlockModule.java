package com.extensionbox.app.modules;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.core.app.NotificationCompat;

import com.extensionbox.app.Prefs;
import com.extensionbox.app.R;
import com.extensionbox.app.SystemAccess;

import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Locale;

public class UnlockModule implements Module {

    private Context ctx;
    private BroadcastReceiver rcv;
    private boolean running = false;
    private int count;
    private long lastUnlockTime = 0;

    @Override public String key() { return "unlock"; }
    @Override public String name() { return "Unlock Counter"; }
    @Override public String emoji() { return "🔓"; }
    @Override public String description() { return "Daily unlocks, detox tracking"; }
    @Override public boolean defaultEnabled() { return true; }
    @Override public boolean alive() { return running; }

    @Override
    public int tickIntervalMs() {
        return ctx != null ? Prefs.getInt(ctx, "ulk_interval", 10000) : 10000;
    }

    @Override
    public void start(Context c, SystemAccess sys) {
        ctx = c;
        count = Prefs.getInt(c, "ulk_today", 0);

        rcv = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_USER_PRESENT.equals(intent.getAction())) {
                    long now = System.currentTimeMillis();
                    int debounceMs = Prefs.getInt(ctx, "ulk_debounce", 5000);
                    if (now - lastUnlockTime >= debounceMs) {
                        count++;
                        Prefs.setInt(ctx, "ulk_today", count);
                        lastUnlockTime = now;
                    }
                }
            }
        };
        c.registerReceiver(rcv, new IntentFilter(Intent.ACTION_USER_PRESENT));
        running = true;
    }

    @Override
    public void stop() {
        if (rcv != null && ctx != null) {
            try { ctx.unregisterReceiver(rcv); } catch (Exception ignored) {}
        }
        rcv = null;
        running = false;
    }

    @Override
    public void tick() {
        if (ctx == null) return;
        count = Prefs.getInt(ctx, "ulk_today", 0);
        checkDayRollover();
    }

    private void checkDayRollover() {
        int today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        int lastDay = Prefs.getInt(ctx, "ulk_last_day", -1);

        if (lastDay != -1 && lastDay != today) {
            Prefs.setInt(ctx, "ulk_yesterday", count);
            Prefs.setInt(ctx, "ulk_today", 0);
            Prefs.setBool(ctx, "ulk_alert_fired", false);
            count = 0;
        }
        Prefs.setInt(ctx, "ulk_last_day", today);
    }

    @Override
    public String compact() {
        return "🔓" + count;
    }

    @Override
    public String detail() {
        int yesterday = ctx != null ? Prefs.getInt(ctx, "ulk_yesterday", 0) : 0;
        int limit = ctx != null ? Prefs.getInt(ctx, "ulk_daily_limit", 0) : 0;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "🔓 Unlocked: %d times today", count));

        Calendar cal = Calendar.getInstance();
        float hoursSinceMidnight = cal.get(Calendar.HOUR_OF_DAY)
                + cal.get(Calendar.MINUTE) / 60f;
        if (hoursSinceMidnight > 0.1f) {
            float rate = count / hoursSinceMidnight;
            sb.append(String.format(Locale.US, " (%.1f/h)", rate));
        }

        if (yesterday > 0) {
            int diff = count - yesterday;
            String cmp = diff <= 0
                    ? String.format(Locale.US, "↓%d 🎉", Math.abs(diff))
                    : String.format(Locale.US, "↑%d", diff);
            sb.append(String.format(Locale.US, "\n   Yesterday: %d (%s)", yesterday, cmp));
        }

        if (limit > 0) {
            int rem = Math.max(0, limit - count);
            sb.append(String.format(Locale.US, "\n   Limit: %d (%d remaining)", limit, rem));
        }

        return sb.toString();
    }

    @Override
    public LinkedHashMap<String, String> dataPoints() {
        LinkedHashMap<String, String> d = new LinkedHashMap<>();
        d.put("unlock.today", String.valueOf(count));

        if (ctx != null) {
            int yesterday = Prefs.getInt(ctx, "ulk_yesterday", 0);
            d.put("unlock.yesterday", String.valueOf(yesterday));

            if (yesterday > 0) {
                int diff = count - yesterday;
                d.put("unlock.vs_yesterday", diff <= 0
                        ? "↓" + Math.abs(diff) + " 🎉"
                        : "↑" + diff);
            }

            int limit = Prefs.getInt(ctx, "ulk_daily_limit", 0);
            d.put("unlock.limit", limit > 0 ? String.valueOf(limit) : "Off");
            if (limit > 0) {
                d.put("unlock.remaining", String.valueOf(Math.max(0, limit - count)));
            }
        }
        return d;
    }

    @Override
    public void checkAlerts(Context c) {
        int limit = Prefs.getInt(c, "ulk_daily_limit", 0);
        boolean alertEnabled = Prefs.getBool(c, "ulk_limit_alert", true);
        boolean alertFired = Prefs.getBool(c, "ulk_alert_fired", false);

        if (limit > 0 && alertEnabled && count >= limit && !alertFired) {
            try {
                NotificationManager nm = (NotificationManager)
                        c.getSystemService(Context.NOTIFICATION_SERVICE);
                NotificationCompat.Builder b = new NotificationCompat.Builder(c, "ebox_alerts")
                        .setSmallIcon(R.drawable.ic_notif)
                        .setContentTitle("🔴 Unlock Limit Reached")
                        .setContentText("You've unlocked " + count
                                + " times. Limit: " + limit + ". Take a break!")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true);
                nm.notify(2006, b.build());
            } catch (Exception ignored) {}
            Prefs.setBool(c, "ulk_alert_fired", true);
        }
    }
}
