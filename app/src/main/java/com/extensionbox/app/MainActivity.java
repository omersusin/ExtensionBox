package com.extensionbox.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.extensionbox.app.ui.DashboardFragment;
import com.extensionbox.app.ui.ExtensionsFragment;
import com.extensionbox.app.ui.SettingsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private Fragment dashFrag, extFrag, setFrag, activeFrag;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestPerms();

        dashFrag = new DashboardFragment();
        extFrag = new ExtensionsFragment();
        setFrag = new SettingsFragment();

        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragmentContainer, dashFrag, "dash")
                .add(R.id.fragmentContainer, extFrag, "ext")
                .add(R.id.fragmentContainer, setFrag, "set")
                .hide(extFrag)
                .hide(setFrag)
                .commit();
        activeFrag = dashFrag;

        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setOnItemSelectedListener(item -> {
            Fragment target;
            int id = item.getItemId();
            if (id == R.id.nav_dashboard) target = dashFrag;
            else if (id == R.id.nav_extensions) target = extFrag;
            else if (id == R.id.nav_settings) target = setFrag;
            else return false;

            getSupportFragmentManager().beginTransaction()
                    .hide(activeFrag)
                    .show(target)
                    .commit();
            activeFrag = target;
            return true;
        });
    }

    private void requestPerms() {
        List<String> perms = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (Build.VERSION.SDK_INT >= 29) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                    != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }
        if (!perms.isEmpty())
            ActivityCompat.requestPermissions(this, perms.toArray(new String[0]), 100);
    }
}
