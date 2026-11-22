plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // Domain is the innermost ring: only shared primitives and cross-cutting Kotlin
    // utilities. No Android, no Room, no DataStore, no engines, no Compose.
    api(project(":core:model"))
    api(project(":core:common"))
    api(libs.kotlinx.coroutines.core)
    // The unified queue aggregates both engine contracts; download domain depends on
    // the engine domain modules (domain -> domain only, never engines).
    api(project(":domain:torrent"))
    api(project(":domain:extractor"))
    implementation(libs.javax.inject)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
