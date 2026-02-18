package com.extensionbox.app.ui;

public final class ModuleRegistry {

    public static final String[][] MODULES = {
        {"battery",    "🔋", "Battery",          "Current, power, temperature, health",     "true"},
        {"cpu_ram",    "🧠", "CPU & RAM",        "CPU usage, memory status",                "true"},
        {"screen",     "📱", "Screen Time",      "Screen on/off time, drain rates",         "true"},
        {"sleep",      "😴", "Deep Sleep",       "CPU sleep vs awake ratio",                "true"},
        {"network",    "📶", "Network Speed",    "Real-time download/upload speed",         "true"},
        {"data",       "📊", "Data Usage",       "Daily & monthly, WiFi & mobile",          "true"},
        {"unlock",     "🔓", "Unlock Counter",   "Daily unlocks, detox tracking",           "true"},
        {"storage",    "💾", "Storage",           "Internal storage usage",                  "false"},
        {"connection", "📡", "Connection Info",   "WiFi, cellular, VPN status",              "false"},
        {"uptime",     "⏱",  "Uptime",           "Device uptime since boot",                "false"},
        {"steps",      "👣", "Step Counter",      "Steps and distance",                      "false"},
        {"speedtest",  "🏎", "Speed Test",       "Periodic download speed test",            "false"},
    };

    public static String keyAt(int i)     { return MODULES[i][0]; }
    public static String emojiAt(int i)   { return MODULES[i][1]; }
    public static String nameAt(int i)    { return MODULES[i][2]; }
    public static String descAt(int i)    { return MODULES[i][3]; }
    public static boolean defAt(int i)    { return "true".equals(MODULES[i][4]); }
    public static int count()             { return MODULES.length; }

    public static String emojiFor(String key) {
        for (String[] m : MODULES) if (m[0].equals(key)) return m[1];
        return "?";
    }

    public static String nameFor(String key) {
        for (String[] m : MODULES) if (m[0].equals(key)) return m[2];
        return key;
    }
}
