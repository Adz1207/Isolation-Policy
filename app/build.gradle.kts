plugins {
    alias(libs.plugins.android.application)
}

val keystorePath: String? = System.getenv("KEYSTORE_PATH")
val keystorePass: String? = System.getenv("KEYSTORE_PASS")
val keyPass: String? = System.getenv("KEYPASS")
val haveReleaseSigning = !keystorePath.isNullOrEmpty() && file(keystorePath).exists()

android {
    namespace = "io.github.mhmrdd.isolationpolicy"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.mhmrdd.isolationpolicy"
        minSdk = 29
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"
    }

    signingConfigs {
        if (haveReleaseSigning) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePass
                keyAlias = "isolationpolicy"
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        aidl = true
    }

    packaging {
        // JANGAN exclude "**": META-INF/xposed/* (java_init.list, module.prop, scope.list)
        // harus ikut masuk ke APK supaya LSPosed mengenali modul.
        resources.excludes += setOf(
            "META-INF/*.version",
            "META-INF/*.kotlin_module",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
            "META-INF/DEPENDENCIES",
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
            "kotlin-tooling-metadata.json",
            "DebugProbesKt.bin"
        )
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    // API hook: disediakan framework saat runtime, jangan di-bundle.
    compileOnly(libs.libxposed.api)
    // Service: komunikasi app <-> framework (status modul & remote preferences).
    implementation(libs.libxposed.service)
    implementation(libs.androidx.recyclerview)
}
