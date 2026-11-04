package io.github.mhmrdd.isolationpolicy;

import android.util.Log;

import androidx.annotation.NonNull;

import io.github.libxposed.api.XposedModule;

/**
 * Entry point modul (libxposed API 102).
 *
 * Didaftarkan lewat META-INF/xposed/java_init.list. Scope modul adalah virtual
 * package "system" (system_server) yang ditulis di META-INF/xposed/scope.list.
 */
public class Entry extends XposedModule {

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        Logger.attach(this);
        Logger.i("module loaded in process=" + param.getProcessName()
                + " systemServer=" + param.isSystemServer()
                + " api=" + getApiVersion());
    }

    @Override
    public void onSystemServerStarting(@NonNull SystemServerStartingParam param) {
        try {
            BindHook.install(this, param.getClassLoader());
        } catch (Throwable t) {
            log(Log.ERROR, Constants.TAG, "BindHook.install failed", t);
        }
    }
}
