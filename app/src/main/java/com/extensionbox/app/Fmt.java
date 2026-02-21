package com.extensionbox.app;

import java.util.Locale;

public final class Fmt {

    public static String duration(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000;
        long m = s / 60;
        long h = m / 60;
        long d = h / 24;
        if (d > 0) return String.format(Locale.US, "%dd %dh", d, h % 24);
        if (h > 0) return String.format(Locale.US, "%dh %dm", h, m % 60);
        if (m > 0) return String.format(Locale.US, "%dm %ds", m, s % 60);
        return String.format(Locale.US, "%ds", s);
    }

    public static String bytes(long b) {
        if (b < 0) b = 0;
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024)
            return String.format(Locale.US, "%.1f KB", b / 1024.0);
        if (b < 1024L * 1024 * 1024)
            return String.format(Locale.US, "%.1f MB", b / (1024.0 * 1024));
        return String.format(Locale.US, "%.2f GB", b / (1024.0 * 1024 * 1024));
    }

    public static String speed(long bytesPerSec) {
        if (bytesPerSec < 0) bytesPerSec = 0;
        if (bytesPerSec < 1024) return bytesPerSec + " B/s";
        if (bytesPerSec < 1024 * 1024)
            return String.format(Locale.US, "%.1f KB/s", bytesPerSec / 1024.0);
        return String.format(Locale.US, "%.1f MB/s", bytesPerSec / (1024.0 * 1024));
    }

    public static String pct(float p) {
        if (Float.isNaN(p)) return "—";
        return String.format(Locale.US, "%.1f%%", p);
    }

    public static String temp(float celsius) {
        if (Float.isNaN(celsius)) return "—";
        return String.format(Locale.US, "%.1f°C", celsius);
    }

    public static String number(long n) {
        return String.format(Locale.US, "%,d", n);
    }
}
