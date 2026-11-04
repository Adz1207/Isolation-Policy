package io.github.mhmrdd.isolationpolicy;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/**
 * Mendaftarkan listener service libxposed. Catatan: callback bisa datang saat Direct Boot
 * (sebelum unlock), jadi semua akses penyimpanan di sini dibungkus try/catch.
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override
            public void onServiceBind(@NonNull XposedService service) {
                ModuleStatus.sBound = true;
                try {
                    SharedPreferences remote = service.getRemotePreferences(Constants.PREFS_NAME);
                    PolicyStore.setRemote(remote);
                    PolicyStore.sync(App.this);
                } catch (Throwable t) {
                    Log.w(Constants.TAG, "remote preferences not available: " + t);
                }
            }

            @Override
            public void onServiceDied(@NonNull XposedService service) {
                ModuleStatus.sBound = false;
                PolicyStore.setRemote(null);
            }
        });
    }
}
