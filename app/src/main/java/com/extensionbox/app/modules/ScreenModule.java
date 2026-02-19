package com.extensionbox.app.modules;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.SystemClock;

import androidx.core.app.NotificationCompat;

import com.extensionbox.app.Fmt;
import com.extensionbox.app.Prefs;
import com.extensionbox.app.R;
import com.extensionbox.app.SystemAccess;

import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Locale;

public class ScreenModule implements Module {
    private Context ctx;
    private BroadcastReceiver rcv;
    private boolean running = false;
    private boolean screenOn = true;

    private long onAccMs, offAccMs;
    private long periodStart;
    private int periodStartLevel;

    private float onDrain, offDrain;

    @Override public String key() { return "screen"; }
    @Override public String name() { return "Screen Time"; }
    @Override public String emoji() { return "📱"; }
    @Override public String description() { return "Screen on/off time, drain rates"; }
    @Override public boolean defaultEnabled() { return true; }
    @Override public boolean alive() { return running; }
    @Override public int priority() { return 20; }

    @Override
    public int tickIntervalMs() {
        return ctx != null ? Prefs.getInt(ctx, "scr_interval", 10000) : 10000;
    }

    @Override
    public void start(Context c, SystemAccess sys) {
        ctx = c;
        onAccMs = Prefs.getLong(c, "scr_on_acc", 0);
        offAccMs = 0;
        onDrain = 0;
        offDrain = 0;
        periodStart = SystemClock.elapsedRealtime();
        periodStartLevel = getBatteryLevel();

        rcv = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long now = SystemClock.elapsedRealtime();
                long dt = now - periodStart;
                int curLevel = getBatteryLevel();
                float drain = Math.max(0, periodStartLevel - curLevel);

                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    onAccMs += dt;
                    onDrain += drain;
                    screenOn = false;
                } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                    offAccMs += dt;
                    offDrain += drain;
                    screenOn = true;
                }
                periodStart = now;
                periodStartLevel = curLevel;
                Prefs.setLong(ctx, "scr_on_acc", onAccMs);
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        c.registerReceiver(rcv, f);
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
        int today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        int lastDay = Prefs.getInt(ctx, "scr_last_day", -1);
        if (lastDay != -1 && lastDay != today) {
            Prefs.setLong(ctx, "scr_yesterday_on", onAccMs);
            onAccMs = 0;
            offAccMs = 0;
            onDrain = 0;
            offDrain = 0;
            periodStart = SystemClock.elapsedRealtime();
            periodStartLevel = getBatteryLevel();
            Prefs.setLong(ctx, "scr_on_acc", 0);
            Prefs.setBool(ctx, "scr_alert_fired", false);
        }
        Prefs.setInt(ctx, "scr_last_day", today);
    }

    private int getBatteryLevel() {
        try {
            BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
            return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        } catch (Exception e) { return 0; }
    }

    private long getTotalOn() {
        long now = SystemClock.elapsedRealtime();
        return onAccMs + (screenOn ? (now - periodStart) : 0);
    }

    private long getTotalOff() {
        long now = SystemClock.elapsedRealtime();
        return offAccMs + (!screenOn ? (now - periodStart) : 0);
    }

    @Override
    public String compact() {
        return "On: " + Fmt.duration(getTotalOn());
    }

    @Override
    public String detail() {
        long on = getTotalOn();
        long off = getTotalOff();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "📱 Screen On: %s", Fmt.duration(on)));

        if (Prefs.getBool(ctx, "scr_show_drain", true)) {
            float curDrain = screenOn ? Math.max(0, periodStartLevel - getBatteryLevel()) : 0;
            float totalOnDrain = onDrain + (screenOn ? curDrain : 0);
            sb.append(String.format(Locale.US, " • %.1f%%", totalOnDrain));
            sb.append(String.format(Locale.US, "\n   Screen Off: %s • %.1f%%", Fmt.duration(off), offDrain));
            if (on > 60000) {
                float rateOn = totalOnDrain / (on / 3600000f);
                sb.append(String.format(Locale.US, "\n   Active: %.1f%%/h", rateOn));
            }
        } else {
            sb.append(String.format(Locale.US, "\n   Screen Off: %s", Fmt.duration(off)));
        }

        if (Prefs.getBool(ctx, "scr_show_yesterday", true)) {
            long yOn = Prefs.getLong(ctx, "scr_yesterday_on", 0);
            if (yOn > 0) {
                long diff = on - yOn;
                int pct = (int)(diff * 100 / yOn);
                String cmp = pct <= 0 ? "↓" + Math.abs(pct) + "% 🎉" : "↑" + pct + "%";
                sb.append(String.format(Locale.US, "\n   Yesterday: %s (%s)", Fmt.duration(yOn), cmp));
            }
        }
        return sb.toString();
    }

    @Override
    public LinkedHashMap<String, String> dataPoints() {
        LinkedHashMap<String, String> d = new LinkedHashMap<>();
        d.put("screen.on_time", Fmt.duration(getTotalOn()));
        d.put("screen.off_time", Fmt.duration(getTotalOff()));
        return d;
    }

    @Override
    public void checkAlerts(Context c) {
        int limitMin = Prefs.getInt(c, "scr_time_limit", 0);
        if (limitMin <= 0) return;
        boolean fired = Prefs.getBool(c, "scr_alert_fired", false);
        long onMs = getTotalOn();
        long limitMs = limitMin * 60000L;

        if (onMs >= limitMs && !fired) {
            try {
                NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
                nm.notify(2004, new NotificationCompat.Builder(c, "ebox_alerts")
                        .setSmallIcon(R.drawable.ic_notif)
                        .setContentTitle("🔴 Screen Time Limit")
                        .setContentText("Screen on for " + Fmt.duration(onMs) + ". Take a break!")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true).build());
            } catch (Exception ignored) {}
            Prefs.setBool(c, "scr_alert_fired", true);
        }
    }
}
