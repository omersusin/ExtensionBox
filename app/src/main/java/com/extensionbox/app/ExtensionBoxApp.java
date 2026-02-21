package com.extensionbox.app;

import android.app.Application;
import com.google.android.material.color.DynamicColors;

public class ExtensionBoxApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
