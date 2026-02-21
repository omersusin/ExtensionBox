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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MonitorService extends Service {

    public static final String ACTION_STOP = "com.extensionbox.STOP";
    private static final String MONITOR_CH = "ebox_monitor";
    private static final String ALERT_CH = "ebox_alerts";
    private static final int NOTIF_ID = 1001;

    private SystemAccess sysAccess;
    private List<Module> modules;
    private Map<String, Long> lastTickTime;
    private Handler handler;
    private Runnable tickRunnable;

    private static final Map<String, LinkedHashMap<String, String>> moduleData = new HashMap<>();

    public static LinkedHashMap<String, String> getModuleData(String key) {
        return moduleData.get(key);
    }

    @Override
    public void onCreate() {
        super.onCreate();
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
        modules.add(new AppUsageModule());
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

                logToHistory(m.key(), m.dataPoints());
                changed = true;
            }
        }
        if (changed) updateNotification();
    }

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
        for (Module m : modules) {
            if ("battery".equals(m.key()) && m.alive() && m instanceof BatteryModule)
                return "Extension Box • " + ((BatteryModule) m).getLevel() + "%";
        }
        return "Extension Box";
    }

    private String buildCompact() {
        if (modules == null) return "Starting...";
        List<String> parts = new ArrayList<>();
        for (Module m : modules) {
            if (!m.alive()) continue;
            String c = m.compact();
            if (c != null && !c.isEmpty()) parts.add(c);
            if (parts.size() >= 4) break;
        }
        return parts.isEmpty() ? "All extensions disabled" : TextUtils.join(" • ", parts);
    }

    private String buildExpanded() {
        if (modules == null) return "Starting...";
        List<String> lines = new ArrayList<>();
        for (Module m : modules) {
            if (!m.alive()) continue;
            String d = m.detail();
            if (d != null && !d.isEmpty()) lines.add(d);
        }
        return lines.isEmpty() ? "Enable extensions from the app" : TextUtils.join("\n", lines);
    }

    private void updateNotification() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.notify(NOTIF_ID, buildNotification());
        } catch (Exception ignored) {}
    }

    private void logToHistory(String moduleKey, LinkedHashMap<String, String> data) {
        if (data == null) return;
        Set<String> interestingKeys = new HashSet<>(Arrays.asList(
                "bat.level", "bat.temp", "cpu.usage", "ram.used_pct",
                "net.download", "net.upload", "data.daily_mb", "scr.on_min"));
        for (Map.Entry<String, String> e : data.entrySet()) {
            if (interestingKeys.contains(e.getKey())) {
                try {
                    HistoryHelper.get(this).insert(moduleKey, e.getKey(), e.getValue());
                } catch (Exception ignored) {}
            }
        }
    }
}
