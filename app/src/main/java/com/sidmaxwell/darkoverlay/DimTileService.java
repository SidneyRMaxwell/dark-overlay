package com.sidmaxwell.darkoverlay;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class DimTileService extends TileService {

    @Override
    public void onStartListening() {
        update(OverlayService.running);
    }

    @Override
    public void onClick() {
        if (OverlayService.running) {
            OverlayService.stop(this);
            update(false);
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            openApp(false);
            return;
        }
        try {
            OverlayService.start(this);
            update(true);
        } catch (Exception e) {
            // Some Android versions refuse background service starts from a tile; go via the app.
            openApp(true);
        }
    }

    @SuppressWarnings("deprecation")
    private void openApp(boolean autoStart) {
        Intent i = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_START, autoStart);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            startActivityAndCollapse(i);
        }
    }

    private void update(boolean on) {
        Tile t = getQsTile();
        if (t == null) return;
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.updateTile();
    }
}
