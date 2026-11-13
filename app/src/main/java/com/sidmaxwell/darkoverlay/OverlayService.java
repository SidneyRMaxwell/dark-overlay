package com.sidmaxwell.darkoverlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.service.quicksettings.TileService;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

public class OverlayService extends Service
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    static final String ACTION_STOP = "com.sidmaxwell.darkoverlay.STOP";
    static final String ACTION_DARKER = "com.sidmaxwell.darkoverlay.DARKER";
    static final String ACTION_LIGHTER = "com.sidmaxwell.darkoverlay.LIGHTER";

    private static final String CHANNEL = "overlay";
    private static final int NOTIF_ID = 1;

    static volatile boolean running;

    private WindowManager wm;
    private View overlay;
    private WindowManager.LayoutParams lp;

    static void start(Context c) {
        c.startForegroundService(new Intent(c, OverlayService.class));
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, OverlayService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }

        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        running = true;
        addOverlay();
        Prefs.get(this).registerOnSharedPreferenceChangeListener(this);
        refreshTile();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopSelf();
        } else if (ACTION_DARKER.equals(action)) {
            Prefs.setDim(this, Prefs.dim(this) + Prefs.STEP);
        } else if (ACTION_LIGHTER.equals(action)) {
            Prefs.setDim(this, Prefs.dim(this) - Prefs.STEP);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Prefs.get(this).unregisterOnSharedPreferenceChangeListener(this);
        if (overlay != null) {
            try {
                wm.removeView(overlay);
            } catch (Exception ignored) {
            }
            overlay = null;
        }
        running = false;
        refreshTile();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        applySettings();
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .notify(NOTIF_ID, buildNotification());
    }

    private void addOverlay() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        // Square big enough to cover the screen in either orientation, including bars and cutouts.
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        int size = Math.max(dm.widthPixels, dm.heightPixels) + 400;

        lp = new WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = -200;
        lp.y = -200;
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            lp.setFitInsetsTypes(0);
        }

        overlay = new View(this);
        overlay.setBackgroundColor(Color.BLACK);
        overlay.setFocusable(false);
        overlay.setClickable(false);

        setParams();
        wm.addView(overlay, lp);
    }

    private void applySettings() {
        if (overlay == null) return;
        setParams();
        try {
            wm.updateViewLayout(overlay, lp);
        } catch (Exception ignored) {
        }
    }

    private void setParams() {
        // Window alpha (not the view colour) is what Android 12+ measures for touch pass-through;
        // keep it just under the 0.8 limit so taps still reach the app underneath.
        lp.alpha = Math.min(Prefs.dim(this) / 100f, 0.79f);
        // A brightness override on our top-most window competes with the one the app sets.
        lp.screenBrightness = Prefs.forceMin(this)
                ? 0.01f
                : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
    }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL, "Dark overlay", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Icon icon = Icon.createWithResource(this, R.drawable.ic_moon);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_moon)
                .setContentTitle("Dark overlay on")
                .setContentText("Darkness " + Prefs.dim(this) + "%")
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(icon, "Lighter", action(ACTION_LIGHTER, 1)).build())
                .addAction(new Notification.Action.Builder(icon, "Darker", action(ACTION_DARKER, 2)).build())
                .addAction(new Notification.Action.Builder(icon, "Off", action(ACTION_STOP, 3)).build())
                .build();
    }

    private PendingIntent action(String a, int req) {
        Intent i = new Intent(this, OverlayService.class).setAction(a);
        return PendingIntent.getService(this, req, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void refreshTile() {
        TileService.requestListeningState(this, new ComponentName(this, DimTileService.class));
    }
}
