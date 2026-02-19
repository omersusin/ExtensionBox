package com.extensionbox.app.modules;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.extensionbox.app.Fmt;
import com.extensionbox.app.Prefs;
import com.extensionbox.app.R;
import com.extensionbox.app.SystemAccess;

import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Locale;

public class StepModule implements Module, SensorEventListener {

    private Context ctx;
    private SensorManager sm;
    private boolean running = false;
    private float lastRaw = -1;
    private long dailySteps;
    private boolean sensorAvailable = true;
    private boolean permissionDenied = false;

    @Override public String key() { return "steps"; }
    @Override public String name() { return "Step Counter"; }
    @Override public String emoji() { return "👣"; }
    @Override public String description() { return "Steps and distance"; }
    @Override public boolean defaultEnabled() { return false; }
    @Override public boolean alive() { return running; }
    @Override public int priority() { return 70; }

    @Override
    public int tickIntervalMs() {
        return ctx != null ? Prefs.getInt(ctx, "stp_interval", 10000) : 10000;
    }

    @Override
    public void start(Context c, SystemAccess sys) {
        ctx = c;
        dailySteps = Prefs.getLong(c, "stp_today", 0);

        // Check ACTIVITY_RECOGNITION permission (required on Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(c, Manifest.permission.ACTIVITY_RECOGNITION)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionDenied = true;
                sensorAvailable = false;
                running = true;
                return;
            }
        }

        permissionDenied = false;
        sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        Sensor s = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (s != null) {
            sm.registerListener(this, s, SensorManager.SENSOR_DELAY_UI);
            sensorAvailable = true;
        } else {
            sensorAvailable = false;
        }
        running = true;
    }

    @Override
    public void stop() {
        if (sm != null) {
            try { sm.unregisterListener(this); } catch (Exception ignored) {}
        }
        sm = null;
        running = false;
    }

    @Override
    public void tick() {
        if (ctx == null) return;

        // Re-check permission in case it was granted after start
        if (permissionDenied && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION)
                    == PackageManager.PERMISSION_GRANTED) {
                permissionDenied = false;
                // Re-register sensor
                sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
                Sensor s = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
                if (s != null) {
                    sm.registerListener(this, s, SensorManager.SENSOR_DELAY_UI);
                    sensorAvailable = true;
                } else {
                    sensorAvailable = false;
                }
            }
        }

        int today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        int lastDay = Prefs.getInt(ctx, "stp_last_day", -1);
        if (lastDay != -1 && lastDay != today) {
            Prefs.setLong(ctx, "stp_yesterday", dailySteps);
            dailySteps = 0;
            lastRaw = -1;
            Prefs.setLong(ctx, "stp_today", 0);
            Prefs.setBool(ctx, "stp_goal_fired", false);
        }
        Prefs.setInt(ctx, "stp_last_day", today);
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        if (e.sensor.getType() != Sensor.TYPE_STEP_COUNTER) return;
        float cur = e.values[0];
        if (lastRaw >= 0) {
            float delta = cur - lastRaw;
            if (delta > 0 && delta < 5000) {
                dailySteps += (long) delta;
                if (ctx != null) Prefs.setLong(ctx, "stp_today", dailySteps);
            }
        }
        lastRaw = cur;
    }

    @Override public void onAccuracyChanged(Sensor s, int a) { }

    @Override
    public String compact() {
        if (permissionDenied) return "👣Perm";
        if (!sensorAvailable) return "👣N/A";
        return "👣" + Fmt.number(dailySteps);
    }

    @Override
    public String detail() {
        if (permissionDenied) return "👣 Permission needed (ACTIVITY_RECOGNITION)";
        if (!sensorAvailable) return "👣 Step sensor not available";

        StringBuilder sb = new StringBuilder();
        int goal = ctx != null ? Prefs.getInt(ctx, "stp_goal", 10000) : 0;
        int strideCm = ctx != null ? Prefs.getInt(ctx, "stp_stride_cm", 75) : 75;
        double km = dailySteps * strideCm / 100000.0;

        if (goal > 0 && Prefs.getBool(ctx, "stp_show_goal", true)) {
            float pct = dailySteps * 100f / goal;
            sb.append(String.format(Locale.US, "👣 Steps: %s / %s (%.0f%%)",
                    Fmt.number(dailySteps), Fmt.number(goal), pct));
        } else {
            sb.append("👣 Steps: ").append(Fmt.number(dailySteps));
        }

        if (Prefs.getBool(ctx, "stp_show_distance", true)) {
            sb.append(String.format(Locale.US, "\n   Distance: %.1f km", km));
        }

        if (Prefs.getBool(ctx, "stp_show_yesterday", true)) {
            long y = Prefs.getLong(ctx, "stp_yesterday", 0);
            if (y > 0) {
                long diff = dailySteps - y;
                String cmp = diff <= 0 ? "↓" + Fmt.number(Math.abs(diff)) : "↑" + Fmt.number(diff);
                sb.append("\n   Yesterday: ").append(Fmt.number(y)).append(" (").append(cmp).append(")");
            }
        }
        return sb.toString();
    }

    @Override
    public LinkedHashMap<String, String> dataPoints() {
        LinkedHashMap<String, String> d = new LinkedHashMap<>();
        if (permissionDenied) {
            d.put("steps.status", "Permission needed");
            return d;
        }
        if (!sensorAvailable) {
            d.put("steps.status", "Sensor not available");
            return d;
        }
        int strideCm = ctx != null ? Prefs.getInt(ctx, "stp_stride_cm", 75) : 75;
        double km = dailySteps * strideCm / 100000.0;
        d.put("steps.today", Fmt.number(dailySteps));
        d.put("steps.distance", String.format(Locale.US, "%.1f km", km));
        int goal = ctx != null ? Prefs.getInt(ctx, "stp_goal", 10000) : 0;
        if (goal > 0) d.put("steps.goal", Fmt.number(goal));
        long y = ctx != null ? Prefs.getLong(ctx, "stp_yesterday", 0) : 0;
        if (y > 0) {
            d.put("steps.yesterday", Fmt.number(y));
            long diff = dailySteps - y;
            d.put("steps.vs_yesterday", diff <= 0 ? "↓" + Fmt.number(Math.abs(diff)) : "↑" + Fmt.number(diff));
        }
        return d;
    }

    @Override
    public void checkAlerts(Context c) {
        if (permissionDenied || !sensorAvailable) return;
        int goal = Prefs.getInt(c, "stp_goal", 10000);
        if (goal <= 0) return;
        boolean fired = Prefs.getBool(c, "stp_goal_fired", false);
        if (dailySteps >= goal && !fired) {
            try {
                NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
                nm.notify(2008, new NotificationCompat.Builder(c, "ebox_alerts")
                        .setSmallIcon(R.drawable.ic_notif)
                        .setContentTitle("🎉 Step Goal Reached!")
                        .setContentText(Fmt.number(dailySteps) + " steps today!")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true).build());
            } catch (Exception ignored) {}
            Prefs.setBool(c, "stp_goal_fired", true);
        }
    }
}
