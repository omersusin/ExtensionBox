package com.extensionbox.app.modules;

import android.content.Context;
import android.os.SystemClock;

import com.extensionbox.app.Fmt;
import com.extensionbox.app.Prefs;
import com.extensionbox.app.SystemAccess;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;

public class UptimeModule implements Module {

    private Context ctx;
    private boolean running = false;
    private long bootTimestamp;

    @Override public String key() { return "uptime"; }
    @Override public String name() { return "Uptime"; }
    @Override public String emoji() { return "⏱"; }
    @Override public String description() { return "Device uptime since boot"; }
    @Override public boolean defaultEnabled() { return false; }
    @Override public boolean alive() { return running; }
    @Override public int priority() { return 95; }

    @Override
    public int tickIntervalMs() {
        return ctx != null ? Prefs.getInt(ctx, "upt_interval", 60000) : 60000;
    }

    @Override
    public void start(Context c, SystemAccess sys) {
        ctx = c;
        bootTimestamp = System.currentTimeMillis() - SystemClock.elapsedRealtime();

        int reboots = Prefs.getInt(c, "upt_reboot_count", 0);
        long lastBoot = Prefs.getLong(c, "upt_last_boot", 0);
        if (Math.abs(bootTimestamp - lastBoot) > 60000) {
            reboots++;
            Prefs.setInt(c, "upt_reboot_count", reboots);
            Prefs.setLong(c, "upt_last_boot", bootTimestamp);
        }
        running = true;
    }

    @Override public void stop() { running = false; }
    @Override public void tick() { }

    @Override
    public String compact() {
        return "⏱" + Fmt.duration(SystemClock.elapsedRealtime());
    }

    @Override
    public String detail() {
        String bootStr = new SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(new Date(bootTimestamp));
        int reboots = ctx != null ? Prefs.getInt(ctx, "upt_reboot_count", 0) : 0;
        return String.format(Locale.US,
                "⏱ Uptime: %s\n   Last boot: %s\n   Reboots tracked: %d",
                Fmt.duration(SystemClock.elapsedRealtime()), bootStr, reboots);
    }

    @Override
    public LinkedHashMap<String, String> dataPoints() {
        String bootStr = new SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(new Date(bootTimestamp));
        int reboots = ctx != null ? Prefs.getInt(ctx, "upt_reboot_count", 0) : 0;
        LinkedHashMap<String, String> d = new LinkedHashMap<>();
        d.put("uptime.duration", Fmt.duration(SystemClock.elapsedRealtime()));
        d.put("uptime.boot_time", bootStr);
        d.put("uptime.reboots", String.valueOf(reboots));
        return d;
    }

    @Override public void checkAlerts(Context ctx) { }
}
