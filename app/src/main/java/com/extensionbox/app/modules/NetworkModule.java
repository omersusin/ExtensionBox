package com.extensionbox.app.modules;

import android.content.Context;
import android.net.TrafficStats;
import android.os.SystemClock;

import com.extensionbox.app.Fmt;
import com.extensionbox.app.Prefs;
import com.extensionbox.app.SystemAccess;

import java.util.LinkedHashMap;
import java.util.Locale;

public class NetworkModule implements Module {

    private Context ctx;
    private boolean running = false;

    // Separate tracking for Rx and Tx
    private long prevRx, prevTx;
    private long prevTime;
    private long dlSpeed, ulSpeed; // bytes/sec

    // Smoothing: keep previous values to average
    private long prevDlSpeed, prevUlSpeed;

    @Override public String key() { return "network"; }
    @Override public String name() { return "Network Speed"; }
    @Override public String emoji() { return "📶"; }
    @Override public String description() { return "Real-time download and upload speed"; }
    @Override public boolean defaultEnabled() { return true; }
    @Override public boolean alive() { return running; }
    @Override public int priority() { return 40; }

    @Override
    public int tickIntervalMs() {
        return ctx != null ? Prefs.getInt(ctx, "net_interval", 3000) : 3000;
    }

    @Override
    public void start(Context c, SystemAccess sys) {
        ctx = c;
        // Use elapsedRealtime for more accurate timing (not affected by wall clock changes)
        prevTime = SystemClock.elapsedRealtime();

        // Read initial values
        long rx = TrafficStats.getTotalRxBytes();
        long tx = TrafficStats.getTotalTxBytes();

        // TrafficStats returns UNSUPPORTED (-1) on some devices
        prevRx = (rx != TrafficStats.UNSUPPORTED) ? rx : 0;
        prevTx = (tx != TrafficStats.UNSUPPORTED) ? tx : 0;

        dlSpeed = 0;
        ulSpeed = 0;
        prevDlSpeed = 0;
        prevUlSpeed = 0;
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        dlSpeed = 0;
        ulSpeed = 0;
    }

    @Override
    public void tick() {
        long rx = TrafficStats.getTotalRxBytes();
        long tx = TrafficStats.getTotalTxBytes();

        if (rx == TrafficStats.UNSUPPORTED || tx == TrafficStats.UNSUPPORTED) {
            dlSpeed = 0;
            ulSpeed = 0;
            return;
        }

        long now = SystemClock.elapsedRealtime();
        long dtMs = now - prevTime;

        if (dtMs > 0) {
            long rxDelta = rx - prevRx;
            long txDelta = tx - prevTx;

            // Guard against counter reset or negative values
            if (rxDelta < 0) rxDelta = 0;
            if (txDelta < 0) txDelta = 0;

            // Calculate bytes per second
            long rawDl = rxDelta * 1000 / dtMs;
            long rawUl = txDelta * 1000 / dtMs;

            // Exponential moving average for smoother display
            // weight: 60% new, 40% old
            dlSpeed = (rawDl * 6 + prevDlSpeed * 4) / 10;
            ulSpeed = (rawUl * 6 + prevUlSpeed * 4) / 10;

            prevDlSpeed = dlSpeed;
            prevUlSpeed = ulSpeed;
        }

        prevRx = rx;
        prevTx = tx;
        prevTime = now;
    }

    @Override
    public String compact() {
        return "↓" + Fmt.speed(dlSpeed) + " ↑" + Fmt.speed(ulSpeed);
    }

    @Override
    public String detail() {
        return String.format(Locale.US,
                "📶 Download: %s\n   Upload: %s",
                Fmt.speed(dlSpeed), Fmt.speed(ulSpeed));
    }

    @Override
    public LinkedHashMap<String, String> dataPoints() {
        LinkedHashMap<String, String> d = new LinkedHashMap<>();
        d.put("net.download", Fmt.speed(dlSpeed));
        d.put("net.upload", Fmt.speed(ulSpeed));
        return d;
    }

    @Override
    public void checkAlerts(Context ctx) {
        // No alerts for network speed
    }
}