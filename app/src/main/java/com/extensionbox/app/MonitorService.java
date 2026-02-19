package com.extensionbox.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;

import com.extensionbox.app.modules.*;
import com.extensionbox.app.widgets.ModuleWidgetProvider;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MonitorService extends Service {

    public static final String ACTION_STOP = "com.extensionbox.STOP";
    private static final String MONITOR_CH = "ebox_monitor";
    private static final String ALERT_CH = "ebox_alerts";
    private static final int NOTIF_ID = 1001;
    private static final int NIGHT_SUMMARY_ID = 2099;

    private static MonitorService instance;
    private SystemAccess sysAccess;
    private List<Module> modules;
    private Map<String, Long> lastTickTime;
    private Handler handler;
    private Runnable tickRunnable;
    private boolean nightSummarySent = false;

    private static final Map<String, LinkedHashMap<String, String>> moduleData = new HashMap<>();

    public static MonitorService getInstance() {
        return instance;
    }

    public static LinkedHashMap<String, String> getModuleData(String key) {
        return moduleData.get(key);
    }

    /**
     * Get the FapCounterModule instance from the running service.
     */ 
    public FapCounterModule getFapModule() {
        if (modules == null) return null;
        for (Module m : modules) {
            if (m instanceof FapCounterModule) return (FapCounterModule) m;
        }
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createChannels();

        sysAccess = new SystemAccess(this);
        modules = new ArrayList<>();
        modules.add(new BatteryModule());
        modules.add(new CpuRamModule());
        modules.add(new ScreenModule());
        modules.add(new SleepModule());
        modules.add(new NetworkModule());
        modules.add(new DataUsageModule());
        modules.add(new UnlockModule());
        modules.add(new StorageModule());
        modules.add(new ConnectionModule());
        modules.add(new UptimeModule());
        modules.add(new StepModule());
        modules.add(new SpeedTestModule());
        modules.add(new FapCounterModule());
        lastTickTime = new HashMap<>();

        startForeground(NOTIF_ID, buildNotification());
        syncModules();

        handler = new Handler(Looper.getMainLooper());
        tickRunnable = () -> {
            doTickCycle();
            scheduleNextTick();
        };
        scheduleNextTick();
        Prefs.setRunning(this, true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopAll();
            Prefs.setRunning(this, false);
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopAll();
        if (handler != null) handler.removeCallbacksAndMessages(null);
        Prefs.setRunning(this, false);
        moduleData.clear();
        instance = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }

    private void syncModules() {
        if (modules == null) return;
        for (Module m : modules) {
            boolean shouldRun = Prefs.isModuleEnabled(this, m.key(), m.defaultEnabled());
            if (shouldRun && !m.alive()) {
                m.start(this, sysAccess);
                lastTickTime.put(m.key(), 0L);
            } else if (!shouldRun && m.alive()) {
                m.stop();
                moduleData.remove(m.key());
                lastTickTime.remove(m.key());
            }
        }
    }

    private void doTickCycle() {
        // Check for day/month rollover before ticking modules
        checkRollover();

        syncModules();
        long now = SystemClock.elapsedRealtime();
        boolean changed = false;

        for (Module m : modules) {
            if (!m.alive()) continue;
            Long last = lastTickTime.get(m.key());
            if (last == null) last = 0L;
            if (now - last >= m.tickIntervalMs()) {
                m.tick();
                m.checkAlerts(this);
                lastTickTime.put(m.key(), now);
                moduleData.put(m.key(), m.dataPoints());
                changed = true;
            }
        }
        if (changed) {
            updateNotification();
            // Update all home screen widgets
            try {
                ModuleWidgetProvider.updateAllWidgets(this);
            } catch (Exception ignored) {}
        }

        // Night Summary (23:00)
        checkNightSummary();
    }

    // ── Day/Month rollover ──────────────────────────────────────

    private void checkRollover() {
        Calendar cal = Calendar.getInstance();
        int today = cal.get(Calendar.DAY_OF_YEAR);
        int thisMonth = cal.get(Calendar.MONTH);
        int thisYear = cal.get(Calendar.YEAR);

        int lastDay = Prefs.getInt(this, "rollover_day", -1);
        int lastMonth = Prefs.getInt(this, "rollover_month", -1);
        int lastYear = Prefs.getInt(this, "rollover_year", -1);

        // First run: just store current values
        if (lastDay == -1) {
            Prefs.setInt(this, "rollover_day", today);
            Prefs.setInt(this, "rollover_month", thisMonth);
            Prefs.setInt(this, "rollover_year", thisYear);
            return;
        }

        // Day changed
        if (today != lastDay || thisYear != lastYear) {
            doDayRollover();
            Prefs.setInt(this, "rollover_day", today);
            Prefs.setInt(this, "rollover_year", thisYear);
        }

        // Month changed
        if (thisMonth != lastMonth || thisYear != lastYear) {
            doMonthRollover();
            Prefs.setInt(this, "rollover_month", thisMonth);
        }
    }

    private void doDayRollover() {
        // Save today's values as yesterday before resetting
        Prefs.setInt(this, "ulk_yesterday", Prefs.getInt(this, "ulk_today", 0));
        Prefs.setLong(this, "stp_yesterday", Prefs.getLong(this, "stp_today", 0));
        Prefs.setLong(this, "scr_yesterday_on", Prefs.getLong(this, "scr_on_acc", 0));
        Prefs.setInt(this, "fap_yesterday", Prefs.getInt(this, "fap_today", 0));

        // Reset daily counters
        Prefs.setInt(this, "ulk_today", 0);
        Prefs.setLong(this, "stp_today", 0);
        Prefs.setLong(this, "dat_daily_total", 0);
        Prefs.setLong(this, "dat_daily_wifi", 0);
        Prefs.setLong(this, "dat_daily_mobile", 0);
        Prefs.setLong(this, "scr_on_acc", 0);
        Prefs.setInt(this, "fap_today", 0);
    }

    private void doMonthRollover() {
        Prefs.setLong(this, "dat_monthly_total", 0);
        Prefs.setLong(this, "dat_monthly_wifi", 0);
        Prefs.setLong(this, "dat_monthly_mobile", 0);
    }

    // ── Scheduling ──────────────────────────────────────────────

    private void scheduleNextTick() {
        if (modules == null) { handler.postDelayed(tickRunnable, 5000); return; }
        long now = SystemClock.elapsedRealtime();
        long minDelay = Long.MAX_VALUE;
        for (Module m : modules) {
            if (!m.alive()) continue;
            Long last = lastTickTime.get(m.key());
            if (last == null) last = 0L;
            long delay = (last + m.tickIntervalMs()) - now;
            if (delay < minDelay) minDelay = delay;
        }
        if (minDelay < 1000) minDelay = 1000;
        if (minDelay > 60000) minDelay = 60000;
        if (minDelay == Long.MAX_VALUE) minDelay = 5000;
        handler.postDelayed(tickRunnable, minDelay);
    }

    private void stopAll() {
        if (modules == null) return;
        for (Module m : modules) { if (m.alive()) m.stop(); }
        moduleData.clear();
    }

    // ── Notification ────────────────────────────────────────────

    private void createChannels() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel monCh = new NotificationChannel(MONITOR_CH, "Extension Box Monitor", NotificationManager.IMPORTANCE_LOW);
        monCh.setShowBadge(false); monCh.enableVibration(false); monCh.setSound(null, null);
        nm.createNotificationChannel(monCh);
        NotificationChannel alertCh = new NotificationChannel(ALERT_CH, "Extension Box Alerts", NotificationManager.IMPORTANCE_HIGH);
        nm.createNotificationChannel(alertCh);
    }

    private Notification buildNotification() {
        Intent openI = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, openI, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stopI = new Intent(this, MonitorService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stopI, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, MONITOR_CH)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(buildTitle())
                .setContentText(buildCompact())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(buildExpanded()))
                .setOngoing(true).setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(openPi)
                .addAction(0, "■ Stop", stopPi)
                .setShowWhen(false)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();
    }

    private String buildTitle() {
        if (modules == null) return "Extension Box";

        boolean contextAware = Prefs.getBool(this, "notif_context_aware", true);
        BatteryModule batMod = null;
        for (Module m : modules) {
            if ("battery".equals(m.key()) && m.alive() && m instanceof BatteryModule) {
                batMod = (BatteryModule) m;
                break;
            }
        }

        if (batMod == null) return "Extension Box";

        int lvl = batMod.getLevel();
        if (contextAware) {
            if (lvl <= 15) return "⚠ Extension Box • " + lvl + "% Low!";
            return "Extension Box • " + lvl + "%";
        }
        return "Extension Box • " + lvl + "%";
    }

    private String buildCompact() {
        if (modules == null) return "Starting...";

        boolean contextAware = Prefs.getBool(this, "notif_context_aware", true);
        int maxItems = Prefs.getInt(this, "notif_compact_items", 4);

        // Collect alive modules sorted by priority
        List<Module> alive = getAliveModulesSorted();

        List<String> parts = new ArrayList<>();
        for (Module m : alive) {
            String c = m.compact();
            if (c != null && !c.isEmpty()) parts.add(c);
            if (parts.size() >= maxItems) break;
        }

        if (parts.isEmpty()) return "All extensions disabled";

        String base = TextUtils.join(" • ", parts);

        // Truncate if too long
        if (base.length() > 50 && parts.size() > 4) {
            parts = parts.subList(0, 4);
            base = TextUtils.join(" • ", parts);
        }

        // Context-aware suffix
        if (contextAware) {
            BatteryModule batMod = null;
            for (Module m : modules) {
                if ("battery".equals(m.key()) && m.alive() && m instanceof BatteryModule) {
                    batMod = (BatteryModule) m;
                    break;
                }
            }
            if (batMod != null && batMod.getLevel() <= 10) {
                base += " • ⚡Charge now!";
            }
        }
        return base;
    }

    private String buildExpanded() {
        if (modules == null) return "Starting...";

        // Sorted by priority
        List<Module> alive = getAliveModulesSorted();

        List<String> lines = new ArrayList<>();
        for (Module m : alive) {
            String d = m.detail();
            if (d != null && !d.isEmpty()) lines.add(d);
        }
        return lines.isEmpty() ? "Enable extensions from the app" : TextUtils.join("\n", lines);
    }

    /** Returns alive modules sorted by priority (lower = higher priority). */
    private List<Module> getAliveModulesSorted() {
        List<Module> alive = new ArrayList<>();
        for (Module m : modules) {
            if (m.alive()) alive.add(m);
        }
        Collections.sort(alive, Comparator.comparingInt(Module::priority));
        return alive;
    }

    // ── Night Summary ───────────────────────────────────────────

    private void checkNightSummary() {
        if (!Prefs.getBool(this, "notif_night_summary", true)) return;

        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);

        if (hour >= 23 && !nightSummarySent) {
            nightSummarySent = true;
            sendNightSummary();
        } else if (hour < 23) {
            nightSummarySent = false;
        }
    }

    private void sendNightSummary() {
        int unlocks = Prefs.getInt(this, "ulk_today", 0);
        long screenMs = Prefs.getLong(this, "scr_on_acc", 0);
        long steps = Prefs.getLong(this, "stp_today", 0);
        int faps = Prefs.getInt(this, "fap_today", 0);

        int screenMin = (int) (screenMs / 60000);
        int screenH = screenMin / 60;
        int screenM = screenMin % 60;

        StringBuilder body = new StringBuilder();
        body.append("📱 Screen: ").append(screenH).append("h ").append(screenM).append("m");
        body.append("  •  🔓 ").append(unlocks).append(" unlocks");
        if (steps > 0) body.append("  •  👣 ").append(steps).append(" steps");
        if (faps > 0) body.append("  •  🍆 ").append(faps);

        // Compare with yesterday
        int ydUnlocks = Prefs.getInt(this, "ulk_yesterday", 0);
        if (ydUnlocks > 0) {
            int diff = unlocks - ydUnlocks;
            int pct = Math.abs(diff * 100 / ydUnlocks);
            if (diff < 0) body.append("\n🎉 ").append(pct).append("% fewer unlocks than yesterday!");
            else if (diff > 0) body.append("\n📈 ").append(pct).append("% more unlocks than yesterday");
        }

        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            Notification n = new NotificationCompat.Builder(this, ALERT_CH)
                    .setSmallIcon(R.drawable.ic_notif)
                    .setContentTitle("🌙 Daily Summary")
                    .setContentText(body.toString())
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(body.toString()))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .build();
            nm.notify(NIGHT_SUMMARY_ID, n);
        } catch (Exception ignored) {}
    }

    private void updateNotification() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.notify(NOTIF_ID, buildNotification());
        } catch (Exception ignored) {}
    }
}
