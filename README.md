# Spotify Playlist Manager

Aplikacja przepisana z Pythona (tkinter) na **Kotlin + Compose**, dostępna w
dwóch wariantach dzielących wspólną logikę domenową:

- **Android** (`:app`) – Jetpack Compose + Hilt
- **Desktop** (`:desktop`) – Compose for Desktop (JVM) — patrz
  [Wersja desktopowa](#wersja-desktopowa-compose-for-desktop)

## Architektura

```
MVVM + Clean Architecture + Hilt DI
```

### Moduły (Kotlin Multiplatform)

```
:shared    – KMP (androidTarget + jvm("desktop"))
             cała czysta logika domenowa: generator, krzywe energii,
             composite score, algorytmy DJ, modele
             └ src/jvmShared/  – kod wspólny dla obu celów JVM
             └ src/androidMain/ – kod zależny od Androida (Coil, Hilt @Inject)
:app       – aplikacja Android (konsumuje android target :shared)
:desktop   – aplikacja desktopowa (konsumuje jvm("desktop") target :shared)
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
| Tokeny | DataStore Preferences (access + refresh token) |
| Auth | OAuth 2.0 Authorization Code + PKCE (Custom Tabs) |
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

## Wersja desktopowa (Compose for Desktop)

Aplikacja desktopowa (`:desktop`) reużywa **tej samej logiki** co Android —
moduł `:shared` jest projektem Kotlin Multiplatform z celami `androidTarget()`
oraz `jvm("desktop")`.

### Uruchomienie

```bash
./gradlew :desktop:run
```

Pakiet natywny dla bieżącego systemu (`.dmg` / `.msi` / `.deb`):

```bash
./gradlew :desktop:packageDistributionForCurrentOS
```

**Wymagania:** JDK 21.

### Konfiguracja (Client ID + Redirect URI)

1. W [Spotify Developer Dashboard](https://developer.spotify.com/dashboard)
   dodaj **Redirect URI** dla desktopu:
   ```
   http://127.0.0.1:8888/callback
   ```
2. Podaj **Client ID** jednym ze sposobów (czytane w tej kolejności):
   - w `local.properties`: `spotify.clientId=TWOJ_CLIENT_ID`
   - zmienna środowiskowa: `SPOTIFY_CLIENT_ID=...`
   - argument JVM: `-Dspotify.clientId=...`

### Co zawiera

- **Logowanie do Spotify** — OAuth 2.0 Authorization Code + PKCE z redirectem
  na loopback `http://127.0.0.1:8888/callback` (uruchamia lokalny serwer,
  otwiera przeglądarkę, zapisuje tokeny w `~/.spotify-playlist-manager/`).
- **Lista playlist** użytkownika + ❤ Polubione — prawdziwe dane z Web API,
  pobrane przez współdzielony kontrakt `ISpotifyRepository` z `:shared`.
- **Generator krzywych energii** (zakładka „Generator") na wbudowanej puli
  `SampleData`: wybór strategii segmentu, liczby utworów, wykres docelowa vs
  rzeczywista (Compose Canvas) i lista dopasowanych utworów z composite score.

Warstwa sieci/repozytorium używa Retrofit/OkHttp/Gson (działają na JVM) i
mapuje DTO z `:shared` na modele domenowe — tak samo jak `:app`.

### Następne kroki (do rozbudowy)

- Ekran **utworów** ze statystykami i sortowaniem (jak na Androidzie).
- Podpięcie generatora pod **prawdziwe playlisty** + import cech audio z CSV
  oraz **zapis** wygenerowanej playlisty na Spotify.
- Port **trybu DJ / Impreza** (plan + live).
- Wyniesienie warstwy Web API do `:shared` (współdzielonej z `:app`).

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
- **Strategie segmentu:** Brak, Narastająco ↗, Opadająco ↘, Stabilnie ━,
  Łuk 🎢, Dolina 🌀, Fala ∿, Romantycznie 🌹, Spokojnie 🌙
- Wykres krzywej energii (Canvas) – docelowa vs rzeczywista
- Zmiana kolejności (przyciski ▲▼)
- Zapis playlisty bezpośrednio do Spotify

## Strategie segmentu – algorytm

Każda strategia definiuje dwa wymiary:

1. **Kształt** – `generateTargets(N)` zwraca listę docelowych score'ów [0..1]
   dla kolejnych pozycji. Matcher szuka w puli tracków o score najbliższym
   do targetu (po auto-skalowaniu do percentyli p5–p95 puli).

2. **Oś** (`ScoreAxis`) – który composite score jest używany:
   - `DANCE` = 0.45·BPM + 0.35·energy + 0.20·danceability
   - `MOOD`  = 0.55·valence + 0.25·(1−acousticness) + 0.20·danceability

| Strategia      | Symbol | Oś    | Kształt                          | Min N |
|----------------|--------|-------|----------------------------------|-------|
| Brak           | —      | DANCE | sortowanie wg ustawień           | 1     |
| Narastająco    | ↗      | DANCE | 0.0 → 1.0 liniowo                | 2     |
| Opadająco      | ↘      | DANCE | 1.0 → 0.0 liniowo                | 2     |
| Stabilnie      | ━      | DANCE | wszystkie 0.5 (blisko mediany)   | 2     |
| Łuk            | 🎢     | DANCE | narasta → pik (65%) → opada      | 3     |
| Dolina         | 🌀     | DANCE | opada → dno (50%) → narasta      | 3     |
| Fala           | ∿      | DANCE | sinusoida (konfigurowalny takt)  | 2     |
| Romantycznie   | 🌹     | MOOD  | wszystkie 1.0 (top MOOD)         | 2     |
| Spokojnie      | 🌙     | MOOD  | 1.0 → 0.3 (schłodzenie klimatu)  | 2     |

Auto-range: targety są skalowane do rozkładu p5–p95 puli, więc bachata
(composite ~0.15–0.35) i salsa (composite ~0.55–0.95) dają pełną
rozpiętość bez konfiguracji zakresu.
