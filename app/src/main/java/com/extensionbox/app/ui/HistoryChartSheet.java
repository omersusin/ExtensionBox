package com.extensionbox.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.extensionbox.app.HistoryHelper;
import com.extensionbox.app.R;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

public class HistoryChartSheet extends BottomSheetDialogFragment {

    private static final String ARG_KEY = "module_key";
    private static final String ARG_FIELD = "field";
    private static final String ARG_LABEL = "label";

    public static HistoryChartSheet newInstance(String moduleKey, String field, String label) {
        HistoryChartSheet sheet = new HistoryChartSheet();
        Bundle args = new Bundle();
        args.putString(ARG_KEY, moduleKey);
        args.putString(ARG_FIELD, field);
        args.putString(ARG_LABEL, label);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_history_chart, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        if (args == null) return;

        String moduleKey = args.getString(ARG_KEY, "");
        String field = args.getString(ARG_FIELD, "");
        String label = args.getString(ARG_LABEL, "History");

        TextView tvTitle = view.findViewById(R.id.chartTitle);
        tvTitle.setText(label + " — Last 24h");

        LineChart chart = view.findViewById(R.id.lineChart);
        loadChart(chart, moduleKey, field, label);
    }

    private void loadChart(LineChart chart, String moduleKey, String field, String label) {
        List<long[]> raw = HistoryHelper.get(requireContext()).query(moduleKey, field, 24);

        if (raw.isEmpty()) {
            chart.setNoDataText("No history yet. Data will appear after monitoring runs for a while.");
            chart.invalidate();
            return;
        }

        long firstTs = raw.get(0)[0];
        List<Entry> entries = new ArrayList<>();
        for (long[] point : raw) {
            float xMinutes = (point[0] - firstTs) / 60000f;
            float y = point[1];
            entries.add(new Entry(xMinutes, y));
        }

        LineDataSet dataSet = new LineDataSet(entries, label);
        dataSet.setColor(0xFF6200EE);
        dataSet.setCircleColor(0xFF6200EE);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(3f);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        chart.setData(new LineData(dataSet));
        chart.getDescription().setEnabled(false);
        chart.getXAxis().setDrawGridLines(false);
        chart.getAxisRight().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.animateX(500);
        chart.invalidate();
    }
}
