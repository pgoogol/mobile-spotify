plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    kotlin("plugin.serialization") version "2.0.21"
}

android {
    namespace   = "com.spotify.playlistmanager.shared"
    compileSdk  = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions { jvmTarget = "21" }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.room.runtime)
    implementation(libs.coil.compose)

    implementation(libs.hilt.android)

    // Room usunięty z :shared – TrackFeaturesCache (@Entity) przeniesiony do :app/data/cache
    // :shared nie zna żadnych bibliotek Androidowych specyficznych dla warstwy danych
    compileOnly(libs.javax.inject)

    // Testy jednostkowe – bez emulatora
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    implementation(kotlin("test"))
}
