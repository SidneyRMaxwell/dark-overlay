package com.sidmaxwell.darkoverlay;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {

    static final String EXTRA_START = "start";

    private TextView permStatus, dimLabel;
    private Button grantBtn;
    private Switch toggle;
    private SeekBar dimSeek;
    private CheckBox forceMin;
    private boolean updatingUi;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        permStatus = findViewById(R.id.permStatus);
        dimLabel = findViewById(R.id.dimLabel);
        grantBtn = findViewById(R.id.grantBtn);
        toggle = findViewById(R.id.toggle);
        dimSeek = findViewById(R.id.dimSeek);
        forceMin = findViewById(R.id.forceMin);

        grantBtn.setOnClickListener(v -> startActivity(new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()))));

        toggle.setOnCheckedChangeListener((b, on) -> {
            if (updatingUi) return;
            if (on) {
                if (!Settings.canDrawOverlays(this)) {
                    grantBtn.performClick();
                } else {
                    OverlayService.start(this);
                }
            } else {
                OverlayService.stop(this);
            }
            // Service state flips asynchronously; re-read shortly after.
            handler.postDelayed(this::refresh, 300);
        });

        dimSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                dimLabel.setText("Darkness: " + p + "%");
                if (fromUser) Prefs.setDim(MainActivity.this, p);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });

        forceMin.setOnCheckedChangeListener((b, on) -> {
            if (!updatingUi) Prefs.setForceMin(this, on);
        });

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }

        handleStartExtra(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleStartExtra(intent);
    }

    private void handleStartExtra(Intent i) {
        if (i != null && i.getBooleanExtra(EXTRA_START, false) && Settings.canDrawOverlays(this)) {
            i.removeExtra(EXTRA_START);
            OverlayService.start(this);
            handler.postDelayed(this::refresh, 300);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        updatingUi = true;
        boolean granted = Settings.canDrawOverlays(this);
        permStatus.setVisibility(granted ? View.GONE : View.VISIBLE);
        grantBtn.setVisibility(granted ? View.GONE : View.VISIBLE);
        toggle.setChecked(OverlayService.running);
        int d = Prefs.dim(this);
        dimSeek.setProgress(d);
        dimLabel.setText("Darkness: " + d + "%");
        forceMin.setChecked(Prefs.forceMin(this));
        updatingUi = false;
    }
}
