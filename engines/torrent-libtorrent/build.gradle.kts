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
    // The libtorrent4j dependency is intentionally NOT declared here. T060 runs the
    // feasibility PoC (version, minSdk, ABI verification) before any engine dependency
    // is pinned; see ADR-0001 and the pinned-distribution policy (AGENTS.md §17).
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
