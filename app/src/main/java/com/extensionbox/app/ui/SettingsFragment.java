package com.extensionbox.app.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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

public class SettingsFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inf, ViewGroup parent, Bundle saved) {
        View v = inf.inflate(R.layout.fragment_settings, parent, false);

        TextView tvTier = v.findViewById(R.id.tvSettingsTier);
        tvTier.setText("🔑 Permission Tier: " + new SystemAccess(requireContext()).getTierName());

        MaterialButton btnBatOpt = v.findViewById(R.id.btnBatteryOpt);
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

        MaterialButton btnReset = v.findViewById(R.id.btnResetDaily);
        btnReset.setOnClickListener(b -> {
            Prefs.setInt(requireContext(), "ulk_today", 0);
            Prefs.setLong(requireContext(), "stp_today", 0);
            Prefs.setLong(requireContext(), "dat_daily_total", 0);
            Prefs.setLong(requireContext(), "dat_daily_wifi", 0);
            Prefs.setLong(requireContext(), "dat_daily_mobile", 0);
            Prefs.setLong(requireContext(), "scr_on_acc", 0);
            Toast.makeText(requireContext(), "Daily stats reset ✓", Toast.LENGTH_SHORT).show();
        });

        MaterialButton btnResetAll = v.findViewById(R.id.btnResetAll);
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

        return v;
    }
}
