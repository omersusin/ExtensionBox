package com.extensionbox.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.content.ContextCompat;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            if (Prefs.isRunning(ctx)) {
                ContextCompat.startForegroundService(ctx,
                        new Intent(ctx, MonitorService.class));
            }
        }
    }
}
