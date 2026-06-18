plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

/*
 * :shared jest teraz modułem Kotlin Multiplatform z dwoma celami JVM:
 *
 *   • androidTarget()  – konsumowany przez :app (aplikacja Android)
 *   • jvm("desktop")   – konsumowany przez :desktop (Compose for Desktop)
 *
 * Cała czysta logika domenowa (generator, krzywe energii, algorytmy DJ, modele)
 * mieszka w pośrednim zestawie źródeł `jvmShared`, z którego dziedziczą oba cele.
 * Trzymamy ją w warstwie JVM (a nie w commonMain), bo korzysta z biblioteki
 * standardowej JVM (np. String.format), a oba cele i tak są JVM.
 *
 * androidMain zawiera wyłącznie kod zależny od Androida:
 *   • CoilImageCacheCleaner (Coil + Context)
 *   • use-case'y wstrzykiwane przez Hilt (@Inject) – używane tylko przez :app
 */
kotlin {
    jvmToolchain(21)

    androidTarget()
    jvm("desktop")

    sourceSets {
        val commonMain by getting
        val commonTest by getting

        val jvmShared by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val jvmSharedTest by creating {
            dependsOn(commonTest)
            dependencies {
                implementation(libs.junit)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        val androidMain by getting {
            dependsOn(jvmShared)
            dependencies {
                compileOnly(libs.javax.inject)
                implementation(libs.hilt.android)
                implementation(libs.coil.compose)
            }
        }
        val desktopMain by getting {
            dependsOn(jvmShared)
        }

        val androidUnitTest by getting {
            dependsOn(jvmSharedTest)
        }
        val desktopTest by getting {
            dependsOn(jvmSharedTest)
        }
    }
}

android {
    namespace = "com.spotify.playlistmanager.shared"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
