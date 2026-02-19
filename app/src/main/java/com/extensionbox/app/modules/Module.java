package com.extensionbox.app.modules;

import android.content.Context;
import com.extensionbox.app.SystemAccess;
import java.util.LinkedHashMap;

public interface Module {
    String key();
    String name();
    String emoji();
    String description();
    boolean defaultEnabled();
    void start(Context ctx, SystemAccess sys);
    void stop();
    void tick();
    int tickIntervalMs();
    String compact();
    String detail();
    LinkedHashMap<String, String> dataPoints();
    boolean alive();
    void checkAlerts(Context ctx);

    /**
     * Priority for notification ordering. Lower = higher priority.
     * Battery=10, Screen=20, Sleep=30, Network=40, Data=50, Unlock=60, Steps=70, SpeedTest=80, etc.
     */
    int priority();
}