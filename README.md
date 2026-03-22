# Spotify Playlist Manager – Android

Aplikacja Android przepisana z Pythona (tkinter) na **Kotlin + Jetpack Compose**.

## Architektura

```
MVVM + Clean Architecture + Hilt DI
```

```
app/
├── data/
│   ├── api/          – Retrofit (Spotify Web API)
│   ├── cache/        – Room (audio features cache)
│   ├── model/        – modele danych
│   └── repository/   – SpotifyRepository + PlaylistGeneratorEngine
├── di/               – Hilt modules
├── ui/
│   ├── screens/
│   │   ├── login/    – Logowanie przez Spotify Auth SDK
│   │   ├── playlists/ – Lista playlist z wyszukiwaniem
│   │   ├── tracks/   – Lista utworów ze statystykami + sortowanie
│   │   └── generate/ – Generator playlist z krzywymi energii
│   ├── components/   – EnergyCurveChart (Canvas)
│   └── theme/        – Spotify dark theme (Material3)
└── util/             – TokenManager, EnergyCurveCalculator, Extensions
```

## Technologie

| Komponent | Technologia |
|-----------|-------------|
| UI | Jetpack Compose + Material3 |
| Nawigacja | Navigation Compose |
| DI | Hilt |
| HTTP | Retrofit + OkHttp |
| Zdjęcia | Coil |
| Baza danych | Room (cache audio features) |
| Tokeny | DataStore Preferences |
| Auth | Spotify Auth SDK 3.1.0 |
| Wykres energii | Compose Canvas |

## Konfiguracja

### 1. Spotify Developer Dashboard

1. Wejdź na [developer.spotify.com/dashboard](https://developer.spotify.com/dashboard)
2. Utwórz aplikację → skopiuj **Client ID**
3. W ustawieniach dodaj Redirect URI:
   ```
   com.spotify.playlistmanager://callback
   ```
4. Dodaj package name: `com.spotify.playlistmanager`
5. Dodaj SHA-1 fingerprint swojej aplikacji (patrz niżej)

### 2. Uzupełnij Client ID

W pliku `app/build.gradle.kts` zmień:
```kotlin
buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"TWOJ_CLIENT_ID\"")
```

### 3. SHA-1 fingerprint (wymagane przez Spotify SDK)

```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey \
        -storepass android -keypass android
```

Skopiuj SHA-1 i wklej w Spotify Developer Dashboard.

## Uruchomienie

```bash
./gradlew assembleDebug
```

lub otwórz projekt w **Android Studio** (Hedgehog+) i kliknij Run.

**Wymagania:**
- Android 8.0+ (API 26+)
- Zainstalowana aplikacja Spotify na urządzeniu

## Funkcjonalności

### Ekran playlist
- Lista wszystkich playlist + ❤ Polubione utwory
- Wyszukiwanie po nazwie
- Pull-to-refresh

### Ekran utworów
- Statystyki: liczba, czas, śr. BPM (min/max), energia, taneczność
- Filtrowanie po tytule/artyście/albumie
- Sortowanie po każdej kolumnie (klik na nagłówek)
- Audio features z Room cache lub Spotify API

### Generator playlist
- Wiele źródeł z indywidualnymi ustawieniami
- Sortowanie: popularność, długość, energia, taneczność, BPM, data
- **Krzywe energii:** wzrastająca ↗, opadająca ↘, fala ∿, losowa 🎲,
  Salsa 💃, Bachata 🌹, Reggaeton 🔥, Merengue ⚡, Cumbia 🎺, stała ─
- Wykres krzywej energii (Canvas) – docelowa vs rzeczywista
- Zmiana kolejności (przyciski ▲▼)
- Zapis playlisty bezpośrednio do Spotify

## Krzywe energii – algorytm

Każda krzywa generuje docelową wartość energii (0–1) dla każdej pozycji
w playliście. Następnie algorytm dopasowuje dostępne utwory do pozycji
minimalizując odchylenie ich rzeczywistej energii od docelowej.

```
Salsa:     base + clave (rytm 3-2)
Bachata:   powolne narastanie z pulsami
Reggaeton: plateau z gwałtownym narastaniem/zejściem
Merengue:  stale wysokie tempo z minimalnymi wahaniami
Cumbia:    budowanie przez całą playlistę z dwiema falami
```
