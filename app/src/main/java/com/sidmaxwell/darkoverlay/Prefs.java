package com.sidmaxwell.darkoverlay;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Process;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

final class Prefs {
    static final String FILE = "settings";
    static final String KEY_DIM = "dim";            // 0..MAX_DIM percent
    static final String KEY_FORCE_MIN = "forceMin"; // push backlight to minimum
    static final String KEY_PER_APP = "perApp";     // only dim inside chosen apps
    static final String KEY_APPS = "apps";          // chosen package names
    /** Android 12+ blocks touches through untrusted overlays above 80% opacity. */
    static final int MAX_DIM = 80;
    static final int STEP = 10;

    /** TikTok's package names (global + some regions), selected by default. */
    private static final Set<String> DEFAULT_APPS = new HashSet<>(Arrays.asList(
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill"));

    private Prefs() {}

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static int dim(Context c) {
        return clamp(get(c).getInt(KEY_DIM, 60));
    }

    static void setDim(Context c, int v) {
        get(c).edit().putInt(KEY_DIM, clamp(v)).apply();
    }

    static boolean forceMin(Context c) {
        return get(c).getBoolean(KEY_FORCE_MIN, true);
    }

    static void setForceMin(Context c, boolean v) {
        get(c).edit().putBoolean(KEY_FORCE_MIN, v).apply();
    }

    static boolean perApp(Context c) {
        return get(c).getBoolean(KEY_PER_APP, true);
    }

    static void setPerApp(Context c, boolean v) {
        get(c).edit().putBoolean(KEY_PER_APP, v).apply();
    }

    static Set<String> apps(Context c) {
        return new HashSet<>(get(c).getStringSet(KEY_APPS, DEFAULT_APPS));
    }

    static void setApps(Context c, Set<String> apps) {
        get(c).edit().putStringSet(KEY_APPS, new HashSet<>(apps)).apply();
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(MAX_DIM, v));
    }

    @SuppressWarnings("deprecation")
    static boolean hasUsageAccess(Context c) {
        AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        int mode = Build.VERSION.SDK_INT >= 29
                ? ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName())
                : ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }
}
