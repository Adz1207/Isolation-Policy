package io.github.mhmrdd.isolationpolicy;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * Hook ProcessList#startProcessLocked di system_server.
 * Jika proses dihosting App Zygote dan paketnya ada di daftar tolak, permintaan
 * dilaporkan "berhasil" tanpa melakukan fork.
 */
public final class BindHook {

    private static final String HOSTING_RECORD = "com.android.server.am.HostingRecord";
    private static final String PROCESS_RECORD = "com.android.server.am.ProcessRecord";

    private static volatile XposedModule sModule;
    private static volatile SharedPreferences sPrefs;

    private BindHook() {}

    static void install(@NonNull XposedModule module, @NonNull ClassLoader cl) {
        sModule = module;
        sPrefs = openPrefs();

        try {
            Class<?> processList = cl.loadClass("com.android.server.am.ProcessList");
            int count = 0;
            for (Method method : processList.getDeclaredMethods()) {
                if ("startProcessLocked".equals(method.getName())
                        && method.getReturnType() == boolean.class) {
                    try {
                        // API 101+: hook(...) mengembalikan builder, lalu intercept(Hooker)
                        module.hook(method).intercept(new StartProcessLockedHooker());
                        count++;
                    } catch (Throwable t) {
                        Logger.e("Failed to hook " + method, t);
                    }
                }
            }
            Logger.i("BindHook installed on ProcessList.startProcessLocked count=" + count);
        } catch (Throwable t) {
            Logger.e("BindHook install failed", t);
        }
    }

    /** Model interceptor-chain libxposed: tanpa proceed() berarti method asli dilewati. */
    public static final class StartProcessLockedHooker implements XposedInterface.Hooker {
        @Override
        public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
            String denied = null;
            try {
                denied = deniedPackage(chain.getArgs());
            } catch (Throwable t) {
                Logger.e("policy check failed", t);
            }
            if (denied != null) {
                Logger.i("accepted startProcessLocked without app zygote fork for " + denied);
                return Boolean.TRUE;
            }
            return chain.proceed();
        }
    }

    /** @return nama paket yang harus ditolak, atau null bila proses boleh lanjut. */
    @Nullable
    private static String deniedPackage(@NonNull List<Object> args) {
        Object hr = null;
        Object pr = null;
        for (Object arg : args) {
            if (arg == null) continue;
            String cn = arg.getClass().getName();
            if (hr == null && HOSTING_RECORD.equals(cn)) {
                hr = arg;
            } else if (pr == null && PROCESS_RECORD.equals(cn)) {
                pr = arg;
            }
        }
        if (hr == null || pr == null) return null;
        if (!usesAppZygote(hr)) return null;

        String pkg = resolvePackage(pr);
        if (pkg == null) return null;
        return isDenied(pkg) ? pkg : null;
    }

    @Nullable
    private static SharedPreferences openPrefs() {
        XposedModule m = sModule;
        if (m == null) return null;
        try {
            return m.getRemotePreferences(Constants.PREFS_NAME);
        } catch (Throwable t) {
            Logger.e("Failed to initialize remote preferences", t);
            return null;
        }
    }

    @Nullable
    private static String resolvePackage(Object processRecord) {
        try {
            Object info = getField(processRecord, "info");
            if (info == null) return null;
            Object pkg = getField(info, "packageName");
            if (pkg instanceof String) {
                String s = (String) pkg;
                if (!s.isEmpty()) return s;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean usesAppZygote(Object hostingRecord) {
        try {
            Method m = hostingRecord.getClass().getDeclaredMethod("usesAppZygote");
            m.setAccessible(true);
            Object result = m.invoke(hostingRecord);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
        } catch (Throwable ignored) {
        }

        try {
            Field f = hostingRecord.getClass().getDeclaredField("mHostingZygote");
            f.setAccessible(true);
            return f.getInt(hostingRecord) == 2;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean isDenied(String pkg) {
        if (pkg == null) return false;
        if (Constants.APPLICATION_ID.equals(pkg)) return true;

        SharedPreferences prefs = sPrefs;
        if (prefs == null) {
            prefs = openPrefs();
            sPrefs = prefs;
        }
        if (prefs == null) {
            if (BuildConfig.DEBUG) {
                Logger.i("remote preferences unavailable, cannot check pkg=" + pkg);
            }
            return false;
        }

        Set<String> denied = prefs.getStringSet(Constants.KEY_DENIED, Collections.<String>emptySet());
        if (BuildConfig.DEBUG) {
            Logger.i("policy size=" + (denied != null ? denied.size() : 0) + " checking pkg=" + pkg);
        }
        return denied != null && denied.contains(pkg);
    }

    @Nullable
    private static Object getField(Object target, String fieldName) {
        if (target == null) return null;
        Class<?> current = target.getClass();
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                break;
            }
        }
        return null;
    }
}
