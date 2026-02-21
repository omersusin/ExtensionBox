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
}
