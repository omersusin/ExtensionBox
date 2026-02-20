package com.extensionbox.app;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.BatteryManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.Locale;

/**
 * SystemAccess: unified class for reading system data via Normal/Shizuku/Root tiers.
 *
 * Detection order: Root → Shizuku → Normal.
 * All read methods are wrapped in try-catch. Never crash. Return fallback values silently.
 * If enhanced tier is unavailable, gracefully degrade to standard Android APIs.
 */
public class SystemAccess {

    public static final String TIER_ROOT = "Root";
    public static final String TIER_SHIZUKU = "Shizuku";
    public static final String TIER_NORMAL = "Normal";

    private final String tier;
    private final boolean rootAvailable;
    private final boolean shizukuAvailable;

    public SystemAccess(Context ctx) {
        // Detect best available tier: Root > Shizuku > Normal
        rootAvailable = detectRoot();
        shizukuAvailable = !rootAvailable && detectShizuku(ctx);

        if (rootAvailable) {
            tier = TIER_ROOT;
        } else if (shizukuAvailable) {
            tier = TIER_SHIZUKU;
        } else {
            tier = TIER_NORMAL;
        }
    }

    // ── Tier info ────────────────────────────────────────────────

    public String getTierName() {
        return tier;
    }

    /** Returns true if Root or Shizuku is available (enhanced data access). */
    public boolean isEnhanced() {
        return rootAvailable || shizukuAvailable;
    }

    // ── Root detection ──────────────────────────────────────────

