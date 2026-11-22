plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.pulldock.core.network"
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
    implementation(project(":core:common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)

    // ConnectivityMonitor uses framework ConnectivityManager (no artifact needed).
    // The UIDT JobScheduler wrapper (T107) also uses framework android.app.job APIs;
    // androidx.work arrives with the WorkManager deferrable jobs task (T109).
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
