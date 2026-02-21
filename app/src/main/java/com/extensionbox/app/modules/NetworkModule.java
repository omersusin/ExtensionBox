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

    private long prevRx, prevTx;
    private long prevTime;
    private long dlSpeed, ulSpeed;

    private long prevDlSpeed, prevUlSpeed;

    @Override public String key() { return "network"; }
    @Override public String name() { return "Network Speed"; }
    @Override public String emoji() { return "📶"; }
    @Override public String description() { return "Real-time download and upload speed"; }
    @Override public boolean defaultEnabled() { return true; }
    @Override public boolean alive() { return running; }

    @Override
    public int tickIntervalMs() {
        return ctx != null ? Prefs.getInt(ctx, "net_interval", 3000) : 3000;
    }

    @Override
    public void start(Context c, SystemAccess sys) {
        ctx = c;
        prevTime = SystemClock.elapsedRealtime();

        long[] stats = readNetworkStats();
        prevRx = stats[0];
        prevTx = stats[1];

        dlSpeed = 0;
        ulSpeed = 0;
        prevDlSpeed = 0;
        prevUlSpeed = 0;
        running = true;
    }

    private long[] readNetworkStats() {
        long rx = TrafficStats.getTotalRxBytes();
        long tx = TrafficStats.getTotalTxBytes();

        if (rx == TrafficStats.UNSUPPORTED || tx == TrafficStats.UNSUPPORTED) {

            long procRx = 0, procTx = 0;
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader("/proc/net/dev"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("lo:")) continue;
                    String[] tokens = line.trim().split("\\s+");
                    if (tokens.length >= 10) {
                        try {
                            String rxStr = tokens[1];
                            String txStr = tokens[tokens.length > 16 ? 9 : 9];

                            procRx += Long.parseLong(rxStr);
                            procTx += Long.parseLong(txStr);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                return new long[]{procRx, procTx};
            } catch (Exception e) {
                return new long[]{0, 0};
            }
        }
        return new long[]{rx, tx};
    }

    @Override
    public void stop() {
        running = false;
        dlSpeed = 0;
        ulSpeed = 0;
    }

    @Override
    public void tick() {
        long[] stats = readNetworkStats();
        long rx = stats[0];
        long tx = stats[1];

        if (rx == 0 && tx == 0 && prevRx == 0 && prevTx == 0) {

            dlSpeed = 0;
            ulSpeed = 0;
            return;
        }

        long now = SystemClock.elapsedRealtime();
        long dtMs = now - prevTime;

        if (dtMs > 0) {
            long rxDelta = rx - prevRx;
            long txDelta = tx - prevTx;

            if (rxDelta < 0) rxDelta = 0;
            if (txDelta < 0) txDelta = 0;

            long rawDl = rxDelta * 1000 / dtMs;
            long rawUl = txDelta * 1000 / dtMs;

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

    }
}
