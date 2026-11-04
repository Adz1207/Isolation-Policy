package io.github.mhmrdd.isolationpolicy;

/**
 * Status koneksi ke framework Xposed. Diisi oleh {@link App} lewat
 * XposedServiceHelper (service libxposed), bukan lagi lewat self-hook.
 */
public final class ModuleStatus {

    static volatile boolean sBound = false;

    private ModuleStatus() {}

    public static boolean isActive() {
        return sBound;
    }
}
