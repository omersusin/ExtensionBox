package com.extensionbox.app.ui;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.AdapterView;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.extensionbox.app.Prefs;
import com.extensionbox.app.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public class ExtensionsFragment extends Fragment {

    private RecyclerView recyclerView;

    @Override
    public View onCreateView(LayoutInflater inf, ViewGroup parent, Bundle saved) {
        View v = inf.inflate(R.layout.fragment_extensions, parent, false);
        recyclerView = v.findViewById(R.id.extRecycler);

        setupRecyclerView();
        return v;
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(requireContext()));
        recyclerView.setPadding(dp(16), dp(16), dp(16), dp(16));
        recyclerView.setClipToPadding(false);
        recyclerView.setAdapter(new ExtensionAdapter());
    }

    private class ExtensionAdapter extends RecyclerView.Adapter<ExtensionAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_extension_card, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            String key = ModuleRegistry.keyAt(position);
            String name = ModuleRegistry.nameAt(position);
            String desc = ModuleRegistry.descAt(position);
            int iconRes = ModuleRegistry.iconResFor(key);
            boolean def = ModuleRegistry.defAt(position);
            boolean enabled = Prefs.isModuleEnabled(requireContext(), key, def);

            TextView tvTitle = holder.itemView.findViewById(R.id.tvTitle);
            TextView tvDesc = holder.itemView.findViewById(R.id.tvDesc);
            ImageView imgIcon = holder.itemView.findViewById(R.id.imgIcon);
            MaterialSwitch sw = holder.itemView.findViewById(R.id.switchEnable);
            MaterialButton btnSettings = holder.itemView.findViewById(R.id.btnSettings);
            MaterialCardView card = (MaterialCardView) holder.itemView;

            tvTitle.setText(name);
            tvDesc.setText(desc);
            imgIcon.setImageResource(iconRes);

            sw.setOnCheckedChangeListener(null);
            sw.setChecked(enabled);

            card.setAlpha(enabled ? 1.0f : 0.6f);
            btnSettings.setVisibility(enabled ? View.VISIBLE : View.GONE);

            sw.setOnCheckedChangeListener((b, checked) -> {

                if (checked && "app_usage".equals(key) && !hasUsageAccess()) {
                    sw.setChecked(false);

                    try {
                        startActivity(new android.content.Intent(
                            android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS));
                        android.widget.Toast.makeText(requireContext(),
                            "Please enable Usage Access for Extension Box", android.widget.Toast.LENGTH_LONG).show();
                    } catch (Exception ignored) {}
                    return;
                }
                Prefs.setModuleEnabled(requireContext(), key, checked);
                card.setAlpha(checked ? 1.0f : 0.6f);
                btnSettings.setVisibility(checked ? View.VISIBLE : View.GONE);
            });

            btnSettings.setOnClickListener(v -> showSettingsSheet(key, name));
        }

        @Override
        public int getItemCount() {
            return ModuleRegistry.count();
        }

        class VH extends RecyclerView.ViewHolder {
            VH(@NonNull View v) { super(v); }
        }
    }

    private boolean hasUsageAccess() {
        try {
            android.app.AppOpsManager aom = (android.app.AppOpsManager)
                    requireContext().getSystemService(android.content.Context.APP_OPS_SERVICE);
            int mode = aom.checkOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    requireContext().getPackageName());
            return mode == android.app.AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    private void showSettingsSheet(String key, String name) {
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(16), dp(24), dp(24));

        TextView title = new TextView(requireContext());
        title.setText(name + " Settings");
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(16));
        layout.addView(title);

        buildSettingsPanel(layout, key);

        if (layout.getChildCount() == 1) {
            TextView empty = new TextView(requireContext());
            empty.setText("No settings available for this module.");
            empty.setAlpha(0.6f);
            layout.addView(empty);
        }

        sheet.setContentView(layout);
        sheet.show();
    }

    private void buildSettingsPanel(LinearLayout panel, String key) {
        switch (key) {
            case "battery":
                addSpinnerWithCustom(panel, "bat_interval", "Refresh",
                        new String[]{"5s","10s","30s","60s"}, new int[]{5000,10000,30000,60000}, 10000);
                addSwitch(panel, "bat_low_alert", "Low Battery Alert", true);
                addSpinnerWithCustom(panel, "bat_low_thresh", "Alert Threshold",
                        new String[]{"5%","10%","15%","20%","25%","30%"}, new int[]{5,10,15,20,25,30}, 15);
                addSwitch(panel, "bat_temp_alert", "High Temp Alert", true);
                addSpinnerWithCustom(panel, "bat_temp_thresh", "Temp Threshold",
                        new String[]{"38°C","40°C","42°C","45°C"}, new int[]{38,40,42,45}, 42);
                break;
            case "cpu_ram":
                addSpinnerWithCustom(panel, "cpu_interval", "Refresh",
                        new String[]{"3s","5s","10s","30s"}, new int[]{3000,5000,10000,30000}, 5000);
                addSwitch(panel, "cpu_ram_alert", "High RAM Alert", false);
                addSpinnerWithCustom(panel, "cpu_ram_thresh", "RAM Threshold",
                        new String[]{"80%","85%","90%","95%"}, new int[]{80,85,90,95}, 90);
                break;
            case "screen":
                addSpinnerWithCustom(panel, "scr_interval", "Refresh",
                        new String[]{"5s","10s","30s"}, new int[]{5000,10000,30000}, 10000);
                addSpinnerWithCustom(panel, "scr_time_limit", "Screen Time Limit",
                        new String[]{"Off","1h","2h","3h","4h","6h","8h"}, new int[]{0,60,120,180,240,360,480}, 0);
                addSwitch(panel, "scr_show_drain", "Show Drain Info", true);
                addSwitch(panel, "scr_show_yesterday", "Show Yesterday", true);
                break;
            case "sleep":
                addSpinnerWithCustom(panel, "slp_interval", "Refresh",
                        new String[]{"10s","30s","60s"}, new int[]{10000,30000,60000}, 30000);
                break;
            case "network":
                addSpinnerWithCustom(panel, "net_interval", "Refresh",
                        new String[]{"1s","3s","5s","10s"}, new int[]{1000,3000,5000,10000}, 3000);
                break;
            case "data":
                addSpinnerWithCustom(panel, "dat_interval", "Refresh",
                        new String[]{"30s","60s","5min"}, new int[]{30000,60000,300000}, 60000);
                addSwitch(panel, "dat_show_breakdown", "WiFi/Mobile Split", true);
                addSpinnerWithCustom(panel, "dat_plan_limit", "Monthly Plan",
                        new String[]{"Off","1GB","2GB","5GB","10GB","20GB","50GB"}, new int[]{0,1024,2048,5120,10240,20480,51200}, 0);
                addSpinnerWithCustom(panel, "dat_plan_alert_pct", "Plan Alert At",
                        new String[]{"80%","90%","95%"}, new int[]{80,90,95}, 90);
                addSpinnerWithCustom(panel, "dat_billing_day", "Billing Day",
                        new String[]{"1","5","10","15","20","25"}, new int[]{1,5,10,15,20,25}, 1);
                break;
            case "unlock":
                addSpinnerWithCustom(panel, "ulk_interval", "Refresh",
                        new String[]{"5s","10s","30s"}, new int[]{5000,10000,30000}, 10000);
                addSpinnerWithCustom(panel, "ulk_daily_limit", "Daily Limit",
                        new String[]{"Off","20","30","50","75","100"}, new int[]{0,20,30,50,75,100}, 0);
                addSwitch(panel, "ulk_limit_alert", "Alert on Limit", true);
                addSpinnerWithCustom(panel, "ulk_debounce", "Debounce",
                        new String[]{"3s","5s","10s"}, new int[]{3000,5000,10000}, 5000);
                break;
            case "storage":
                addSpinnerWithCustom(panel, "sto_interval", "Refresh",
                        new String[]{"1min","5min","15min"}, new int[]{60000,300000,900000}, 300000);
                addSwitch(panel, "sto_low_alert", "Low Storage Alert", true);
                addSpinnerWithCustom(panel, "sto_low_thresh_mb", "Low Threshold",
                        new String[]{"500MB","1GB","2GB","5GB"}, new int[]{500,1000,2000,5000}, 1000);
                break;
            case "connection":
                addSpinnerWithCustom(panel, "con_interval", "Refresh",
                        new String[]{"5s","10s","30s"}, new int[]{5000,10000,30000}, 10000);
                break;
            case "uptime":
                addSpinnerWithCustom(panel, "upt_interval", "Refresh",
                        new String[]{"30s","1min","5min"}, new int[]{30000,60000,300000}, 60000);
                break;
            case "steps":
                addSpinnerWithCustom(panel, "stp_interval", "Refresh",
                        new String[]{"5s","10s","30s"}, new int[]{5000,10000,30000}, 10000);
                addSpinnerWithCustom(panel, "stp_goal", "Daily Goal",
                        new String[]{"Off","5000","8000","10000","15000"}, new int[]{0,5000,8000,10000,15000}, 10000);
                addSpinnerWithCustom(panel, "stp_stride_cm", "Step Length",
                        new String[]{"60cm","65cm","70cm","75cm","80cm","85cm"}, new int[]{60,65,70,75,80,85}, 75);
                addSwitch(panel, "stp_show_distance", "Show Distance", true);
                addSwitch(panel, "stp_show_yesterday", "Show Yesterday", true);
                addSwitch(panel, "stp_show_goal", "Show Goal", true);
                break;
            case "speedtest":
                addSpinnerWithCustom(panel, "spd_interval", "Display Refresh",
                        new String[]{"10s","30s","60s"}, new int[]{10000,30000,60000}, 30000);
                addSwitch(panel, "spd_auto_test", "Auto Test", true);
                addSpinnerWithCustom(panel, "spd_test_freq", "Test Frequency",
                        new String[]{"15min","30min","1h","2h"}, new int[]{15,30,60,120}, 60);
                addSwitch(panel, "spd_wifi_only", "WiFi Only", true);
                addSwitch(panel, "spd_show_ping", "Show Ping", true);
                addSpinnerWithCustom(panel, "spd_daily_limit", "Daily Test Limit",
                        new String[]{"5","10","20","∞"}, new int[]{5,10,20,9999}, 10);
                break;
            case "fap":
                addSpinnerWithCustom(panel, "fap_interval", "Refresh",
                        new String[]{"30s","1min","5min"}, new int[]{30000,60000,300000}, 60000);
                addSpinnerWithCustom(panel, "fap_daily_limit", "Daily Limit Warning",
                        new String[]{"Off","1","2","3","5"}, new int[]{0,1,2,3,5}, 0);
                addSwitch(panel, "fap_show_streak", "Show Streak", true);
                addSwitch(panel, "fap_show_yesterday", "Show Yesterday", true);
                addSwitch(panel, "fap_show_alltime", "Show All-Time Total", true);
                break;
        }
    }

    private void addSwitch(LinearLayout parent, String prefKey, String label, boolean def) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView tv = new TextView(requireContext());
        tv.setText(label);
        tv.setTextSize(16);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        MaterialSwitch sw = new MaterialSwitch(requireContext());
        sw.setChecked(Prefs.getBool(requireContext(), prefKey, def));
        sw.setOnCheckedChangeListener((b, c) -> Prefs.setBool(requireContext(), prefKey, c));

        row.addView(tv);
        row.addView(sw);
        parent.addView(row);
    }

    private void addSpinnerWithCustom(LinearLayout parent, String prefKey, String label,
                                      String[] options, int[] values, int def) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(4), 0, dp(12));

        TextView tv = new TextView(requireContext());
        tv.setText(label);
        tv.setTextSize(14);
        tv.setAlpha(0.8f);
        row.addView(tv);

        String[] extOptions = new String[options.length + 1];
        int[] extValues = new int[values.length + 1];
        System.arraycopy(options, 0, extOptions, 0, options.length);
        System.arraycopy(values, 0, extValues, 0, values.length);
        extOptions[options.length] = "Custom…";
        extValues[values.length] = -1;

        Spinner sp = new Spinner(requireContext());
        sp.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, extOptions));

        int current = Prefs.getInt(requireContext(), prefKey, def);
        boolean found = false;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                sp.setSelection(i);
                found = true;
                break;
            }
        }

        if (!found) {
            sp.setSelection(extOptions.length - 1);
        }

        final boolean[] suppressFirst = {true};
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (suppressFirst[0]) { suppressFirst[0] = false; return; }

                if (extValues[pos] == -1) {

                    showCustomInputDialog(prefKey, label, sp, extValues);
                } else {
                    Prefs.setInt(requireContext(), prefKey, extValues[pos]);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        row.addView(sp);
        parent.addView(row);
    }

    private void showCustomInputDialog(String prefKey, String label, Spinner sp, int[] values) {
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Enter value");
        int currentVal = Prefs.getInt(requireContext(), prefKey, 0);
        if (currentVal > 0) input.setText(String.valueOf(currentVal));

        int padding = dp(24);
        LinearLayout wrapper = new LinearLayout(requireContext());
        wrapper.setPadding(padding, dp(8), padding, 0);
        wrapper.addView(input);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Custom: " + label)
                .setView(wrapper)
                .setPositiveButton("OK", (d, w) -> {
                    try {
                        int val = Integer.parseInt(input.getText().toString().trim());
                        if (val > 0) {
                            Prefs.setInt(requireContext(), prefKey, val);
                        }
                    } catch (NumberFormatException ignored) {}
                })
                .setNegativeButton("Cancel", (d, w) -> {

                    int saved = Prefs.getInt(requireContext(), prefKey, values[0]);
                    for (int i = 0; i < values.length; i++) {
                        if (values[i] == saved) { sp.setSelection(i); break; }
                    }
                })
                .show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
