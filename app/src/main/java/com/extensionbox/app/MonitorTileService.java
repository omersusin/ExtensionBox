package com.extensionbox.app;

import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.N)
public class MonitorTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        syncTileState();
    }

    @Override
    public void onClick() {
        super.onClick();
        boolean running = Prefs.isRunning(getApplicationContext());
        if (running) {

            Intent stop = new Intent(getApplicationContext(), MonitorService.class);
            stop.setAction(MonitorService.ACTION_STOP);
            startService(stop);
        } else {

            Intent start = new Intent(getApplicationContext(), MonitorService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(start);
            } else {
                startService(start);
            }
        }

        getQsTile().setState(running ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
        getQsTile().updateTile();
    }

    private void syncTileState() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean running = Prefs.isRunning(getApplicationContext());
        tile.setState(running ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel("Extension Box");
        tile.setSubtitle(running ? "Monitoring" : "Paused");
        tile.updateTile();
    }
}
