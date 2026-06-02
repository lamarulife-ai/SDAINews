import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Read upload-key credentials from gitignored keystore.properties.
// Absence is non-fatal — debug builds and CI runs without secrets still
// work; only `assembleRelease` / `bundleRelease` requires it.
val keystoreFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystoreFile.exists()) load(FileInputStream(keystoreFile))
}
val hasReleaseKey = keystoreFile.exists()
    && keystoreProps.getProperty("storeFile")?.isNotBlank() == true

android {
    namespace = "com.sdai.news"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sdai.news"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // The `release` signing config is only fully wired when the
        // keystore.properties file exists. On a fresh clone (no keystore
        // configured yet) this still compiles — only a release build
        // tries to actually sign.
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Only attach the signing config if we actually have one;
            // otherwise the release build will produce an unsigned AAB
            // that Play Console rejects — by design, so missing keys
            // fail loud at upload rather than silently.
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/LICENSE*",
            "/META-INF/NOTICE*",
        )
    }

    lint {
        abortOnError = false
        // Skip lint on release builds. `lintVitalAnalyzeRelease` is the
        // #1 source of Windows file-lock errors on rebuild (it caches
        // jars and Gradle can't always release the handles before the
        // next `clean`). Run lint manually via `gradlew lint` when you
        // actually want the report.
        checkReleaseBuilds = false
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")    // VerticalPager
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Chrome Custom Tabs — opens articles in the user's existing
    // browser engine (cookies, ad-blockers, autofill all carry over)
    // while keeping the SD AI News toolbar branding.
    implementation("androidx.browser:browser:1.8.0")

    // Image loading — Coil's compose binding handles caching + lazy decode
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Networking — OkHttp for HTTP, Moshi for JSON parsing
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.moshi:moshi:1.15.1")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")

    // Room — caches the latest article batch + persists bookmarks offline
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore for user prefs (theme, wellness toggle)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Daily news push — WorkManager runs the fetch, posts a notification
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Home-screen widget — Compose-style Glance
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
}
