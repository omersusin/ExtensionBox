package com.extensionbox.app;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {

    private static final String FILE = "ebox";

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean isModuleEnabled(Context c, String key, boolean def) {
        return p(c).getBoolean("m_" + key + "_enabled", def);
    }

    public static void setModuleEnabled(Context c, String key, boolean val) {
        p(c).edit().putBoolean("m_" + key + "_enabled", val).apply();
    }

    public static boolean isRunning(Context c) {
        return p(c).getBoolean("running", false);
    }

    public static void setRunning(Context c, boolean val) {
        p(c).edit().putBoolean("running", val).apply();
    }

    public static int getInt(Context c, String key, int def) {
        return p(c).getInt(key, def);
    }

    public static void setInt(Context c, String key, int val) {
        p(c).edit().putInt(key, val).apply();
    }

    public static long getLong(Context c, String key, long def) {
        return p(c).getLong(key, def);
    }

    public static void setLong(Context c, String key, long val) {
        p(c).edit().putLong(key, val).apply();
    }

    public static boolean getBool(Context c, String key, boolean def) {
        return p(c).getBoolean(key, def);
    }

    public static void setBool(Context c, String key, boolean val) {
        p(c).edit().putBoolean(key, val).apply();
    }

    public static String getString(Context c, String key, String def) {
        return p(c).getString(key, def);
    }

    public static void setString(Context c, String key, String val) {
        p(c).edit().putString(key, val).apply();
    }
}
