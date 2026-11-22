plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.pulldock.engines.extractor"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // The Chaquopy CPython runtime + pinned yt-dlp dependency is intentionally NOT
    // declared here. T080 runs the runtime feasibility PoC (Python/JS/yt-dlp
    // combination, ABI, license) before pinning; see ADR-0002 and AGENTS.md §17.
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
