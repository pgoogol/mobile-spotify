import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
}

/*
 * :desktop – aplikacja na komputer (Compose for Desktop / JVM).
 *
 * Reużywa całej logiki domenowej z modułu :shared (cel `jvm("desktop")`):
 * generator playlist, krzywe energii, composite score, algorytmy DJ.
 *
 * Uruchomienie:  ./gradlew :desktop:run
 * Pakiet natywny: ./gradlew :desktop:packageDistributionForCurrentOS
 */
kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    // Dispatcher Main dla Compose Desktop (Swing event loop).
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "com.spotify.playlistmanager.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "SpotifyPlaylistManager"
            packageVersion = "1.0.0"
        }
    }
}
