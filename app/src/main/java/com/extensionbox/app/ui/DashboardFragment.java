package com.extensionbox.app.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.extensionbox.app.MonitorService;
import com.extensionbox.app.Prefs;
import com.extensionbox.app.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.LinkedHashMap;
import java.util.Map;

public class DashboardFragment extends Fragment {

    private LinearLayout container;
    private TextView tvStatus;
    private MaterialButton btnToggle;
    private Handler handler;
    private Runnable refreshRunnable;

    @Override
    public View onCreateView(LayoutInflater inf, ViewGroup parent, Bundle saved) {
        View v = inf.inflate(R.layout.fragment_dashboard, parent, false);
        container = v.findViewById(R.id.dashCardContainer);
        tvStatus = v.findViewById(R.id.tvDashStatus);
        btnToggle = v.findViewById(R.id.btnDashToggle);

        btnToggle.setOnClickListener(b -> {
            if (Prefs.isRunning(requireContext())) {
                Intent i = new Intent(requireContext(), MonitorService.class);
                i.setAction(MonitorService.ACTION_STOP);
                requireContext().startService(i);
            } else {
                ContextCompat.startForegroundService(requireContext(),
                        new Intent(requireContext(), MonitorService.class));
            }
            handler.postDelayed(this::refresh, 500);
        });

        handler = new Handler(Looper.getMainLooper());
        refreshRunnable = () -> {
            if (isAdded()) {
                refresh();
                handler.postDelayed(refreshRunnable, 2000);
            }
        };
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
        handler.postDelayed(refreshRunnable, 2000);
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacksAndMessages(null);
    }

    private void refresh() {
        if (!isAdded() || getContext() == null) return;

        boolean running = Prefs.isRunning(requireContext());
        int active = 0;
        for (int i = 0; i < ModuleRegistry.count(); i++) {
            if (Prefs.isModuleEnabled(requireContext(), ModuleRegistry.keyAt(i), ModuleRegistry.defAt(i)))
                active++;
        }

        tvStatus.setText(running
                ? "● Running • " + active + "/" + ModuleRegistry.count() + " active"
                : "○ Stopped");
        tvStatus.setTextColor(running ? 0xFF4CAF50 : 0xFFF44336);
        btnToggle.setText(running ? "⏹  Stop" : "▶  Start Monitoring");

        container.removeAllViews();

        if (!running) {
            TextView tv = new TextView(requireContext());
            tv.setText("Start monitoring to see live data");
            tv.setTextColor(0xFF888888);
            tv.setPadding(0, 48, 0, 0);
            tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            container.addView(tv);
            return;
        }

        for (int i = 0; i < ModuleRegistry.count(); i++) {
            String key = ModuleRegistry.keyAt(i);
            LinkedHashMap<String, String> data = MonitorService.getModuleData(key);
            if (data == null || data.isEmpty()) continue;

            MaterialCardView card = new MaterialCardView(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            card.setLayoutParams(lp);
            card.setContentPadding(dp(16), dp(12), dp(16), dp(12));
            card.setCardElevation(0);
            card.setStrokeWidth(0);

            LinearLayout inner = new LinearLayout(requireContext());
            inner.setOrientation(LinearLayout.VERTICAL);

            // Title
            TextView title = new TextView(requireContext());
            title.setText(ModuleRegistry.emojiAt(i) + "  " + ModuleRegistry.nameAt(i));
            title.setTextSize(16);
            title.setTypeface(null, Typeface.BOLD);
            title.setPadding(0, 0, 0, dp(8));
            inner.addView(title);

            // Data rows
            for (Map.Entry<String, String> entry : data.entrySet()) {
                LinearLayout row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, dp(2), 0, dp(2));

                TextView label = new TextView(requireContext());
                String rawKey = entry.getKey();
                int dot = rawKey.lastIndexOf('.');
                String labelText = dot >= 0 ? rawKey.substring(dot + 1) : rawKey;
                labelText = labelText.substring(0, 1).toUpperCase() + labelText.substring(1).replace("_", " ");
                label.setText(labelText);
                label.setTextSize(13);
                label.setAlpha(0.7f);
                LinearLayout.LayoutParams lbl = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                label.setLayoutParams(lbl);

                TextView value = new TextView(requireContext());
                value.setText(entry.getValue());
                value.setTextSize(14);
                value.setTypeface(null, Typeface.BOLD);

                row.addView(label);
                row.addView(value);
                inner.addView(row);
            }

            card.addView(inner);
            container.addView(card);
        }

        if (container.getChildCount() == 0) {
            TextView tv = new TextView(requireContext());
            tv.setText("No active extensions");
            tv.setTextColor(0xFF888888);
            tv.setPadding(0, 48, 0, 0);
            tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            container.addView(tv);
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
