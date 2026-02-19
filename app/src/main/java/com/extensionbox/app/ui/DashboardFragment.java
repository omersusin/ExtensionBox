package com.extensionbox.app.ui;

import android.annotation.SuppressLint;
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

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.extensionbox.app.MonitorService;
import com.extensionbox.app.Prefs;
import com.extensionbox.app.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;


import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DashboardFragment extends Fragment {

    private RecyclerView recyclerView;
    private TextView tvStatus;
    private MaterialButton btnToggle;
    private Handler handler;
    private Runnable refreshRunnable;
    private DashCardAdapter adapter;
    private ItemTouchHelper touchHelper;
    private final List<DashCard> cards = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inf, ViewGroup parent, Bundle saved) {
        View v = inf.inflate(R.layout.fragment_dashboard, parent, false);
        recyclerView = v.findViewById(R.id.dashRecycler);
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

        adapter = new DashCardAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
        recyclerView.setItemAnimator(null);

        // Drag-to-reorder
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder from,
                                  @NonNull RecyclerView.ViewHolder to) {
                int fromPos = from.getAdapterPosition();
                int toPos = to.getAdapterPosition();
                if (fromPos < 0 || toPos < 0) return false;
                Collections.swap(cards, fromPos, toPos);
                adapter.notifyItemMoved(fromPos, toPos);
                saveCardOrder();
                return true;
            }

            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) { }
            @Override public boolean isLongPressDragEnabled() { return true; }
        };
        touchHelper = new ItemTouchHelper(callback);
        touchHelper.attachToRecyclerView(recyclerView);

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

    @SuppressLint("NotifyDataSetChanged")
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

        int ok = 0xFF4CAF50;
        int bad = 0xFFF44336;
        tvStatus.setTextColor(running ? ok : bad);
        btnToggle.setText(running ? "⏹  Stop" : "▶  Start Monitoring");

        cards.clear();

        if (!running) {
            cards.add(new DashCard("info", "Welcome", "Start monitoring to see live data.", null));
            adapter.notifyDataSetChanged();
            return;
        }

        List<String> orderedKeys = getSavedOrder();

        for (String key : orderedKeys) {
            LinkedHashMap<String, String> data = MonitorService.getModuleData(key);
            if (data == null || data.isEmpty()) continue;

            String emoji = ModuleRegistry.emojiFor(key);
            String name = ModuleRegistry.nameFor(key);
            cards.add(new DashCard(key, emoji + "  " + name, null, data));
        }

        if (cards.isEmpty()) {
            cards.add(new DashCard("info", "No data", "No active extensions.", null));
        }

        adapter.notifyDataSetChanged();
    }

    private List<String> getSavedOrder() {
        String saved = Prefs.getString(requireContext(), "dash_card_order", "");
        List<String> ordered = new ArrayList<>();

        if (!saved.isEmpty()) {
            String[] keys = saved.split(",");
            for (String k : keys) {
                if (!k.isEmpty()) ordered.add(k);
            }
        }

        for (int i = 0; i < ModuleRegistry.count(); i++) {
            String key = ModuleRegistry.keyAt(i);
            if (!ordered.contains(key)) ordered.add(key);
        }

        return ordered;
    }

    private void saveCardOrder() {
        StringBuilder sb = new StringBuilder();
        for (DashCard c : cards) {
            if (!"info".equals(c.key)) {
                if (sb.length() > 0) sb.append(",");
                sb.append(c.key);
            }
        }
        Prefs.setString(requireContext(), "dash_card_order", sb.toString());
    }

    private static class DashCard {
        String key;
        String title;
        String message;
        LinkedHashMap<String, String> data;

        DashCard(String key, String title, String message, LinkedHashMap<String, String> data) {
            this.key = key;
            this.title = title;
            this.message = message;
            this.data = data;
        }
    }

    private class DashCardAdapter extends RecyclerView.Adapter<DashCardAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            MaterialCardView card = new MaterialCardView(parent.getContext());

            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(10);
            card.setLayoutParams(lp);

            // Visual polish — match Extensions/Settings card style
            card.setCardElevation(0);
            card.setRadius(dp(16));
            card.setStrokeWidth(0);

            card.setContentPadding(dp(16), dp(14), dp(16), dp(14));
            return new VH(card);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            DashCard item = cards.get(position);
            MaterialCardView card = (MaterialCardView) holder.itemView;
            card.removeAllViews();

            LinearLayout inner = new LinearLayout(requireContext());
            inner.setOrientation(LinearLayout.VERTICAL);

            // Header
            LinearLayout header = new LinearLayout(requireContext());
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(android.view.Gravity.CENTER_VERTICAL);

            TextView handle = new TextView(requireContext());
            handle.setText("⋮⋮");
            handle.setTextSize(16);
            handle.setAlpha(0.25f);
            handle.setPadding(0, 0, dp(10), 0);
            header.addView(handle);

            TextView title = new TextView(requireContext());
            title.setText(item.title);
            title.setTextSize(16);
            title.setTypeface(null, Typeface.BOLD);
            title.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            header.addView(title);

            inner.addView(header);

            // Body
            if ("info".equals(item.key)) {
                TextView tv = new TextView(requireContext());
                tv.setText(item.message);
                tv.setAlpha(0.75f);
                tv.setTextSize(13);
                tv.setPadding(0, dp(10), 0, dp(2));
                inner.addView(tv);

                card.addView(inner);
                return;
            }

            View spacer = new View(requireContext());
            spacer.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(10)));
            inner.addView(spacer);

            if (item.data != null) {
                int shown = 0;
                for (Map.Entry<String, String> entry : item.data.entrySet()) {
                    if (shown >= 6) break; // keep cards compact
                    shown++;

                    LinearLayout row = new LinearLayout(requireContext());
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setPadding(0, dp(3), 0, dp(3));

                    TextView label = new TextView(requireContext());
                    String rawKey = entry.getKey();
                    int dot = rawKey.lastIndexOf('.');
                    String labelText = dot >= 0 ? rawKey.substring(dot + 1) : rawKey;
                    if (labelText.length() > 0) {
                        labelText = labelText.substring(0, 1).toUpperCase()
                                + labelText.substring(1).replace("_", " ");
                    }
                    label.setText(labelText);
                    label.setTextSize(13);
                    label.setAlpha(0.65f);
                    label.setLayoutParams(new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

                    TextView value = new TextView(requireContext());
                    value.setText(entry.getValue());
                    value.setTextSize(14);
                    value.setTypeface(null, Typeface.BOLD);

                    row.addView(label);
                    row.addView(value);
                    inner.addView(row);
                }
            }

            // "vs yesterday" comparison footer
            String ydText = getYesterdayComparison(item.key);
            if (ydText != null) {
                TextView ydTv = new TextView(requireContext());
                ydTv.setText(ydText);
                ydTv.setTextSize(12);
                ydTv.setAlpha(0.6f);
                ydTv.setPadding(0, dp(8), 0, 0);
                inner.addView(ydTv);
            }

            // Fap Counter: +1 button on dashboard
            if ("fap".equals(item.key)) {
                MaterialButton fapBtn = new MaterialButton(requireContext(),
                        null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(40));
                btnLp.topMargin = dp(8);
                fapBtn.setLayoutParams(btnLp);
                fapBtn.setText("🍆 +1");
                fapBtn.setTextSize(14);
                fapBtn.setAllCaps(false);
                fapBtn.setOnClickListener(fv -> {
                    // Increment via MonitorService's FapCounterModule
                    MonitorService svc = MonitorService.getInstance();
                    if (svc != null) {
                        com.extensionbox.app.modules.FapCounterModule fapMod = svc.getFapModule();
                        if (fapMod != null) {
                            fapMod.increment();
                            refresh(); // immediate update
                        }
                    } else {
                        // Fallback: increment directly in prefs
                        int t = Prefs.getInt(requireContext(), "fap_today", 0) + 1;
                        Prefs.setInt(requireContext(), "fap_today", t);
                        int at = Prefs.getInt(requireContext(), "fap_all_time", 0) + 1;
                        Prefs.setInt(requireContext(), "fap_all_time", at);
                        int mo = Prefs.getInt(requireContext(), "fap_monthly", 0) + 1;
                        Prefs.setInt(requireContext(), "fap_monthly", mo);
                        refresh();
                    }
                });
                inner.addView(fapBtn);
            }

            card.addView(inner);
        }

        @Override
        public int getItemCount() {
            return cards.size();
        }

        class VH extends RecyclerView.ViewHolder {
            VH(@NonNull View v) { super(v); }
        }
    }

    private String getYesterdayComparison(String key) {
        if (!isAdded() || getContext() == null) return null;
        switch (key) {
            case "unlock": {
                int today = Prefs.getInt(requireContext(), "ulk_today", 0);
                int yd = Prefs.getInt(requireContext(), "ulk_yesterday", 0);
                if (yd > 0) return buildCompareText("unlocks", today, yd);
                break;
            }
            case "screen": {
                long today = Prefs.getLong(requireContext(), "scr_on_acc", 0) / 60000;
                long yd = Prefs.getLong(requireContext(), "scr_yesterday_on", 0) / 60000;
                if (yd > 0) return buildCompareText("screen time (min)", (int) today, (int) yd);
                break;
            }
            case "steps": {
                long today = Prefs.getLong(requireContext(), "stp_today", 0);
                long yd = Prefs.getLong(requireContext(), "stp_yesterday", 0);
                if (yd > 0) return buildCompareText("steps", (int) today, (int) yd);
                break;
            }
            case "fap": {
                int today = Prefs.getInt(requireContext(), "fap_today", 0);
                int yd = Prefs.getInt(requireContext(), "fap_yesterday", 0);
                if (yd > 0) return buildCompareText("count", today, yd);
                break;
            }
        }
        return null;
    }

    private String buildCompareText(String label, int today, int yesterday) {
        if (yesterday == 0) return null;
        int diff = today - yesterday;
        int pct = Math.abs(diff * 100 / yesterday);
        if (diff < 0) return "↓ " + pct + "% less " + label + " than yesterday 🎉";
        if (diff > 0) return "↑ " + pct + "% more " + label + " than yesterday";
        return "Same " + label + " as yesterday";
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
