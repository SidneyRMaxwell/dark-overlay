package com.sidmaxwell.darkoverlay;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static final String FILE = "settings";
    static final String KEY_DIM = "dim";            // 0..MAX_DIM percent
    static final String KEY_FORCE_MIN = "forceMin"; // push backlight to minimum
    /** Android 12+ blocks touches through untrusted overlays above 80% opacity. */
    static final int MAX_DIM = 80;
    static final int STEP = 10;

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

    static int clamp(int v) {
        return Math.max(0, Math.min(MAX_DIM, v));
    }
}
