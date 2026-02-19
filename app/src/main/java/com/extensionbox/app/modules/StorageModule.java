package com.extensionbox.app.modules;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Environment;
import android.os.StatFs;

import androidx.core.app.NotificationCompat;

import com.extensionbox.app.Fmt;
import com.extensionbox.app.Prefs;
import com.extensionbox.app.R;
import com.extensionbox.app.SystemAccess;

import java.util.LinkedHashMap;
import java.util.Locale;

public class StorageModule implements Module {

    private Context ctx;
    private boolean running = false;
    private long intUsed, intFree, intTotal;

    @Override public String key() { return "storage"; }
    @Override public String name() { return "Storage"; }
    @Override public String emoji() { return "💾"; }
    @Override public String description() { return "Internal storage usage"; }
    @Override public boolean defaultEnabled() { return false; }
    @Override public boolean alive() { return running; }
    @Override public int priority() { return 85; }

    @Override
    public int tickIntervalMs() {
        return ctx != null ? Prefs.getInt(ctx, "sto_interval", 300000) : 300000;
    }

    @Override
    public void start(Context c, SystemAccess sys) {
        ctx = c;
        running = true;
    }

    @Override public void stop() { running = false; }

    @Override
    public void tick() {
        try {
            StatFs sf = new StatFs(Environment.getDataDirectory().getPath());
            intTotal = sf.getTotalBytes();
            intFree = sf.getAvailableBytes();
            intUsed = intTotal - intFree;
        } catch (Exception ignored) {}
    }

    @Override
    public String compact() {
        return "💾" + Fmt.bytes(intUsed) + "/" + Fmt.bytes(intTotal);
    }

    @Override
    public String detail() {
        float pct = intTotal > 0 ? intUsed * 100f / intTotal : 0;
        return String.format(Locale.US,
                "💾 Internal: %s / %s (%.1f%%)\n   Free: %s",
                Fmt.bytes(intUsed), Fmt.bytes(intTotal), pct, Fmt.bytes(intFree));
    }

    @Override
    public LinkedHashMap<String, String> dataPoints() {
        float pct = intTotal > 0 ? intUsed * 100f / intTotal : 0;
        LinkedHashMap<String, String> d = new LinkedHashMap<>();
        d.put("storage.used", Fmt.bytes(intUsed));
        d.put("storage.free", Fmt.bytes(intFree));
        d.put("storage.total", Fmt.bytes(intTotal));
        d.put("storage.pct", String.format(Locale.US, "%.1f%%", pct));
        return d;
    }

    @Override
    public void checkAlerts(Context c) {
        boolean on = Prefs.getBool(c, "sto_low_alert", true);
        int threshMb = Prefs.getInt(c, "sto_low_thresh_mb", 1000);
        boolean fired = Prefs.getBool(c, "sto_low_alert_fired", false);
        long threshBytes = threshMb * 1024L * 1024L;

        if (on && intFree < threshBytes && intFree > 0 && !fired) {
            try {
                NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
                nm.notify(2007, new NotificationCompat.Builder(c, "ebox_alerts")
                        .setSmallIcon(R.drawable.ic_notif)
                        .setContentTitle("🔴 Low Storage")
                        .setContentText("Only " + Fmt.bytes(intFree) + " remaining")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true).build());
            } catch (Exception ignored) {}
            Prefs.setBool(c, "sto_low_alert_fired", true);
        }
        if (fired && intFree > threshBytes + 500L * 1024 * 1024) {
            Prefs.setBool(c, "sto_low_alert_fired", false);
        }
    }
}
