import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin: domain rules with no Android APIs, so they run as plain JVM unit tests
// (normalization and template engine fixtures in T4.2, queue rules in T4.4).
plugins {
    alias(libs.plugins.kotlin.jvm)
    // The Parser Template fixture suite lives in testFixtures, so the same code runs as JVM unit
    // tests here and as instrumented tests on Android (app androidTest).
    `java-test-fixtures`
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

// Parser Template fixtures: a local copy from the private Asancart documents (scripts/sync-parser-templates.sh),
// never committed. The fixture tests skip when it is missing.
sourceSets.named("test") { resources.srcDir(rootProject.file("fixtures")) }

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testFixturesImplementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
