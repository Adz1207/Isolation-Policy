package io.github.mhmrdd.isolationpolicy;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

/**
 * Penyimpanan daftar tolak.
 *
 * - Salinan lokal (SharedPreferences biasa) adalah sumber kebenaran di sisi aplikasi.
 * - Salinan remote (libxposed remote preferences) dibaca oleh hook di system_server.
 *   Remote hanya tersedia selama service framework terhubung.
 */
final class PolicyStore {

    private static final String LOCAL_NAME = "policy_local";

    private static volatile SharedPreferences sRemote;

    private PolicyStore() {}

    static void setRemote(SharedPreferences remote) {
        sRemote = remote;
    }

    static boolean hasRemote() {
        return sRemote != null;
    }

    static Set<String> load(Context ctx) {
        Set<String> out = new HashSet<String>();
        Set<String> stored = null;
        try {
            stored = local(ctx).getStringSet(Constants.KEY_DENIED, null);
        } catch (Throwable t) {
            Log.w(Constants.TAG, "local policy unavailable: " + t);
        }
        if (stored == null) {
            SharedPreferences r = sRemote;
            if (r != null) {
                try { stored = r.getStringSet(Constants.KEY_DENIED, null); } catch (Throwable ignored) {}
            }
        }
        if (stored != null) out.addAll(stored);
        out.add(Constants.APPLICATION_ID);
        return out;
    }

    static void save(Context ctx, Set<String> denied) {
        Set<String> copy = new HashSet<String>(denied);
        copy.add(Constants.APPLICATION_ID);
        try {
            local(ctx).edit().putStringSet(Constants.KEY_DENIED, copy).apply();
        } catch (Throwable t) {
            Log.w(Constants.TAG, "failed to write local policy: " + t);
        }
        pushRemote(copy);
        if (BuildConfig.DEBUG) {
            Log.i(Constants.TAG, "wrote policy size=" + copy.size() + " remote=" + hasRemote());
        }
    }

    /** Dorong salinan lokal ke remote (dipanggil saat service framework baru terhubung). */
    static void sync(Context ctx) {
        pushRemote(load(ctx));
    }

    private static void pushRemote(Set<String> set) {
        SharedPreferences r = sRemote;
        if (r == null) return;
        try {
            r.edit().putStringSet(Constants.KEY_DENIED, new HashSet<String>(set)).apply();
        } catch (Throwable t) {
            Log.w(Constants.TAG, "failed to write remote policy: " + t);
        }
    }

    private static SharedPreferences local(Context ctx) {
        return ctx.getSharedPreferences(LOCAL_NAME, Context.MODE_PRIVATE);
    }
}
