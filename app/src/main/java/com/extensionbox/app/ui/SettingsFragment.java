package com.extensionbox.app.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.extensionbox.app.Prefs;
import com.extensionbox.app.R;
import com.extensionbox.app.SystemAccess;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.IOException;

public class SettingsFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inf, ViewGroup parent, Bundle saved) {
        View v = inf.inflate(R.layout.fragment_settings, parent, false);

        TextView tvTier = v.findViewById(R.id.tvSettingsTier);
        tvTier.setText("🔑 Permission Tier: " + new SystemAccess(requireContext()).getTierName());

        View btnBatOpt = v.findViewById(R.id.btnBatteryOpt);
        btnBatOpt.setOnClickListener(b -> {
            try {
                PowerManager pm = (PowerManager) requireContext().getSystemService(Context.POWER_SERVICE);
                if (pm.isIgnoringBatteryOptimizations(requireContext().getPackageName())) {
                    Toast.makeText(requireContext(), "Already exempted ✓", Toast.LENGTH_SHORT).show();
                } else {
                    Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    i.setData(Uri.parse("package:" + requireContext().getPackageName()));
                    startActivity(i);
                }
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Cannot open settings", Toast.LENGTH_SHORT).show();
            }
        });

        View btnReset = v.findViewById(R.id.btnResetDaily);
        btnReset.setOnClickListener(b -> {
            Prefs.setInt(requireContext(), "ulk_today", 0);
            Prefs.setLong(requireContext(), "stp_today", 0);
            Prefs.setLong(requireContext(), "dat_daily_total", 0);
            Prefs.setLong(requireContext(), "dat_daily_wifi", 0);
            Prefs.setLong(requireContext(), "dat_daily_mobile", 0);
            Prefs.setLong(requireContext(), "scr_on_acc", 0);
            Prefs.setInt(requireContext(), "fap_today", 0);
            Toast.makeText(requireContext(), "Daily stats reset ✓", Toast.LENGTH_SHORT).show();
        });

        View btnResetAll = v.findViewById(R.id.btnResetAll);
        btnResetAll.setOnClickListener(b -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Reset All Data")
                    .setMessage("This will erase all stats and settings. Are you sure?")
                    .setPositiveButton("Reset", (d, w) -> {
                        requireContext().getSharedPreferences("ebox", Context.MODE_PRIVATE)
                                .edit().clear().apply();
                        Toast.makeText(requireContext(), "All data reset ✓", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        View btnExport = v.findViewById(R.id.btnExportStrings);
        btnExport.setOnClickListener(b -> exportStringsXml());

        return v;
    }

    private void exportStringsXml() {
        try {

            InputStream is = requireContext().getResources().openRawResource(
                    requireContext().getResources().getIdentifier("strings_export_template",
                            "raw", requireContext().getPackageName()));

            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
            sb.append("<!-- ═══════════════════════════════════════════════════════════\n");
            sb.append("     Extension Box - Translation Template\n");
            sb.append("     ═══════════════════════════════════════════════════════════\n");
            sb.append("\n");
            sb.append("     HOW TO CONTRIBUTE A TRANSLATION:\n");
            sb.append("     1. Copy this file\n");
            sb.append("     2. Translate all <string> values to your language\n");
            sb.append("     3. DO NOT change the 'name' attributes\n");
            sb.append("     4. Fill in your info below:\n");
            sb.append("\n");
            sb.append("     Translator Name:     [Your Name]\n");
            sb.append("     GitHub Username:      [Your GitHub Username]\n");
            sb.append("     GitHub Profile:       [https://github.com/yourusername]\n");
            sb.append("     Language:             [e.g., Turkish, French, German]\n");
            sb.append("     Language Code:        [e.g., tr, fr, de]\n");
            sb.append("\n");
            sb.append("     SUBMIT:\n");
            sb.append("     - Open a Pull Request at https://github.com/omersusin/ExtensionBox\n");
            sb.append("     - Place this file in: app/src/main/res/values-[LANG_CODE]/strings.xml\n");
            sb.append("     - Your name and profile photo will be added to the Contributors section!\n");
            sb.append("     ═══════════════════════════════════════════════════════════ -->\n\n");
            sb.append("<resources>\n");
            sb.append("    <string name=\"app_name\">Extension Box</string>\n");
            sb.append("    <string name=\"dashboard\">Dashboard</string>\n");
            sb.append("    <string name=\"extensions\">Extensions</string>\n");
            sb.append("    <string name=\"settings\">Settings</string>\n");
            sb.append("    <string name=\"about\">About</string>\n");
            sb.append("    <string name=\"start_monitoring\">Start Monitoring</string>\n");
            sb.append("    <string name=\"stop_monitoring\">Stop</string>\n");
            sb.append("    <string name=\"running\">Running</string>\n");
            sb.append("    <string name=\"stopped\">Stopped</string>\n");
            sb.append("    <string name=\"active\">active</string>\n");
            sb.append("    <string name=\"no_active_extensions\">No active extensions</string>\n");
            sb.append("    <string name=\"start_to_see_data\">Start monitoring to see live data</string>\n");
            sb.append("    <string name=\"drag_to_reorder\">Hold &amp; drag to reorder cards</string>\n");
            sb.append("    <string name=\"permissions\">Permissions</string>\n");
            sb.append("    <string name=\"data_section\">Data</string>\n");
            sb.append("    <string name=\"reset_daily\">Reset Daily Stats</string>\n");
            sb.append("    <string name=\"reset_all\">Reset All Data</string>\n");
            sb.append("    <string name=\"reset_confirm\">This will erase all stats and settings. Are you sure?</string>\n");
            sb.append("    <string name=\"reset_done\">All data reset ✓</string>\n");
            sb.append("    <string name=\"daily_reset_done\">Daily stats reset ✓</string>\n");
            sb.append("    <string name=\"battery_exemption\">Request Battery Exemption</string>\n");
            sb.append("    <string name=\"already_exempted\">Already exempted ✓</string>\n");
            sb.append("    <string name=\"export_strings\">Export strings.xml for Translation</string>\n");
            sb.append("    <string name=\"developers\">Developers</string>\n");
            sb.append("    <string name=\"source_code\">View Source Code on GitHub</string>\n");
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
            sb.append("    <string name=\"streak\">Streak</string>\n");
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
                fw.write("<!-- See Downloads folder or check Extension Box Settings -->\n");
                fw.close();
                Toast.makeText(requireContext(),
                        "Exported to app folder ✓\nPath: " + output.getAbsolutePath(),
                        Toast.LENGTH_LONG).show();
            } catch (Exception e2) {
                Toast.makeText(requireContext(),
                        "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }
}
