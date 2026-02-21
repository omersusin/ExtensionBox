package com.extensionbox.app;

import android.content.Context;
import android.os.BatteryManager;

public class SystemAccess {

    private final String tier;

    public SystemAccess(Context ctx) {

        tier = "Normal";
    }

    public String getTierName() {
        return tier;
    }

    public boolean isEnhanced() {
        return false;
    }

    public int readBatteryCurrentMa(Context ctx) {
        try {
            BatteryManager bm = (BatteryManager)
                    ctx.getSystemService(Context.BATTERY_SERVICE);
            if (bm == null) return 0;
            int ua = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            return ua / 1000;
        } catch (Exception e) {
            return 0;
        }
    }

    public int readDesignCapacity(Context ctx) {
        try {
            Object pp = Class.forName("com.android.internal.os.PowerProfile")
                    .getConstructor(Context.class).newInstance(ctx);
            double cap = (double) Class.forName("com.android.internal.os.PowerProfile")
                    .getMethod("getBatteryCapacity").invoke(pp);
            return cap > 0 ? (int) cap : 4000;
        } catch (Exception e) {
            return 4000;
        }
    }
}
