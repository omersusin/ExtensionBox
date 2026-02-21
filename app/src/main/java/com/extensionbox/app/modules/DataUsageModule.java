package com.extensionbox.app.modules;

import android.app.NotificationManager;
import android.content.Context;
import android.net.TrafficStats;

import androidx.core.app.NotificationCompat;

import com.extensionbox.app.Fmt;
import com.extensionbox.app.Prefs;
import com.extensionbox.app.R;
import com.extensionbox.app.SystemAccess;

import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Locale;

public class DataUsageModule implements Module {

    private Context ctx;
    private boolean running = false;
    private long prevTotal, prevMobile;
    private long dailyTotal, dailyWifi, dailyMobile;
    private long monthTotal, monthWifi, monthMobile;

    @Override public String key() { return "data"; }
    @Override public String name() { return "Data Usage"; }
    @Override public String emoji() { return "📊"; }
    @Override public String description() { return "Daily & monthly, WiFi & mobile"; }
    @Override public boolean defaultEnabled() { return true; }
    @Override public boolean alive() { return running; }

    @Override
    public int tickIntervalMs() {
        return ctx != null ? Prefs.getInt(ctx, "dat_interval", 60000) : 60000;
    }

    @Override
    public void start(Context c, SystemAccess sys) {
        ctx = c;
        long rx = TrafficStats.getTotalRxBytes();
        long tx = TrafficStats.getTotalTxBytes();
        prevTotal = (rx != TrafficStats.UNSUPPORTED) ? rx + tx : 0;
        long mrx = TrafficStats.getMobileRxBytes();
        long mtx = TrafficStats.getMobileTxBytes();
        prevMobile = (mrx != TrafficStats.UNSUPPORTED) ? mrx + mtx : 0;

        dailyTotal = Prefs.getLong(c, "dat_daily_total", 0);
        dailyWifi = Prefs.getLong(c, "dat_daily_wifi", 0);
        dailyMobile = Prefs.getLong(c, "dat_daily_mobile", 0);
        monthTotal = Prefs.getLong(c, "dat_month_total", 0);
        monthWifi = Prefs.getLong(c, "dat_month_wifi", 0);
        monthMobile = Prefs.getLong(c, "dat_month_mobile", 0);
        running = true;
    }

    @Override
    public void stop() { running = false; }

    @Override
    public void tick() {
        if (ctx == null) return;
        rollover();

        long rx = TrafficStats.getTotalRxBytes();
        long tx = TrafficStats.getTotalTxBytes();
        if (rx == TrafficStats.UNSUPPORTED) return;
        long total = rx + tx;

        long mrx = TrafficStats.getMobileRxBytes();
        long mtx = TrafficStats.getMobileTxBytes();
        long mobile = (mrx != TrafficStats.UNSUPPORTED) ? mrx + mtx : 0;

        if (prevTotal > 0 && total >= prevTotal) {
            long dt = total - prevTotal;
            long dm = mobile - prevMobile;
            if (dm < 0) dm = 0;
            long dw = dt - dm;
            if (dw < 0) dw = 0;

            dailyTotal += dt; dailyMobile += dm; dailyWifi += dw;
            monthTotal += dt; monthMobile += dm; monthWifi += dw;

            Prefs.setLong(ctx, "dat_daily_total", dailyTotal);
            Prefs.setLong(ctx, "dat_daily_wifi", dailyWifi);
            Prefs.setLong(ctx, "dat_daily_mobile", dailyMobile);
            Prefs.setLong(ctx, "dat_month_total", monthTotal);
            Prefs.setLong(ctx, "dat_month_wifi", monthWifi);
            Prefs.setLong(ctx, "dat_month_mobile", monthMobile);
        } else if (total < prevTotal) {

        }
        prevTotal = total;
        prevMobile = mobile;
    }

