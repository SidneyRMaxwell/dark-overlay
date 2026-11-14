package com.sidmaxwell.darkoverlay;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

public class MainActivity extends Activity {

    static final String EXTRA_START = "start";

    private TextView permStatus, dimLabel, usageStatus;
    private Button grantBtn, chooseAppsBtn, usageBtn;
    private Switch toggle, perApp;
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
        usageStatus = findViewById(R.id.usageStatus);
        grantBtn = findViewById(R.id.grantBtn);
        chooseAppsBtn = findViewById(R.id.chooseAppsBtn);
        usageBtn = findViewById(R.id.usageBtn);
        toggle = findViewById(R.id.toggle);
        perApp = findViewById(R.id.perApp);
        dimSeek = findViewById(R.id.dimSeek);
        forceMin = findViewById(R.id.forceMin);

        grantBtn.setOnClickListener(v -> openOverlayPermission());
        findViewById(R.id.permSettingsBtn).setOnClickListener(v -> startActivity(new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()))));
        usageBtn.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        chooseAppsBtn.setOnClickListener(v -> chooseApps());

        toggle.setOnCheckedChangeListener((b, on) -> {
            if (updatingUi) return;
            if (on) {
                if (!Settings.canDrawOverlays(this)) {
                    openOverlayPermission();
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

        perApp.setOnCheckedChangeListener((b, on) -> {
            if (updatingUi) return;
            Prefs.setPerApp(this, on);
            refresh();
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

    private void openOverlayPermission() {
        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())));
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

        boolean per = Prefs.perApp(this);
        perApp.setChecked(per);
        chooseAppsBtn.setEnabled(per);
        boolean needUsage = per && !Prefs.hasUsageAccess(this);
        usageStatus.setVisibility(needUsage ? View.VISIBLE : View.GONE);
        usageBtn.setVisibility(needUsage ? View.VISIBLE : View.GONE);
        updateChooseLabel();
        updatingUi = false;
    }

    private void updateChooseLabel() {
        Set<String> chosen = Prefs.apps(this);
        PackageManager pm = getPackageManager();
        List<String> names = new ArrayList<>();
        for (String pkg : chosen) {
            try {
                names.add(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString());
            } catch (PackageManager.NameNotFoundException ignored) {
                // Chosen but not installed (e.g. the regional TikTok default) - skip.
            }
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        chooseAppsBtn.setText(names.isEmpty()
                ? "Choose apps (none chosen)"
                : "Choose apps: " + String.join(", ", names));
    }

    private void chooseApps() {
        chooseAppsBtn.setEnabled(false);
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            TreeMap<String, String> byLabel = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (ResolveInfo ri : pm.queryIntentActivities(launcher, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName())) continue;
                String label = ri.loadLabel(pm).toString();
                byLabel.put(label + "\u0000" + pkg, pkg);
            }
            List<String> labels = new ArrayList<>();
            List<String> pkgs = new ArrayList<>();
            for (java.util.Map.Entry<String, String> en : byLabel.entrySet()) {
                labels.add(en.getKey().substring(0, en.getKey().indexOf('\u0000')));
                pkgs.add(en.getValue());
            }
            runOnUiThread(() -> showPicker(labels, pkgs));
        }).start();
    }

    private void showPicker(List<String> labels, List<String> pkgs) {
        chooseAppsBtn.setEnabled(Prefs.perApp(this));
        if (isFinishing()) return;
        Set<String> chosen = Prefs.apps(this);
        boolean[] checked = new boolean[pkgs.size()];
        for (int i = 0; i < pkgs.size(); i++) checked[i] = chosen.contains(pkgs.get(i));

        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Darken these apps")
                .setMultiChoiceItems(labels.toArray(new String[0]), checked,
                        (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Save", (d, w) -> {
                    Set<String> result = Prefs.apps(this);
                    for (int i = 0; i < pkgs.size(); i++) {
                        if (checked[i]) result.add(pkgs.get(i));
                        else result.remove(pkgs.get(i));
                    }
                    Prefs.setApps(this, result);
                    updateChooseLabel();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
