package com.extensionbox.app;

import android.app.Activity;

/**
 * Maps the saved theme index to the correct style resource.
 * Call ThemeHelper.apply(activity) BEFORE setContentView().
 */
public final class ThemeHelper {

    // Theme indices — must match Settings spinner order
    public static final int MONET = 0;
    public static final int GRUVBOX = 1;
    public static final int CATPPUCCIN = 2;
    public static final int NORD = 3;
    public static final int AMOLED = 4;
    public static final int SOLARIZED = 5;
    public static final int DRACULA = 6;

    public static final String[] NAMES = {
            "Material You (Monet)",
            "Gruvbox Dark",
            "Catppuccin Mocha",
            "Nord",
            "AMOLED",
            "Solarized Dark",
            "Dracula"
    };

    public static void apply(Activity activity) {
        int theme = Prefs.getInt(activity, "app_theme", MONET);
        switch (theme) {
            case GRUVBOX:    activity.setTheme(R.style.AppTheme_Gruvbox);    break;
            case CATPPUCCIN: activity.setTheme(R.style.AppTheme_Catppuccin); break;
            case NORD:       activity.setTheme(R.style.AppTheme_Nord);       break;
            case AMOLED:     activity.setTheme(R.style.AppTheme_Amoled);     break;
            case SOLARIZED:  activity.setTheme(R.style.AppTheme_Solarized);  break;
            case DRACULA:    activity.setTheme(R.style.AppTheme_Dracula);    break;
            default:         activity.setTheme(R.style.AppTheme);            break;
        }
    }
}
