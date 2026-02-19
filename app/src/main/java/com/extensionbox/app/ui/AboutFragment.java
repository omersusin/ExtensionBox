package com.extensionbox.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.extensionbox.app.BuildConfig;
import com.extensionbox.app.R;

public class AboutFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inf, ViewGroup parent, Bundle saved) {
        View v = inf.inflate(R.layout.fragment_about, parent, false);

        // Dynamic version from BuildConfig
        TextView tvVersion = v.findViewById(R.id.tvAboutVersion);
        if (tvVersion != null) {
            tvVersion.setText("v" + BuildConfig.VERSION_NAME);
        }

        // Dev card click listeners
        View devCard1 = v.findViewById(R.id.devCard1);
        if (devCard1 != null) {
            devCard1.setOnClickListener(view -> {
                // AI doesn't have a profile link, do nothing
            });
        }

        View devCard2 = v.findViewById(R.id.devCard2);
        if (devCard2 != null) {
            devCard2.setOnClickListener(view -> {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/omersusin"));
                    startActivity(i);
                } catch (Exception ignored) {}
            });
        }

        // Source code link
        View btnSource = v.findViewById(R.id.btnSourceCode);
        if (btnSource != null) {
            btnSource.setOnClickListener(view -> {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/omersusin/ExtensionBox"));
                    startActivity(i);
                } catch (Exception ignored) {}
            });
        }

        return v;
    }
}
