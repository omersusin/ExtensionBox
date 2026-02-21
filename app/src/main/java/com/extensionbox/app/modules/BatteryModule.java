package com.extensionbox.app.modules;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import androidx.core.app.NotificationCompat;

import com.extensionbox.app.Fmt;
import com.extensionbox.app.Prefs;
import com.extensionbox.app.R;
import com.extensionbox.app.SystemAccess;

import java.util.LinkedHashMap;
import java.util.Locale;

public class BatteryModule implements Module {

    private Context ctx;
    private SystemAccess sys;
    private BroadcastReceiver rcv;
    private boolean running = false;

    private int level, temp, voltage, health, status, plugged;
    private int designCap = 4000;
    private int currentMa = 0;

    @Override public String key() { return "battery"; }
    @Override public String name() { return "Battery"; }
    @Override public String emoji() { return "🔋"; }
    @Override public String description() { return "Current, power, temperature, health"; }
    @Override public boolean defaultEnabled() { return true; }
    @Override public boolean alive() { return running; }

    @Override
    public int tickIntervalMs() {
        return ctx != null ? Prefs.getInt(ctx, "bat_interval", 10000) : 10000;
    }

    @Override
    public void start(Context c, SystemAccess s) {
        ctx = c;
        sys = s;
        designCap = sys.readDesignCapacity(c);

        rcv = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent i) {
                level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                voltage = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
                health = i.getIntExtra(BatteryManager.EXTRA_HEALTH, 0);
                status = i.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
                plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            }
        };

        Intent sticky = c.registerReceiver(rcv,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (sticky != null) {
            level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            temp = sticky.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
            voltage = sticky.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
            health = sticky.getIntExtra(BatteryManager.EXTRA_HEALTH, 0);
            status = sticky.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
            plugged = sticky.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        }

        running = true;
    }

    @Override
    public void stop() {
        if (rcv != null && ctx != null) {
            try { ctx.unregisterReceiver(rcv); } catch (Exception ignored) {}
        }
        rcv = null;
        running = false;
    }

    @Override
    public void tick() {
        if (sys != null && ctx != null) {
            currentMa = sys.readBatteryCurrentMa(ctx);
        }
    }

    @Override
    public String compact() {
        return level + "% " + timeLeft();
    }

    @Override
    public String detail() {
        int ma = Math.abs(currentMa);
        float w = ma * voltage / 1_000_000f;
        float t = temp / 10f;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US,
                "🔋 %d%% • %dmA (%.1fW) • %s\n", level, ma, w, Fmt.temp(t)));
        sb.append(String.format(Locale.US,
                "   Health: %s • %.2fV • %s\n", healthStr(), voltage / 1000f, statusStr()));
        sb.append("   ").append(timeLeft());

        if (isCharging()) {
            sb.append(" • ").append(chargeType());
        }

        return sb.toString();
    }

    @Override
    public LinkedHashMap<String, String> dataPoints() {
        LinkedHashMap<String, String> d = new LinkedHashMap<>();
        int ma = Math.abs(currentMa);
        float w = ma * voltage / 1_000_000f;
        float t = temp / 10f;

        d.put("battery.level", level + "%");
        d.put("battery.current", ma + " mA");
        d.put("battery.power", String.format(Locale.US, "%.1f W", w));
        d.put("battery.temp", Fmt.temp(t));
        d.put("battery.voltage", String.format(Locale.US, "%.2fV", voltage / 1000f));
        d.put("battery.health", healthStr());
        d.put("battery.status", statusStr());
        d.put("battery.time_left", timeLeft());

        if (isCharging()) {
            d.put("battery.charge_type", chargeType());
        }

        d.put("battery.design_cap", designCap + " mAh");
        return d;
    }

    @Override
    public void checkAlerts(Context c) {
        boolean lowEnabled = Prefs.getBool(c, "bat_low_alert", true);
        int lowThresh = Prefs.getInt(c, "bat_low_thresh", 15);
        boolean lowFired = Prefs.getBool(c, "bat_low_fired", false);

        if (lowEnabled && level <= lowThresh && !lowFired && !isCharging()) {
            fireAlert(c, 2001,
                    "🔴 Battery Low",
                    "Battery at " + level + "%. Charge your phone!");
            Prefs.setBool(c, "bat_low_fired", true);
        }
        if (lowFired && level > lowThresh + 5) {
            Prefs.setBool(c, "bat_low_fired", false);
        }

        boolean tempEnabled = Prefs.getBool(c, "bat_temp_alert", true);
        int tempThresh = Prefs.getInt(c, "bat_temp_thresh", 42);
        boolean tempFired = Prefs.getBool(c, "bat_temp_fired", false);
        float currentTemp = temp / 10f;

        if (tempEnabled && currentTemp >= tempThresh && !tempFired) {
            fireAlert(c, 2002,
                    "🔴 High Temperature",
                    "Battery at " + Fmt.temp(currentTemp) + ". Let your phone cool down!");
            Prefs.setBool(c, "bat_temp_fired", true);
        }
        if (tempFired && currentTemp < tempThresh - 3) {
            Prefs.setBool(c, "bat_temp_fired", false);
        }
    }

    public int getLevel() { return level; }

    private boolean isCharging() {
        return status == BatteryManager.BATTERY_STATUS_CHARGING;
    }

    private String timeLeft() {
        int ma = Math.abs(currentMa);
        if (ma < 5) return "—";

        if (isCharging()) {
            float neededMah = (100 - level) / 100f * designCap;
            float hrs = neededMah / ma;
            return "⚡Full in " + formatHours(hrs);
        } else {
            float remMah = level / 100f * designCap;
            float hrs = remMah / ma;
            return formatHours(hrs) + " left";
        }
    }

    private String formatHours(float hrs) {
        if (hrs < 0) hrs = 0;
        int d = (int) (hrs / 24);
        int h = (int) (hrs % 24);
        int m = (int) ((hrs * 60) % 60);
        if (d > 0) return String.format(Locale.US, "%dd %dh", d, h);
        if (h > 0) return String.format(Locale.US, "%dh %dm", h, m);
        return String.format(Locale.US, "%dm", m);
    }

    private String chargeType() {
        int ma = Math.abs(currentMa);
        if (ma > 3000) return "Rapid";
        if (ma > 1500) return "Fast";
        if (ma > 500) return "Normal";
        return "Slow";
    }

    private String healthStr() {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "Good";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "Overheat!";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "Dead";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "OverVolt";
            case BatteryManager.BATTERY_HEALTH_COLD: return "Cold";
            default: return "Unknown";
        }
    }

    private String statusStr() {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "Charging";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "Discharging";
            case BatteryManager.BATTERY_STATUS_FULL: return "Full";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "Not charging";
            default: return "Unknown";
        }
    }

    private void fireAlert(Context c, int id, String title, String body) {
        try {
            NotificationManager nm = (NotificationManager)
                    c.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationCompat.Builder b = new NotificationCompat.Builder(c, "ebox_alerts")
                    .setSmallIcon(R.drawable.ic_notif)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true);
            nm.notify(id, b.build());
        } catch (Exception ignored) {}
    }
}
