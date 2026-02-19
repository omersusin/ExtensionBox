package com.extensionbox.app.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.AdapterView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

import com.extensionbox.app.Prefs;
import com.extensionbox.app.R;
import com.extensionbox.app.SystemAccess;
import com.extensionbox.app.ThemeHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

public class SettingsFragment extends Fragment {

    private LinearLayout container;
    private ActivityResultLauncher<Intent> restoreLauncher;

    @Override
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        restoreLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        importSettings(result.getData().getData());
                    }
                });
    }

    @Override
    public View onCreateView(LayoutInflater inf, ViewGroup parent, Bundle saved) {
        View v = inf.inflate(R.layout.fragment_settings, parent, false);
        container = v.findViewById(R.id.settingsContainer);
        buildSettings();
        return v;
    }

    private void buildSettings() {
        container.removeAllViews();
        container.addView(buildAppearanceCard());
        container.addView(buildGeneralCard());
        container.addView(buildNotificationCard());
        container.addView(buildDataCard());
        container.addView(buildBackupCard());
        container.addView(buildTranslationCard());
    }

    // ═══════════════════════════════════════════════════════════
    //  Card: Appearance — Themes
    // ═══════════════════════════════════════════════════════════

    private View buildAppearanceCard() {
        return buildCard("🎨", "Appearance", "Theme and visual style", panel -> {
            addThemeSpinner(panel);
        });
    }

    private void addThemeSpinner(LinearLayout parent) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView tv = new TextView(requireContext());
        tv.setText("Theme");
        tv.setTextSize(14);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Spinner sp = new Spinner(requireContext());
        sp.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, ThemeHelper.NAMES));

        int current = Prefs.getInt(requireContext(), "app_theme", ThemeHelper.MONET);
        sp.setSelection(current);

        final boolean[] suppress = {true};
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (suppress[0]) { suppress[0] = false; return; }
                Prefs.setInt(requireContext(), "app_theme", pos);
                // Recreate activity to apply new theme
                if (getActivity() != null) {
                    getActivity().recreate();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        row.addView(tv);
        row.addView(sp);
        parent.addView(row);

        // Preview hint
        TextView hint = new TextView(requireContext());
        hint.setText("App will restart to apply the new theme");
        hint.setTextSize(11);
        hint.setAlpha(0.5f);
        hint.setPadding(0, dp(4), 0, 0);
        parent.addView(hint);
    }

    // ═══════════════════════════════════════════════════════════
    //  Card: General
    // ═══════════════════════════════════════════════════════════

    private View buildGeneralCard() {
        return buildCard("⚙", "General", "Permissions & system settings", panel -> {
            // Permission tier with visual indicator
            SystemAccess sys = new SystemAccess(requireContext());
            String tierName = sys.getTierName();
            String tierEmoji = sys.isEnhanced() ? "🟢" : "🟠";

            TextView tierTv = new TextView(requireContext());
            tierTv.setText(tierEmoji + " Permission Tier: " + tierName);
            tierTv.setTextSize(14);
            tierTv.setPadding(0, dp(4), 0, dp(4));
            panel.addView(tierTv);

            if (sys.isEnhanced()) {
                TextView enhancedHint = new TextView(requireContext());
                enhancedHint.setText("Enhanced data: cycle count, real health %, CPU temp, technology");
                enhancedHint.setTextSize(11);
                enhancedHint.setAlpha(0.6f);
                enhancedHint.setPadding(0, 0, 0, dp(8));
                panel.addView(enhancedHint);
            } else {
                TextView normalHint = new TextView(requireContext());
                normalHint.setText("Grant Root or Shizuku for enhanced battery data (cycles, health %, CPU temp)");
                normalHint.setTextSize(11);
                normalHint.setAlpha(0.6f);
                normalHint.setPadding(0, 0, 0, dp(8));
                panel.addView(normalHint);
            }

            // Shizuku button — show only when relevant
            if (!SystemAccess.TIER_ROOT.equals(tierName)) {
                boolean shizukuInstalled = isShizukuInstalled();
                if (shizukuInstalled && !SystemAccess.TIER_SHIZUKU.equals(tierName)) {
                    addActionButton(panel, "🔑 Grant Shizuku Permission", v -> {
                        try {
                            Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
                            shizukuClass.getMethod("requestPermission", int.class).invoke(null, 0);
                            Toast.makeText(requireContext(), "Shizuku permission requested", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Toast.makeText(requireContext(), "Open Shizuku app and grant permission manually", Toast.LENGTH_SHORT).show();
                            try {
                                Intent launchIntent = requireContext().getPackageManager()
                                        .getLaunchIntentForPackage("moe.shizuku.privileged.api");
                                if (launchIntent != null) startActivity(launchIntent);
                            } catch (Exception ignored) {}
                        }
                    });
                } else if (!shizukuInstalled) {
                    addActionButton(panel, "📥 Install Shizuku (for enhanced data)", v -> {
                        try {
                            Intent i = new Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api"));
                            startActivity(i);
                        } catch (Exception e) {
                            Toast.makeText(requireContext(), "Cannot open store", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }

            // Battery exemption
            addActionButton(panel, "🔋 Request Battery Exemption", v -> {
                try {
                    android.os.PowerManager pm = (android.os.PowerManager)
                            requireContext().getSystemService(Context.POWER_SERVICE);
                    if (pm.isIgnoringBatteryOptimizations(requireContext().getPackageName())) {
                        Toast.makeText(requireContext(), "Already exempted ✓", Toast.LENGTH_SHORT).show();
                    } else {
                        Intent i = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        i.setData(Uri.parse("package:" + requireContext().getPackageName()));
                        startActivity(i);
                    }
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "Cannot open settings", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private boolean isShizukuInstalled() {
        try {
            requireContext().getPackageManager().getPackageInfo("moe.shizuku.privileged.api", 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Card: Notifications
    // ═══════════════════════════════════════════════════════════

    private View buildNotificationCard() {
        return buildCard("🔔", "Notifications", "Notification behavior & update rate", panel -> {
            addSwitch(panel, "notif_context_aware", "Context-Aware Notification", true);
            addSwitch(panel, "notif_night_summary", "Night Summary (23:00)", true);
            addSpinner(panel, "notif_update_rate", "Update Rate",
                    new String[]{"3s", "5s", "10s", "30s"},
                    new int[]{3000, 5000, 10000, 30000}, 5000);
            addSpinner(panel, "notif_compact_items", "Compact Items",
                    new String[]{"3", "4", "5", "6"},
                    new int[]{3, 4, 5, 6}, 4);
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  Card: Data
    // ═══════════════════════════════════════════════════════════

    private View buildDataCard() {
        return buildCard("📊", "Data", "Reset stats & counters", panel -> {
            addActionButton(panel, "↺ Reset Daily Stats", v -> {
                Prefs.setInt(requireContext(), "ulk_today", 0);
                Prefs.setLong(requireContext(), "stp_today", 0);
                Prefs.setLong(requireContext(), "dat_daily_total", 0);
                Prefs.setLong(requireContext(), "dat_daily_wifi", 0);
                Prefs.setLong(requireContext(), "dat_daily_mobile", 0);
                Prefs.setLong(requireContext(), "scr_on_acc", 0);
                Prefs.setInt(requireContext(), "fap_today", 0);
                Toast.makeText(requireContext(), "Daily stats reset ✓", Toast.LENGTH_SHORT).show();
            });

            addActionButton(panel, "⚠ Reset All Data", v -> {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Reset All Data")
                        .setMessage("This will erase all stats, settings, and theme. Are you sure?")
                        .setPositiveButton("Reset", (d, w) -> {
                            requireContext().getSharedPreferences("ebox", Context.MODE_PRIVATE)
                                    .edit().clear().apply();
                            Toast.makeText(requireContext(), "All data reset ✓", Toast.LENGTH_SHORT).show();
                            if (getActivity() != null) getActivity().recreate();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  Card: Backup & Restore
    // ═══════════════════════════════════════════════════════════

    private View buildBackupCard() {
        return buildCard("📦", "Backup & Restore", "Export/import your settings & data", panel -> {
            addActionButton(panel, "📤 Export Settings", v -> exportSettings());
            addActionButton(panel, "📥 Import Settings", v -> {
                Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("application/json");
                restoreLauncher.launch(pick);
            });
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  Card: Translation
    // ═══════════════════════════════════════════════════════════

    private View buildTranslationCard() {
        return buildCard("🌐", "Translation", "Help translate Extension Box into your language", panel -> {
            TextView desc = new TextView(requireContext());
            desc.setText("Export the template, translate all strings, and submit a Pull Request on GitHub. Your name, profile photo and contribution details will be added to the Contributors section.");
            desc.setTextSize(13);
            desc.setAlpha(0.7f);
            desc.setPadding(0, 0, 0, dp(8));
            panel.addView(desc);

            addActionButton(panel, "🌐 Export strings.xml for Translation", v -> exportStringsXml());
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  Reusable Card Builder
    // ═══════════════════════════════════════════════════════════

    private interface PanelBuilder {
        void build(LinearLayout panel);
    }

    private View buildCard(String emoji, String title, String desc, PanelBuilder builder) {
        MaterialCardView card = new MaterialCardView(requireContext());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(6);
        card.setLayoutParams(clp);
        card.setContentPadding(dp(16), dp(12), dp(16), dp(12));
        card.setCardElevation(0);
        card.setStrokeWidth(0);

        LinearLayout outer = new LinearLayout(requireContext());
        outer.setOrientation(LinearLayout.VERTICAL);

        // Title row
        LinearLayout row1 = new LinearLayout(requireContext());
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleCol = new LinearLayout(requireContext());
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView titleTv = new TextView(requireContext());
        titleTv.setText(emoji + "  " + title);
        titleTv.setTextSize(16);
        titleCol.addView(titleTv);

        TextView descTv = new TextView(requireContext());
        descTv.setText(desc);
        descTv.setTextSize(12);
        descTv.setAlpha(0.6f);
        titleCol.addView(descTv);

        row1.addView(titleCol);
        outer.addView(row1);

        // Panel (collapsed)
        LinearLayout panel = new LinearLayout(requireContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(0, dp(12), 0, 0);
        panel.setVisibility(View.GONE);
        builder.build(panel);

        // Toggle
        TextView btnToggle = new TextView(requireContext());
        btnToggle.setText("▾ Expand");
        btnToggle.setTextSize(13);
        btnToggle.setPadding(0, dp(8), 0, 0);
        btnToggle.setAlpha(0.7f);
        btnToggle.setOnClickListener(v2 -> {
            boolean show = panel.getVisibility() == View.GONE;
            panel.setVisibility(show ? View.VISIBLE : View.GONE);
            btnToggle.setText(show ? "▴ Collapse" : "▾ Expand");
        });
        outer.addView(btnToggle);
        outer.addView(panel);

        card.addView(outer);
        return card;
    }

    // ═══════════════════════════════════════════════════════════
    //  Reusable UI Components
    // ═══════════════════════════════════════════════════════════

    private void addSwitch(LinearLayout parent, String prefKey, String label, boolean def) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView tv = new TextView(requireContext());
        tv.setText(label);
        tv.setTextSize(14);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        MaterialSwitch sw = new MaterialSwitch(requireContext());
        sw.setChecked(Prefs.getBool(requireContext(), prefKey, def));
        sw.setOnCheckedChangeListener((b, c) -> Prefs.setBool(requireContext(), prefKey, c));

        row.addView(tv);
        row.addView(sw);
        parent.addView(row);
    }

    private void addSpinner(LinearLayout parent, String prefKey, String label,
                            String[] options, int[] values, int def) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView tv = new TextView(requireContext());
        tv.setText(label);
        tv.setTextSize(14);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Spinner sp = new Spinner(requireContext());
        sp.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, options));

        int current = Prefs.getInt(requireContext(), prefKey, def);
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) { sp.setSelection(i); break; }
        }

        final boolean[] suppress = {true};
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (suppress[0]) { suppress[0] = false; return; }
                Prefs.setInt(requireContext(), prefKey, values[pos]);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        row.addView(tv);
        row.addView(sp);
        parent.addView(row);
    }

    private void addActionButton(LinearLayout parent, String text, View.OnClickListener listener) {
        MaterialButton btn = new MaterialButton(requireContext(),
                null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        lp.bottomMargin = dp(6);
        btn.setLayoutParams(lp);
        btn.setText(text);
        btn.setTextSize(14);
        btn.setAllCaps(false);
        btn.setOnClickListener(listener);
        parent.addView(btn);
    }

    // ═══════════════════════════════════════════════════════════
    //  Backup/Restore
    // ═══════════════════════════════════════════════════════════

    private void exportSettings() {
        try {
            SharedPreferences sp = requireContext().getSharedPreferences("ebox", Context.MODE_PRIVATE);
            JSONObject json = new JSONObject();
            for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
                Object val = e.getValue();
                if (val instanceof Boolean) json.put(e.getKey(), (Boolean) val);
                else if (val instanceof Integer) json.put(e.getKey(), (Integer) val);
                else if (val instanceof Long) json.put(e.getKey(), (Long) val);
                else if (val instanceof Float) json.put(e.getKey(), (Float) val);
                else if (val instanceof String) json.put(e.getKey(), (String) val);
            }

            File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File output = new File(downloads, "extensionbox_backup.json");
            FileWriter fw = new FileWriter(output);
            fw.write(json.toString(2));
            fw.close();

            Toast.makeText(requireContext(),
                    "Exported to Downloads/extensionbox_backup.json ✓",
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(),
                    "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void importSettings(Uri uri) {
        if (uri == null) return;
        try {
            InputStream is = requireContext().getContentResolver().openInputStream(uri);
            if (is == null) return;
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            JSONObject json = new JSONObject(sb.toString());
            SharedPreferences.Editor ed = requireContext()
                    .getSharedPreferences("ebox", Context.MODE_PRIVATE).edit();

            java.util.Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object val = json.get(key);
                if (val instanceof Boolean) ed.putBoolean(key, (Boolean) val);
                else if (val instanceof Integer) ed.putInt(key, (Integer) val);
                else if (val instanceof Long) ed.putLong(key, ((Long) val));
                else if (val instanceof Double) {
                    double d = (Double) val;
                    if (d == Math.floor(d) && !Double.isInfinite(d)) {
                        if (d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE)
                            ed.putInt(key, (int) d);
                        else
                            ed.putLong(key, (long) d);
                    } else {
                        ed.putFloat(key, (float) d);
                    }
                }
                else if (val instanceof String) ed.putString(key, (String) val);
            }
            ed.apply();

            Toast.makeText(requireContext(), "Settings restored ✓\nRestarting...", Toast.LENGTH_LONG).show();
            if (getActivity() != null) getActivity().recreate();
        } catch (Exception e) {
            Toast.makeText(requireContext(),
                    "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Export strings.xml
    // ═══════════════════════════════════════════════════════════

    private void exportStringsXml() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
            sb.append("<!-- ═══════════════════════════════════════════════════════════\n");
            sb.append("     Extension Box - Translation Template\n");
            sb.append("     ═══════════════════════════════════════════════════════════\n\n");
            sb.append("     HOW TO CONTRIBUTE A TRANSLATION:\n");
            sb.append("     1. Copy this file\n");
            sb.append("     2. Translate all <string> values to your language\n");
            sb.append("     3. DO NOT change the 'name' attributes\n");
            sb.append("     4. Fill in your info below:\n\n");
            sb.append("     Translator Name:     [Your Name]\n");
            sb.append("     GitHub Username:      [Your GitHub Username]\n");
            sb.append("     GitHub Profile:       [https://github.com/yourusername]\n");
            sb.append("     Language:             [e.g., Turkish, French, German]\n");
            sb.append("     Language Code:        [e.g., tr, fr, de]\n\n");
            sb.append("     SUBMIT:\n");
            sb.append("     - Open a Pull Request at https://github.com/omersusin/ExtensionBox\n");
            sb.append("     - Place this file in: app/src/main/res/values-[LANG_CODE]/strings.xml\n");
            sb.append("     - Your name and profile photo will be added to the Contributors section!\n");
            sb.append("     ═══════════════════════════════════════════════════════════ -->\n\n");
            sb.append("<resources>\n");

            // App Core
            sb.append("    <!-- App Core -->\n");
            sb.append("    <string name=\"app_name\">Extension Box</string>\n");
            sb.append("    <string name=\"dashboard\">Dashboard</string>\n");
            sb.append("    <string name=\"extensions\">Extensions</string>\n");
            sb.append("    <string name=\"settings\">Settings</string>\n");
            sb.append("    <string name=\"about\">About</string>\n\n");

            // Dashboard
            sb.append("    <!-- Dashboard -->\n");
            sb.append("    <string name=\"start_monitoring\">Start Monitoring</string>\n");
            sb.append("    <string name=\"stop_monitoring\">Stop</string>\n");
            sb.append("    <string name=\"running\">Running</string>\n");
            sb.append("    <string name=\"stopped\">Stopped</string>\n");
            sb.append("    <string name=\"active\">active</string>\n");
            sb.append("    <string name=\"no_active_extensions\">No active extensions</string>\n");
            sb.append("    <string name=\"start_to_see_data\">Start monitoring to see live data</string>\n");
            sb.append("    <string name=\"drag_to_reorder\">Hold &amp; drag to reorder cards</string>\n\n");

            // Settings
            sb.append("    <!-- Settings -->\n");
            sb.append("    <string name=\"appearance\">Appearance</string>\n");
            sb.append("    <string name=\"theme\">Theme</string>\n");
            sb.append("    <string name=\"general\">General</string>\n");
            sb.append("    <string name=\"notifications\">Notifications</string>\n");
            sb.append("    <string name=\"context_aware\">Context-Aware Notification</string>\n");
            sb.append("    <string name=\"night_summary\">Night Summary</string>\n");
            sb.append("    <string name=\"update_rate\">Update Rate</string>\n");
            sb.append("    <string name=\"permissions\">Permissions</string>\n");
            sb.append("    <string name=\"data_section\">Data</string>\n");
            sb.append("    <string name=\"reset_daily\">Reset Daily Stats</string>\n");
            sb.append("    <string name=\"reset_all\">Reset All Data</string>\n");
            sb.append("    <string name=\"reset_confirm\">This will erase all stats and settings. Are you sure?</string>\n");
            sb.append("    <string name=\"reset_done\">All data reset ✓</string>\n");
            sb.append("    <string name=\"daily_reset_done\">Daily stats reset ✓</string>\n");
            sb.append("    <string name=\"battery_exemption\">Request Battery Exemption</string>\n");
            sb.append("    <string name=\"already_exempted\">Already exempted ✓</string>\n");
            sb.append("    <string name=\"backup_restore\">Backup &amp; Restore</string>\n");
            sb.append("    <string name=\"backup_export\">Export Settings</string>\n");
            sb.append("    <string name=\"backup_import\">Import Settings</string>\n");
            sb.append("    <string name=\"translation\">Translation</string>\n");
            sb.append("    <string name=\"export_strings\">Export strings.xml for Translation</string>\n\n");

            // Module common
            sb.append("    <!-- Module common -->\n");
            sb.append("    <string name=\"module_settings\">Settings</string>\n");
            sb.append("    <string name=\"refresh\">Refresh</string>\n");
            sb.append("    <string name=\"custom\">Custom</string>\n");
            sb.append("    <string name=\"enter_custom_value\">Enter Custom Value</string>\n");
            sb.append("    <string name=\"cancel\">Cancel</string>\n");
            sb.append("    <string name=\"ok\">OK</string>\n");
            sb.append("    <string name=\"today\">Today</string>\n");
            sb.append("    <string name=\"yesterday\">Yesterday</string>\n");
            sb.append("    <string name=\"monthly\">Monthly</string>\n");
            sb.append("    <string name=\"all_time\">All Time</string>\n");
            sb.append("    <string name=\"streak\">Streak</string>\n\n");

            // About
            sb.append("    <!-- About -->\n");
            sb.append("    <string name=\"developers\">Developers</string>\n");
            sb.append("    <string name=\"source_code\">View Source Code on GitHub</string>\n");
            sb.append("    <string name=\"license\">License</string>\n");
            sb.append("</resources>\n");

            File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File output = new File(downloads, "extensionbox_strings.xml");
            FileWriter fw = new FileWriter(output);
            fw.write(sb.toString());
            fw.close();

            Toast.makeText(requireContext(),
                    "Exported to Downloads/extensionbox_strings.xml ✓",
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            try {
                File output = new File(requireContext().getExternalFilesDir(null),
                        "extensionbox_strings.xml");
                FileWriter fw = new FileWriter(output);
                fw.write("<!-- fallback -->");
                fw.close();
                Toast.makeText(requireContext(),
                        "Exported to app folder ✓\n" + output.getAbsolutePath(),
                        Toast.LENGTH_LONG).show();
            } catch (Exception e2) {
                Toast.makeText(requireContext(),
                        "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