    private static boolean detectRoot() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = br.readLine();
            br.close();
            int exitCode = p.waitFor();
            return exitCode == 0 && line != null && line.contains("uid=0");
        } catch (Exception e) {
            return false;
        }
    }

    // ── Shizuku detection ───────────────────────────────────────

    private static boolean detectShizuku(Context ctx) {
        try {
            // Check if Shizuku is installed
            ctx.getPackageManager().getPackageInfo("moe.shizuku.privileged.api", 0);
            // Try to use Shizuku API via reflection (optional dependency)
            Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
            boolean alive = (boolean) shizukuClass.getMethod("pingBinder").invoke(null);
            if (!alive) return false;
            int result = (int) shizukuClass.getMethod("checkSelfPermission").invoke(null);
            return result == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    // ── Sysfs reader ────────────────────────────────────────────

    /**
     * Read a sysfs file. Tries direct read first, then root, then Shizuku.
     * Returns null on failure.
     */
    public String readSysFile(String path) {
        // Try direct read (works for some files even without root)
        String val = readFileDirect(path);
        if (val != null) return val;

        // Try root
        if (rootAvailable) {
            val = readFileRoot(path);
            if (val != null) return val;
        }

        // Try Shizuku
        if (shizukuAvailable) {
            val = readFileShizuku(path);
            if (val != null) return val;
        }

        return null;
    }

    private static String readFileDirect(String path) {
        try {
            File f = new File(path);
            if (!f.exists() || !f.canRead()) return null;
            BufferedReader br = new BufferedReader(new FileReader(f));
            String line = br.readLine();
            br.close();
            return (line != null && !line.isEmpty()) ? line.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String readFileRoot(String path) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "cat " + path});
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = br.readLine();
            br.close();
            p.waitFor();
            return (line != null && !line.isEmpty()) ? line.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String readFileShizuku(String path) {
        try {
            // Use Shizuku to run cat via reflection
            Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
            // ShizukuRemoteProcess approach — try newProcess
            // For simplicity, try Runtime exec since Shizuku patches it
            Process p = Runtime.getRuntime().exec(new String[]{"cat", path});
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = br.readLine();
            br.close();
            p.waitFor();
            return (line != null && !line.isEmpty()) ? line.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ── Battery data via sysfs ──────────────────────────────────

    /**
     * Read battery current in mA.
     * Enhanced: reads /sys/class/power_supply/battery/current_now (μA / 1000).
     * Normal: uses BatteryManager API.
     */
    public int readBatteryCurrentMa(Context ctx) {
        // Try sysfs first (more accurate)
        if (isEnhanced()) {
            String val = readSysFile("/sys/class/power_supply/battery/current_now");
            if (val != null) {
                try {
                    long ua = Long.parseLong(val);
                    return (int) (ua / 1000);
                } catch (NumberFormatException ignored) {}
            }
        }

        // Fallback: standard API
        try {
            BatteryManager bm = (BatteryManager)
                    ctx.getSystemService(Context.BATTERY_SERVICE);
            if (bm == null) return 0;
            int ua = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            return ua / 1000;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Read design capacity in mAh.
     * Enhanced: reads /sys/class/power_supply/battery/charge_full_design (μAh / 1000).
     * Normal: uses PowerProfile reflection.
     */
    public int readDesignCapacity(Context ctx) {
        // Try sysfs first
        if (isEnhanced()) {
            String val = readSysFile("/sys/class/power_supply/battery/charge_full_design");
            if (val != null) {
                try {
                    long uah = Long.parseLong(val);
                    int mah = (int) (uah / 1000);
                    if (mah > 0 && mah < 100000) return mah;
                } catch (NumberFormatException ignored) {}
            }
        }

        // Fallback: PowerProfile reflection
        try {
            Object pp = Class.forName("com.android.internal.os.PowerProfile")
                    .getConstructor(Context.class).newInstance(ctx);
            double cap = (double) Class.forName("com.android.internal.os.PowerProfile")
                    .getMethod("getBatteryCapacity").invoke(pp);
            return cap > 0 ? (int) cap : 4000;
        } catch (Exception e) {
            return 4000;
        }
    }

    /**
     * Read actual remaining capacity in mAh (charge_full).
     * Only available on enhanced tier. Returns -1 if unavailable.
     */
    public int readActualCapacity() {
        if (!isEnhanced()) return -1;
        String val = readSysFile("/sys/class/power_supply/battery/charge_full");
        if (val == null) return -1;
        try {
            long uah = Long.parseLong(val);
            int mah = (int) (uah / 1000);
            return (mah > 0 && mah < 100000) ? mah : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Read battery cycle count. Only available on enhanced tier. Returns -1 if unavailable.
     */
    public int readCycleCount() {
        if (!isEnhanced()) return -1;
        String val = readSysFile("/sys/class/power_supply/battery/cycle_count");
        if (val == null) return -1;
        try {
            int cycles = Integer.parseInt(val);
            return cycles >= 0 ? cycles : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Read battery technology (e.g., "Li-poly", "Li-ion").
     * Only available on enhanced tier. Returns null if unavailable.
     */
    public String readBatteryTechnology() {
        if (!isEnhanced()) return null;
        return readSysFile("/sys/class/power_supply/battery/technology");
    }

    /**
     * Read CPU temperature in °C.
     * Tries multiple thermal zone paths.
     * Returns Float.NaN if unavailable.
     */
    public float readCpuTemp() {
        // Common thermal zone paths for CPU temperature
        String[] paths = {
                "/sys/devices/virtual/thermal/thermal_zone0/temp",
                "/sys/class/thermal/thermal_zone0/temp",
                "/sys/devices/virtual/thermal/thermal_zone1/temp",
                "/sys/class/thermal/thermal_zone1/temp",
                "/sys/devices/virtual/thermal/thermal_zone2/temp",
        };

        for (String path : paths) {
            String val = readSysFile(path);
            if (val != null) {
                try {
                    float raw = Float.parseFloat(val);
                    // Some devices report in millidegrees
                    if (raw > 1000) raw /= 1000f;
                    // Sanity check
                    if (raw > 0 && raw < 150) return raw;
                } catch (NumberFormatException ignored) {}
            }
        }

        return Float.NaN;
    }

    /**
     * Read real battery health percentage: (charge_full / charge_full_design) × 100.
     * Only available on enhanced tier. Returns -1 if unavailable.
     */
    public int readRealHealthPct(Context ctx) {
        int actualCap = readActualCapacity();
        int designCap = readDesignCapacity(ctx);
        if (actualCap <= 0 || designCap <= 0) return -1;
        int pct = actualCap * 100 / designCap;
        return (pct > 0 && pct <= 200) ? pct : -1;
    }

    /**
     * Fallback CPU usage reader for Normal tier on Android 8+.
     * Uses 'top' command to get a snapshot.
     */
    public float readCpuUsageFallback() {
        try {
            // Run top command once
            Process p = Runtime.getRuntime().exec(new String[]{"top", "-n", "1", "-b"});
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            // Common top headers:
            // "User 4%, System 6%, IOW 0%, IRQ 0%"
            // "400%cpu  20%user   0%nice  15%sys 360%idle..."
            while ((line = br.readLine()) != null) {
                line = line.toLowerCase(Locale.US);
                if (line.contains("user") && line.contains("sys") && (line.contains("%") || line.contains("cpu"))) {
                    float u = parseCpuFromTopLine(line);
                    if (u >= 0) {
                        br.close();
                        p.destroy();
                        return u;
                    }
                }
            }
            br.close();
            p.destroy();
        } catch (Exception ignored) {}
        return -1f;
    }

    private float parseCpuFromTopLine(String line) {
        try {
            // Pattern 1: "User 4%, System 6%..."
            if (line.contains("user") && line.contains("%")) {
                float total = 0;
                String[] parts = line.split(",");
                for (String part : parts) {
                    if (part.contains("user") || part.contains("sys") || part.contains("nice") || part.contains("irq")) {
                        String num = part.replaceAll("[^0-9.]", "");
                        if (!num.isEmpty()) total += Float.parseFloat(num);
                    }
                }
                if (total > 0) return total;
            }
            // Pattern 2: "400%cpu  20%user   0%nice  15%sys..."
            // Total = 400 - idle (if we can find idle)
            if (line.contains("idle")) {
                String[] words = line.trim().split("\\s+");
                float idle = -1;
                float total = -1;
                for (String w : words) {
                    if (w.contains("idle")) {
                        String num = w.replaceAll("[^0-9.]", "");
                        if (!num.isEmpty()) idle = Float.parseFloat(num);
                    } else if (w.contains("cpu")) {
                        String num = w.replaceAll("[^0-9.]", "");
                        if (!num.isEmpty()) total = Float.parseFloat(num);
                    }
                }
                if (total > 0 && idle >= 0) {
                    float usage = (total - idle) * 100f / total;
                    return Math.max(0, Math.min(100, usage));
                }
            }
        } catch (Exception ignored) {}
        return -1f;
    }

    public int getCpuCoreCount() {
        try {
            return Runtime.getRuntime().availableProcessors();
        } catch (Exception e) {
            return 1;
        }
    }
}