plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.pulldock.data.torrent"
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
    // Torrent adapter: translates libtorrent alerts into typed domain events. Native
    // types stop at :engines:torrent-libtorrent and never reach domain or UI.
    api(project(":domain:torrent"))
    implementation(project(":domain:download"))
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":engines:torrent-libtorrent"))
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
