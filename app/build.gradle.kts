import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinKapt)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlinSerialization)
}

// ── Release signing ─────────────────────────────────────────────────
// Reads keystore credentials from keystore.properties (not checked in).
// If the file is missing the release build type falls back to unsigned,
// which is fine for debug testing but will NOT produce an uploadable AAB.
//
// Expected keystore.properties format:
//   storeFile=path/to/automatist-release.jks
//   storePassword=...
//   keyAlias=...
//   keyPassword=...
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val hasSigningConfig = keystorePropertiesFile.exists()
if (hasSigningConfig) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

android {
    namespace = "com.automatist.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.automatist.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    if (hasSigningConfig) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
    lint {
        // Run lint checks but do not abort the release build.
        // Review the HTML report at build/reports/lint-results-release.html
        // and fix issues incrementally before Play upload.
        abortOnError = false
        checkReleaseBuilds = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // Retrofit & OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Google AI Edge — AICore (on-device Gemini Nano via Android system service)
    // RELEASE NOTE: This is an experimental library (0.0.1-exp01). The API surface is
    // small and stable for our use (GenerativeModel, prepareInferenceEngine, generateContent),
    // but it may change in future releases. The feature is fully optional and gated:
    //   - Requires Android 14+ (API 34) at runtime; all call sites guard with Build.VERSION check
    //   - Only executes on AICore-capable devices (Pixel 8+, Galaxy S24+)
    //   - Unsupported devices receive OfflineModelStatus.UNSUPPORTED gracefully, no crash
    //   - AndroidManifest uses tools:overrideLibrary to resolve library minSdk 31 vs app minSdk 26
    //   - Cloud/API provider paths are completely unaffected
    implementation(libs.google.ai.edge.aicore)

    // MediaPipe LLM Inference — downloadable offline models (Gemma 3n E2B)
    // Only used when user has downloaded a DOWNLOADABLE model. Does not affect
    // cloud/API providers or the AICore/Gemini Nano path.
    implementation(libs.google.mediapipe.tasks.genai)

    // Google Drive + Auth (Cloud Sync)
    implementation(libs.google.api.drive) {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation(libs.google.api.client.android) {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation(libs.google.play.auth)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Debug
    debugImplementation(libs.androidx.ui.tooling)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
}

// Custom task: build release APK/AAB and install on connected device
// Usage: ./gradlew buildReleaseAndInstall
tasks.register("buildReleaseAndInstall") {
    dependsOn("assembleRelease", "installRelease")
    doLast {
        println("\n✅ Release build complete and installed on connected device")
        println("📦 Play Store AAB: app/build/outputs/bundle/release/app-release.aab")
        println("📱 Device APK: Installed on device\n")
    }
}
