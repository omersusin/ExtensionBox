package com.extensionbox.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.extensionbox.app.R;
import com.google.android.material.card.MaterialCardView;

public class AboutFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inf, ViewGroup parent, Bundle saved) {
        View v = inf.inflate(R.layout.fragment_about, parent, false);

        View devCard1 = v.findViewById(R.id.devCard1);
        if (devCard1 != null) {
            devCard1.setOnClickListener(view -> {

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
