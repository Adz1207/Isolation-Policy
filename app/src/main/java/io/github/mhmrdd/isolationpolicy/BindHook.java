package io.github.mhmrdd.isolationpolicy;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * Hook ProcessList#startProcessLocked di system_server.
 * Menggunakan caching refleksi dan volatile set untuk performa jalur panas (*hot path*).
 */
@SuppressLint({"PrivateApi", "DiscouragedPrivateApi"})
public final class BindHook {

    private static final String HOSTING_RECORD = "com.android.server.am.HostingRecord";
    private static final String PROCESS_RECORD = "com.android.server.am.ProcessRecord";

    private static volatile XposedModule sModule;
    private static volatile SharedPreferences sPrefs;
    private static volatile Set<String> sCachedDenied = Collections.emptySet();
    private static SharedPreferences.OnSharedPreferenceChangeListener sPrefsListener;

    private static volatile Method sUsesAppZygoteMethod;
    private static volatile Field sHostingZygoteField;
    private static volatile Field sInfoField;

    private BindHook() {}

    static void install(@NonNull XposedModule module, @NonNull ClassLoader cl) {
        sModule = module;
        sPrefs = openPrefs();
        initPrefsListener(sPrefs);
        reloadCachedDenied();

        try {
            Class<?> processList = cl.loadClass("com.android.server.am.ProcessList");
            int count = 0;
            for (Method method : processList.getDeclaredMethods()) {
                if ("startProcessLocked".equals(method.getName())
                        && method.getReturnType() == boolean.class) {
                    try {
                        module.hook(method).intercept(new StartProcessLockedHooker());
                        count++;
                    } catch (Throwable t) {
                        Logger.e("Failed to hook " + method, t);
                    }
                }
            }
            Logger.i("BindHook installed on ProcessList.startProcessLocked count=" + count + " on SDK " + Build.VERSION.SDK_INT);
        } catch (Throwable t) {
            Logger.e("BindHook install failed", t);
        }
    }

    private static synchronized void initPrefsListener(SharedPreferences prefs) {
        if (prefs == null || sPrefsListener != null) return;
        try {
            sPrefsListener = (sharedPreferences, key) -> {
                if (Constants.KEY_DENIED.equals(key)) {
                    reloadCachedDenied();
                }
            };
            prefs.registerOnSharedPreferenceChangeListener(sPrefsListener);
        } catch (Throwable t) {
            Logger.e("Failed to register preferences listener", t);
        }
    }

    private static void reloadCachedDenied() {
        SharedPreferences prefs = sPrefs;
        if (prefs == null) {
            prefs = openPrefs();
            sPrefs = prefs;
        }
        if (prefs == null) {
            sCachedDenied = Collections.emptySet();
            return;
        }
        try {
            Set<String> denied = prefs.getStringSet(Constants.KEY_DENIED, null);
            if (denied != null) {
                sCachedDenied = new HashSet<>(denied);
            } else {
                sCachedDenied = Collections.emptySet();
            }
        } catch (Throwable t) {
            Logger.e("Failed to load cached denied policy", t);
            sCachedDenied = Collections.emptySet();
        }
    }

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

        if (isSystemPackage(pr)) return null;

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

    private static boolean isSystemPackage(Object processRecord) {
        try {
            Object info = getProcessInfoCached(processRecord);
            if (info instanceof ApplicationInfo appInfo) {
                return Constants.isSystemPackage(appInfo.uid, appInfo.flags);
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    @Nullable
    private static String resolvePackage(Object processRecord) {
        try {
            Object info = getProcessInfoCached(processRecord);
            if (info instanceof ApplicationInfo appInfo) {
                String s = appInfo.packageName;
                if (s != null && !s.isEmpty()) return s;
            } else if (info != null) {
                Object pkg = getField(info, "packageName");
                if (pkg instanceof String s) {
                    if (!s.isEmpty()) return s;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean usesAppZygote(Object hostingRecord) {
        try {
            Method m = getUsesAppZygoteMethodCached(hostingRecord.getClass());
            if (m != null) {
                Object result = m.invoke(hostingRecord);
                if (result instanceof Boolean) {
                    return (Boolean) result;
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            Field f = getHostingZygoteFieldCached(hostingRecord.getClass());
            if (f != null) {
                return f.getInt(hostingRecord) == 2;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean isDenied(String pkg) {
        if (pkg == null) return false;
        if (Constants.APPLICATION_ID.equals(pkg)) return true;

        if (sPrefs == null || sPrefsListener == null) {
            reloadCachedDenied();
            initPrefsListener(sPrefs);
        }

        return sCachedDenied.contains(pkg);
    }

    @Nullable
    private static Method getUsesAppZygoteMethodCached(Class<?> clazz) {
        Method m = sUsesAppZygoteMethod;
        if (m == null) {
            synchronized (BindHook.class) {
                m = sUsesAppZygoteMethod;
                if (m == null) {
                    try {
                        m = clazz.getDeclaredMethod("usesAppZygote");
                        m.setAccessible(true);
                        sUsesAppZygoteMethod = m;
                    } catch (Throwable ignored) {}
                }
            }
        }
        return m;
    }

    @Nullable
    private static Field getHostingZygoteFieldCached(Class<?> clazz) {
        Field f = sHostingZygoteField;
        if (f == null) {
            synchronized (BindHook.class) {
                f = sHostingZygoteField;
                if (f == null) {
                    try {
                        f = clazz.getDeclaredField("mHostingZygote");
                        f.setAccessible(true);
                        sHostingZygoteField = f;
                    } catch (Throwable ignored) {}
                }
            }
        }
        return f;
    }

    @Nullable
    private static Object getProcessInfoCached(Object processRecord) {
        if (processRecord == null) return null;
        try {
            Field f = sInfoField;
            if (f == null) {
                synchronized (BindHook.class) {
                    f = sInfoField;
                    if (f == null) {
                        f = processRecord.getClass().getDeclaredField("info");
                        f.setAccessible(true);
                        sInfoField = f;
                    }
                }
            }
            return f.get(processRecord);
        } catch (Throwable ignored) {}
        return getField(processRecord, "info");
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
