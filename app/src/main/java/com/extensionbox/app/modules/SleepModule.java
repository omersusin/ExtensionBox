package com.extensionbox.app.modules;

import android.content.Context;
import android.os.SystemClock;

import com.extensionbox.app.Fmt;
import com.extensionbox.app.Prefs;
import com.extensionbox.app.SystemAccess;

import java.util.LinkedHashMap;
import java.util.Locale;

public class SleepModule implements Module {

    private Context ctx;
    private boolean running = false;
    private long elapsedStart, uptimeStart;

    @Override public String key() { return "sleep"; }
    @Override public String name() { return "Deep Sleep"; }
    @Override public String emoji() { return "😴"; }
    @Override public String description() { return "CPU sleep vs awake ratio"; }
    @Override public boolean defaultEnabled() { return true; }
    @Override public boolean alive() { return running; }
    @Override public int priority() { return 30; }

    @Override
    public int tickIntervalMs() {
        return ctx != null ? Prefs.getInt(ctx, "slp_interval", 30000) : 30000;
    }

    @Override
    public void start(Context c, SystemAccess sys) {
        ctx = c;
        elapsedStart = SystemClock.elapsedRealtime();
        uptimeStart = SystemClock.uptimeMillis();
        running = true;
    }

    @Override public void stop() { running = false; }
    @Override public void tick() { }

    private float deepPct() {
        long el = SystemClock.elapsedRealtime() - elapsedStart;
        long up = SystemClock.uptimeMillis() - uptimeStart;
        long ds = Math.max(0, el - up);
        return el > 0 ? ds * 100f / el : 0;
    }

    @Override
    public String compact() {
        return String.format(Locale.US, "Sleep:%.0f%%", deepPct());
    }

    @Override
    public String detail() {
        long el = SystemClock.elapsedRealtime() - elapsedStart;
        long up = SystemClock.uptimeMillis() - uptimeStart;
        long ds = Math.max(0, el - up);
        float pct = deepPct();
        return String.format(Locale.US,
                "😴 Deep Sleep: %s (%.1f%%)\n   Awake: %s (%.1f%%)",
                Fmt.duration(ds), pct, Fmt.duration(up), 100 - pct);
    }

    @Override
    public LinkedHashMap<String, String> dataPoints() {
        long el = SystemClock.elapsedRealtime() - elapsedStart;
        long up = SystemClock.uptimeMillis() - uptimeStart;
        long ds = Math.max(0, el - up);
        float pct = deepPct();
        LinkedHashMap<String, String> d = new LinkedHashMap<>();
        d.put("sleep.deep_time", Fmt.duration(ds));
        d.put("sleep.deep_pct", Fmt.pct(pct));
        d.put("sleep.awake_time", Fmt.duration(up));
        d.put("sleep.awake_pct", Fmt.pct(100 - pct));
        return d;
    }

    @Override public void checkAlerts(Context ctx) { }
}
