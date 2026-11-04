package io.github.mhmrdd.isolationpolicy;

import android.util.Log;

import androidx.annotation.Nullable;

import io.github.libxposed.api.XposedInterface;

/**
 * Logger tunggal. Selalu menulis ke logcat; bila modul sedang berjalan di dalam
 * proses ter-hook (system_server), log juga dikirim ke log Xposed lewat
 * {@link XposedInterface#log(int, String, String)} (libxposed API 101+).
 */
public final class Logger {

    @Nullable
    private static volatile XposedInterface sXposed;

    private Logger() {}

    /** Dipanggil dari Entry saat modul dimuat. */
    static void attach(@Nullable XposedInterface xposed) {
        sXposed = xposed;
    }

    public static void i(String msg) {
        Log.i(Constants.TAG, msg);
        XposedInterface x = sXposed;
        if (x != null) {
            try { x.log(Log.INFO, Constants.TAG, msg); } catch (Throwable ignored) {}
        }
    }

    public static void w(String msg) {
        Log.w(Constants.TAG, msg);
        XposedInterface x = sXposed;
        if (x != null) {
            try { x.log(Log.WARN, Constants.TAG, msg); } catch (Throwable ignored) {}
        }
    }

    public static void e(String msg, Throwable t) {
        Log.e(Constants.TAG, msg, t);
        XposedInterface x = sXposed;
        if (x != null) {
            try { x.log(Log.ERROR, Constants.TAG, msg, t); } catch (Throwable ignored) {}
        }
    }
}
