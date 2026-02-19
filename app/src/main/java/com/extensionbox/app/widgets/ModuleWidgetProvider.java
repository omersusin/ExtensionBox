package com.extensionbox.app.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.RemoteViews;

import com.extensionbox.app.MainActivity;
import com.extensionbox.app.MonitorService;
import com.extensionbox.app.R;
import com.extensionbox.app.ui.ModuleRegistry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single reusable AppWidgetProvider used for all module widgets.
 * Each module has its own subclass alias registered in the manifest pointing
 * to its own widget info XML, but they all share this implementation.
 *
 * The module key is determined from the widget provider's class name via
 * the alias naming convention (e.g., BatteryWidgetProvider → "battery").
 */
public class ModuleWidgetProvider extends AppWidgetProvider {

    // Subclass aliases — each maps to a module key
    public static class BatteryWidgetProvider extends ModuleWidgetProvider {}
    public static class CpuRamWidgetProvider extends ModuleWidgetProvider {}
    public static class ScreenWidgetProvider extends ModuleWidgetProvider {}
    public static class SleepWidgetProvider extends ModuleWidgetProvider {}
    public static class NetworkWidgetProvider extends ModuleWidgetProvider {}
    public static class DataWidgetProvider extends ModuleWidgetProvider {}
    public static class UnlockWidgetProvider extends ModuleWidgetProvider {}
    public static class StorageWidgetProvider extends ModuleWidgetProvider {}
    public static class ConnectionWidgetProvider extends ModuleWidgetProvider {}
    public static class UptimeWidgetProvider extends ModuleWidgetProvider {}
    public static class StepWidgetProvider extends ModuleWidgetProvider {}
    public static class SpeedTestWidgetProvider extends ModuleWidgetProvider {}
    public static class FapWidgetProvider extends ModuleWidgetProvider {}

    /** Map alias class name suffix → module key */
    private static String resolveKey(Class<?> cls) {
        String name = cls.getSimpleName();
        if (name.equals("BatteryWidgetProvider")) return "battery";
        if (name.equals("CpuRamWidgetProvider")) return "cpu_ram";
        if (name.equals("ScreenWidgetProvider")) return "screen";
        if (name.equals("SleepWidgetProvider")) return "sleep";
        if (name.equals("NetworkWidgetProvider")) return "network";
        if (name.equals("DataWidgetProvider")) return "data";
        if (name.equals("UnlockWidgetProvider")) return "unlock";
        if (name.equals("StorageWidgetProvider")) return "storage";
        if (name.equals("ConnectionWidgetProvider")) return "connection";
        if (name.equals("UptimeWidgetProvider")) return "uptime";
        if (name.equals("StepWidgetProvider")) return "steps";
        if (name.equals("SpeedTestWidgetProvider")) return "speedtest";
        if (name.equals("FapWidgetProvider")) return "fap";
        return "battery"; // fallback
    }

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] widgetIds) {
        String key = resolveKey(getClass());
        for (int id : widgetIds) {
            updateWidget(ctx, mgr, id, key);
        }
    }

    private static void updateWidget(Context ctx, AppWidgetManager mgr, int widgetId, String key) {
        RemoteViews views = new RemoteViews(ctx.getPackageName(), R.layout.widget_module);

        // Header
        String emoji = ModuleRegistry.emojiFor(key);
        String name = ModuleRegistry.nameFor(key);
        views.setTextViewText(R.id.widgetTitle, emoji + "  " + name);

        // Data
        LinkedHashMap<String, String> data = MonitorService.getModuleData(key);
        StringBuilder body = new StringBuilder();
        if (data != null && !data.isEmpty()) {
            int shown = 0;
            for (Map.Entry<String, String> e : data.entrySet()) {
                if (shown >= 6) break;
                shown++;
                String rawKey = e.getKey();
                int dot = rawKey.lastIndexOf('.');
                String label = dot >= 0 ? rawKey.substring(dot + 1) : rawKey;
                if (label.length() > 0) {
                    label = label.substring(0, 1).toUpperCase()
                            + label.substring(1).replace("_", " ");
                }
                if (body.length() > 0) body.append("\n");
                body.append(label).append(": ").append(e.getValue());
            }
        } else {
            body.append("No data — start monitoring");
        }
        views.setTextViewText(R.id.widgetBody, body.toString());

        // Click → open app
        Intent openIntent = new Intent(ctx, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, widgetId, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetRoot, pi);

        mgr.updateAppWidget(widgetId, views);
    }

    /**
     * Called from MonitorService on every tick to refresh all module widgets.
     */
    public static void updateAllWidgets(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        if (mgr == null) return;

        Class<?>[] providers = {
                BatteryWidgetProvider.class,
                CpuRamWidgetProvider.class,
                ScreenWidgetProvider.class,
                SleepWidgetProvider.class,
                NetworkWidgetProvider.class,
                DataWidgetProvider.class,
                UnlockWidgetProvider.class,
                StorageWidgetProvider.class,
                ConnectionWidgetProvider.class,
                UptimeWidgetProvider.class,
                StepWidgetProvider.class,
                SpeedTestWidgetProvider.class,
                FapWidgetProvider.class,
        };

        for (Class<?> providerCls : providers) {
            String key = resolveKey(providerCls);
            ComponentName comp = new ComponentName(ctx, providerCls);
            int[] ids = mgr.getAppWidgetIds(comp);
            if (ids != null) {
                for (int id : ids) {
                    updateWidget(ctx, mgr, id, key);
                }
            }
        }
    }
}
