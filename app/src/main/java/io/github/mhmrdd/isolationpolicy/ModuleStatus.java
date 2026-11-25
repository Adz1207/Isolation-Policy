package io.github.mhmrdd.isolationpolicy;

import androidx.annotation.Nullable;

/**
 * Status koneksi ke framework Xposed. Diisi oleh {@link App} lewat
 * XposedServiceHelper (service libxposed), bukan lagi lewat self-hook.
 */
public final class ModuleStatus {

    public interface OnStatusChangeListener {
        void onStatusChanged(boolean bound);
    }

    static volatile boolean sBound = false;
    @Nullable
    private static volatile OnStatusChangeListener sListener;

    private ModuleStatus() {}

    public static boolean isActive() {
        return sBound;
    }

    public static void setStatusListener(@Nullable OnStatusChangeListener listener) {
        sListener = listener;
    }

    static void notifyStatusChanged(boolean bound) {
        sBound = bound;
        OnStatusChangeListener listener = sListener;
        if (listener != null) {
            listener.onStatusChanged(bound);
        }
    }
}
