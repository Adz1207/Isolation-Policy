package io.github.mhmrdd.isolationpolicy;

import android.content.pm.ApplicationInfo;

public final class Constants {
    public static final String TAG = "IsolPolicy";
    public static final String APPLICATION_ID = "io.github.mhmrdd.isolationpolicy";

    public static final String PREFS_NAME = "policy";
    public static final String KEY_DENIED = "denied";

    public static boolean isSystemPackage(int uid, int flags) {
        return uid < 10000
                || ((flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    && (flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0);
    }

    private Constants() {}
}
