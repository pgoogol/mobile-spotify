plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.spotify.playlistmanager"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.spotify.playlistmanager"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.2.4"

        // ⚠️  Uzupełnij własne dane z developer.spotify.com/dashboard
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"3298169ef5a64bf39b6c1466e304c790\"")
        buildConfigField("String", "SPOTIFY_REDIRECT_URI", "\"com.spotify.playlistmanager://callback\"")

        // Wymagane przez Spotify Auth SDK >= 2.0
        manifestPlaceholders["redirectSchemeName"] = "com.spotify.playlistmanager"
        manifestPlaceholders["redirectHostName"] = "callback"
        // Wymagane przez Spotify Auth SDK >= 3.0
        manifestPlaceholders["redirectPathPattern"] = ".*"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":shared"))              // ← Etap 2: shared module
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose BOM – wszystkie wersje Compose spójne
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt – Dependency Injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Retrofit + OkHttp – Spotify Web API
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Coil – ładowanie obrazów (okładki albumów)
    implementation(libs.coil.compose)

    // Room – lokalna baza danych (cache cech audio)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore – przechowywanie tokenu
    implementation(libs.datastore)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Spotify Auth SDK 3.1.0 (MavenCentral)
    implementation(libs.spotify.auth)

    // Spotify App Remote SDK – WYMAGA RĘCZNEGO POBRANIA AAR
    // 1. Pobierz: https://github.com/spotify/android-sdk/releases
    // 2. Skopiuj spotify-app-remote-release-*.aar -> app/libs/
    // 3. Odkomentuj:
    implementation(files("libs/spotify-app-remote-release-0.8.0.aar"))

    testImplementation(kotlin("test"))
}
