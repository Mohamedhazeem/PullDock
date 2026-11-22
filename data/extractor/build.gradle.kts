plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.pulldock.data.extractor"
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
    // Extractor adapter, provider registry, runtime validation, and update-manifest
    // handling. Python/JS/FFmpeg/process types stay behind the engines boundary.
    api(project(":domain:extractor"))
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":engines:extractor-ytdlp"))
    implementation(project(":engines:media-ffmpeg"))
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
