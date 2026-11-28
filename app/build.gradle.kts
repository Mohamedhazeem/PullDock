import java.util.Properties
import com.android.build.api.variant.FilterConfiguration

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.chaquopy)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.pulldock"
    compileSdk = 37

    // Release signing is read from a gitignored keystore.properties so no secret is
    // committed. Without it the release build stays unsigned (CI can inject its own).
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties().apply {
        if (keystorePropertiesFile.exists()) {
            keystorePropertiesFile.inputStream().use { load(it) }
        }
    }
    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.pulldock"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "0.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // PullDock ships 64-bit only (spec FR-076): arm64-v8a and x86_64. 32-bit ABIs
        // must never be packaged; CI verifies native ABI packaging (T019).
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    // Per-ABI splits keep engine-native APKs lean; a universal 64-bit APK stays
    // available for sideloaded distribution (v1 distribution is private/sideloaded).
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                null
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// Release APK file names start with "pulldock" (per-ABI + universal).
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters
                .firstOrNull { it.filterType == FilterConfiguration.FilterType.ABI }
                ?.identifier
            output.outputFileName.set(
                if (abi != null) {
                    "pulldock-$abi-${variant.buildType}.apk"
                } else {
                    "pulldock-universal-${variant.buildType}.apk"
                },
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Pinned extractor runtime (ADR-0012): built into signed releases at build time.
// No executable code is downloaded or run at app runtime.
chaquopy {
    defaultConfig {
        version = "3.12" // build machine has Python 3.12; 64-bit only, matching abiFilters
        pip {
            install("yt-dlp==${libs.versions.yt.dlp.get()}")
            // HLS fallback downloader (pure Python; pulls cryptography from the Chaquopy
            // repo and httpx from PyPI). Used by the extractor fallback layer (ADR-0013).
            install("hlsfetch==${libs.versions.hlsfetch.get()}")
        }
    }
}

dependencies {
    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.navigation.compose)

    // Kotlin coroutines
    implementation(libs.kotlinx.coroutines.android)

    // PullDock modules (:app is the composition root and wires the whole graph)
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:storage"))
    implementation(project(":core:network"))
    implementation(project(":domain:download"))
    implementation(project(":domain:torrent"))
    implementation(project(":domain:extractor"))
    implementation(project(":domain:discovery"))
    implementation(project(":data:download"))
    implementation(project(":data:torrent"))
    implementation(project(":data:extractor"))
    implementation(project(":data:discovery"))
    implementation(project(":engines:torrent-libtorrent"))
    implementation(project(":engines:extractor-ytdlp"))
    implementation(project(":engines:media-ffmpeg"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:home"))
    implementation(project(":feature:add"))
    implementation(project(":feature:queue"))
    implementation(project(":feature:download-detail"))
    implementation(project(":feature:library"))
    implementation(project(":feature:player"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:legal"))
    implementation(project(":feature:discovery"))

    // Dependency injection (Hilt)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    // Persistence (Room + DataStore)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Media playback (Media3)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    // HLS remux (extractor fallback layer, ADR-0013): ffmpeg-kit maintained fork, LGPL-3.0.
    implementation(libs.ffmpeg.kit.full)

    // Unit testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented testing
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