    private void rollover() {
        Calendar c = Calendar.getInstance();
        int day = c.get(Calendar.DAY_OF_YEAR);
        int lastDay = Prefs.getInt(ctx, "dat_last_day", -1);
        if (lastDay != -1 && lastDay != day) {
            dailyTotal = 0; dailyWifi = 0; dailyMobile = 0;
            Prefs.setLong(ctx, "dat_daily_total", 0);
            Prefs.setLong(ctx, "dat_daily_wifi", 0);
            Prefs.setLong(ctx, "dat_daily_mobile", 0);
        }
        Prefs.setInt(ctx, "dat_last_day", day);

        int billingDay = Prefs.getInt(ctx, "dat_billing_day", 1);
        int dom = c.get(Calendar.DAY_OF_MONTH);
        int lastBillCheck = Prefs.getInt(ctx, "dat_last_bill", -1);
        if (dom == billingDay && lastBillCheck != day) {
            monthTotal = 0; monthWifi = 0; monthMobile = 0;
            Prefs.setLong(ctx, "dat_month_total", 0);
            Prefs.setLong(ctx, "dat_month_wifi", 0);
            Prefs.setLong(ctx, "dat_month_mobile", 0);
            Prefs.setBool(ctx, "dat_plan_alert_fired", false);
        }
        Prefs.setInt(ctx, "dat_last_bill", day);
    }

    @Override
    public String compact() {
        return "Today:" + Fmt.bytes(dailyTotal);
    }

    @Override
    public String detail() {
        StringBuilder sb = new StringBuilder();
        boolean breakdown = ctx != null && Prefs.getBool(ctx, "dat_show_breakdown", true);

        if (breakdown) {
            sb.append(String.format(Locale.US, "📊 Today: %s (W:%s M:%s)",
                    Fmt.bytes(dailyTotal), Fmt.bytes(dailyWifi), Fmt.bytes(dailyMobile)));
        } else {
            sb.append("📊 Today: ").append(Fmt.bytes(dailyTotal));
        }

        sb.append("\n   Month: ").append(Fmt.bytes(monthTotal));

        int planMb = ctx != null ? Prefs.getInt(ctx, "dat_plan_limit", 0) : 0;
        if (planMb > 0) {
            long planBytes = planMb * 1024L * 1024L;
            float pct = monthTotal * 100f / planBytes;
            sb.append(String.format(Locale.US, " / %s (%.1f%%)", Fmt.bytes(planBytes), pct));
        }
        return sb.toString();
    }

    @Override
    public LinkedHashMap<String, String> dataPoints() {
        LinkedHashMap<String, String> d = new LinkedHashMap<>();
        d.put("data.today_total", Fmt.bytes(dailyTotal));
        d.put("data.today_wifi", Fmt.bytes(dailyWifi));
        d.put("data.today_mobile", Fmt.bytes(dailyMobile));
        d.put("data.month_total", Fmt.bytes(monthTotal));
        return d;
    }

    @Override
    public void checkAlerts(Context c) {
        int planMb = Prefs.getInt(c, "dat_plan_limit", 0);
        if (planMb <= 0) return;
        int alertPct = Prefs.getInt(c, "dat_plan_alert_pct", 90);
        boolean fired = Prefs.getBool(c, "dat_plan_alert_fired", false);
        long planBytes = planMb * 1024L * 1024L;
        float pct = monthTotal * 100f / planBytes;

        if (pct >= alertPct && !fired) {
            try {
                NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
                nm.notify(2005, new NotificationCompat.Builder(c, "ebox_alerts")
                        .setSmallIcon(R.drawable.ic_notif)
                        .setContentTitle("⚠️ Data Plan Warning")
                        .setContentText(String.format(Locale.US, "Used %.0f%% of %s plan", pct, Fmt.bytes(planBytes)))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true).build());
            } catch (Exception ignored) {}
            Prefs.setBool(c, "dat_plan_alert_fired", true);
        }
    }
}
