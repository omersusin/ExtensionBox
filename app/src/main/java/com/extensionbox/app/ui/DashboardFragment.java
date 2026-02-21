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
import android.widget.ImageView;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import com.extensionbox.app.MonitorService;
import com.extensionbox.app.Prefs;
import com.extensionbox.app.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

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
    private int lastPosition = -1;

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
        recyclerView.setPadding(dp(16), dp(16), dp(16), dp(16));
        recyclerView.setClipToPadding(false);
        recyclerView.setAdapter(adapter);
        recyclerView.setItemAnimator(null);

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
            cards.add(new DashCard("info", "Welcome", "Start monitoring to see live data.", null, R.drawable.ic_outline_info));
            adapter.notifyDataSetChanged();
            return;
        }

        List<String> orderedKeys = getSavedOrder();

        for (String key : orderedKeys) {
            LinkedHashMap<String, String> data = MonitorService.getModuleData(key);
            if (data == null || data.isEmpty()) continue;

            String name = ModuleRegistry.nameFor(key);
            int icon = ModuleRegistry.iconResFor(key);
            cards.add(new DashCard(key, name, null, data, icon));
        }

        if (cards.isEmpty()) {
            cards.add(new DashCard("info", "No data", "No active extensions.", null, R.drawable.ic_outline_info));
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
        int iconRes;

        DashCard(String key, String title, String message, LinkedHashMap<String, String> data, int iconRes) {
            this.key = key;
            this.title = title;
            this.message = message;
            this.data = data;
            this.iconRes = iconRes;
        }
    }

    private class DashCardAdapter extends RecyclerView.Adapter<DashCardAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_dash_card, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            DashCard item = cards.get(position);

            TextView title = holder.itemView.findViewById(R.id.tvTitle);
            TextView message = holder.itemView.findViewById(R.id.tvMessage);
            ImageView icon = holder.itemView.findViewById(R.id.imgIcon);
            LinearLayout dataLayout = holder.itemView.findViewById(R.id.layoutData);

            title.setText(item.title);
            icon.setImageResource(item.iconRes);

            dataLayout.removeAllViews();

            if ("info".equals(item.key)) {
                message.setVisibility(View.VISIBLE);
                message.setText(item.message);
                dataLayout.setVisibility(View.GONE);
            } else {
                message.setVisibility(View.GONE);
                dataLayout.setVisibility(View.VISIBLE);

                if (item.data != null) {
                    int shown = 0;
                    for (Map.Entry<String, String> entry : item.data.entrySet()) {
                        if (shown >= 6) break;
                        shown++;

                        LinearLayout row = new LinearLayout(requireContext());
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setPadding(0, dp(4), 0, dp(4));

                        TextView label = new TextView(requireContext());
                        String rawKey = entry.getKey();
                        int dot = rawKey.lastIndexOf('.');
                        String labelText = dot >= 0 ? rawKey.substring(dot + 1) : rawKey;
                        if (labelText.length() > 0) {
                            labelText = labelText.substring(0, 1).toUpperCase()
                                    + labelText.substring(1).replace("_", " ");
                        }
                        label.setText(labelText);
                        label.setTextSize(12);
                        label.setAlpha(0.7f);
                        label.setMaxLines(1);
                        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                        label.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));

                        TextView value = new TextView(requireContext());
                        value.setText(entry.getValue());
                        value.setTextSize(13);
                        value.setTypeface(null, Typeface.BOLD);
                        value.setGravity(android.view.Gravity.END);
                        value.setMaxLines(1);
                        value.setEllipsize(android.text.TextUtils.TruncateAt.END);

                        row.addView(label);
                        row.addView(value);
                        dataLayout.addView(row);
                    }
                }
            }

            setAnimation(holder.itemView, position);
        }

        private void setAnimation(View viewToAnimate, int position) {
            if (position > lastPosition) {
                Animation animation = android.view.animation.AnimationUtils.loadAnimation(requireContext(), android.R.anim.slide_in_left);
                animation.setDuration(300);
                viewToAnimate.startAnimation(animation);
                lastPosition = position;
            }
        }

        @Override
        public void onViewDetachedFromWindow(@NonNull VH holder) {
            holder.itemView.clearAnimation();
        }

        @Override
        public int getItemCount() {
            return cards.size();
        }

        class VH extends RecyclerView.ViewHolder {
            VH(@NonNull View v) { super(v); }
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
