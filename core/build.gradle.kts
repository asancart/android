import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin: domain rules with no Android APIs, so they run as plain JVM unit tests
// (normalization and template engine fixtures in T4.2, queue rules in T4.4).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

// REQ-APP-041: no Google Play Services or Firebase anywhere in the app.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group.startsWith("com.google.android.gms") || requested.group.startsWith("com.google.firebase")) {
            throw GradleException("REQ-APP-041: ${requested.group}:${requested.name} is not allowed (no Google Play Services or Firebase)")
        }
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
