plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
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
    implementation(libs.kotlinx.coroutines.android)
    // Room usunięty z :shared – TrackFeaturesCache (@Entity) przeniesiony do :app/data/cache
    // :shared nie zna żadnych bibliotek Androidowych specyficznych dla warstwy danych
    compileOnly("javax.inject:javax.inject:1")

    // Testy jednostkowe – bez emulatora
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
