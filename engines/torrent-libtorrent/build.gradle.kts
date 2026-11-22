plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.pulldock.engines.torrent"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            // Keep bundled engine libraries whole; stripOnly can break JNI resolution.
            useLegacyPackaging = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // T060 (partial): libtorrent4j 2.1.0-39 pinned. Verified statically: MIT wrapper
    // license, Android artifacts ship lib/<abi>/libtorrent4j.so for arm64-v8a and
    // x86_64 only (32-bit ABIs never packaged, FR-076). The on-device init/transfer
    // PoC remains an open verification task (see research.md D1).
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
    api(libs.libtorrent4j.core)
    implementation(libs.libtorrent4j.arm64)
    implementation(libs.libtorrent4j.x8664)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
