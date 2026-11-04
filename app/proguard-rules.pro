# libxposed API 102 (aturan resmi dari README libxposed/api)
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# Dipanggil lewat nama dari manifest / framework
-keep class io.github.mhmrdd.isolationpolicy.AppZygote { *; }
-keep class io.github.mhmrdd.isolationpolicy.IsolationPolicyService { *; }
-keep class io.github.mhmrdd.isolationpolicy.IIsolationPolicyService { *; }
-keep class io.github.mhmrdd.isolationpolicy.IIsolationPolicyService$* { *; }

-repackageclasses "isolationpolicy"
-allowaccessmodification
-overloadaggressively
