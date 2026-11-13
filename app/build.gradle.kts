plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Platform API base URL (REQ-APP-022). Release builds use the documented production value
// (DEC-029). Debug builds use a local backend; the default is the dev machine as seen from the
// Android emulator. Override with -Pasancart.platformApiUrl=... or in ~/.gradle/gradle.properties.
val productionPlatformApiUrl = "https://api.asancart.org"
val localPlatformApiUrl = providers.gradleProperty("asancart.platformApiUrl").getOrElse("http://10.0.2.2:8080")

android {
    namespace = "io.github.asancart.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.asancart.android"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The Parser Template fixtures, shared with :core's JVM tests, as assets of the instrumented
    // tests: the same suite runs on the device's regex engine (T4.2).
    sourceSets.named("androidTest") {
        assets.directories += "../fixtures"
    }

    buildTypes {
        debug {
            // Debug builds install next to a release build and are signed with the debug key.
            applicationIdSuffix = ".debug"
            buildConfigField("String", "PLATFORM_API_BASE_URL", "\"$localPlatformApiUrl\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "PLATFORM_API_BASE_URL", "\"$productionPlatformApiUrl\"")
            // Release signing is T4.5; keys stay offline (REQ-OPS-033), never in this repo.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // :core uses java.time, which needs desugaring below API 26.
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    lint {
        abortOnError = true
        checkDependencies = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Robolectric on SDK 36 reaches FileDescriptor internals through jdk.internal.access (JDK 17+).
tasks.withType<Test>().configureEach {
    jvmArgs(
        "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
    )
}

// REQ-APP-041: no Google Play Services or Firebase anywhere in the app. Fails any resolution that
// would pull one in, directly or transitively.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group.startsWith("com.google.android.gms") || requested.group.startsWith("com.google.firebase")) {
            throw GradleException("REQ-APP-041: ${requested.group}:${requested.name} is not allowed (no Google Play Services or Firebase)")
        }
    }
}

dependencies {
    implementation(project(":core"))
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.startup.runtime)
    implementation(libs.androidx.core)
    implementation(libs.acra.core)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(testFixtures(project(":core")))
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)

    androidTestImplementation(testFixtures(project(":core")))
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.junit)
}
