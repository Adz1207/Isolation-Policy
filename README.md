# IsolPolicy

LSPosed module that denies the `useAppZygote` service-spawn path on a user-selected list of packages.

## Background

On Android 10+ a service can declare `android:useAppZygote="true"` together with `android:isolatedProcess="true"`. The platform forks the service from a per-app **App Zygote** rather than the global zygote. Whatever code an app puts in its `ZygotePreload` runs inside the `app_zygote` SELinux domain before the dyntransition to `isolated_app`. That domain is granted `selinux_check_context` and `selinux_check_access` by AOSP's `system/sepolicy/private/app_zygote.te`, so the preload can ask the kernel to validate arbitrary SELinux labels and answer arbitrary access-vector queries via `/sys/fs/selinux/{context,access}`. An untrusted app domain has neither permission.

## What this module does

The hook is on `com.android.server.am.ProcessList#startProcessLocked` inside `system_server`. When the target process is being hosted by App Zygote and its package is in the deny list, the hook reports the process-start request as accepted but skips the actual fork. The caller's `Context.bindIsolatedService(...)` can resolve to `true`, but no service connection arrives. The `ZygotePreload` callback never runs in `app_zygote`.

System packages cannot be added to the deny list. The module's own package is always denied and cannot be unchecked, so the built-in tester can verify that the hook is blocking `useAppZygote` binds for the host.

## Requirements

- Android 10+ (API 29), rooted, with an LSPosed-compatible framework that implements the **modern Xposed API (libxposed 101/102)**.
- Module scope: **System Framework** (`system`). Enable the module in LSPosed, then reboot.

## Build

Requires JDK 17 or newer and the Android SDK (platform 37).

```
chmod +x gradlew
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. The GitHub Actions workflow (`Build and Release`) produces the same artifact.

For a signed release build, set `KEYSTORE_PATH`, `KEYSTORE_PASS` and `KEYPASS` (key alias `isolationpolicy`) and run `./gradlew assembleRelease`.

## libxposed 102 notes

- Dependencies: `compileOnly io.github.libxposed:api:102.0.0` and `implementation io.github.libxposed:service:102.0.0`.
- Module metadata lives in `app/src/main/resources/META-INF/xposed/` (`module.prop`, `scope.list`, `java_init.list`); the legacy `xposed_init` and manifest meta-data are gone.
- Hooks use the interceptor-chain model (`hook(method).intercept(chain -> ...)`) and are installed from `onSystemServerStarting`.
- The deny list is written by the app through the service's remote preferences (group `policy`) and read by the hook inside `system_server`.

## References

- LSPosed/DirtySepolicy: https://github.com/LSPosed/DirtySepolicy
- AOSP `app_zygote.te`: https://android.googlesource.com/platform/system/sepolicy/+/master/private/app_zygote.te

## License

Apache 2.0. See [`LICENSE`](LICENSE).
