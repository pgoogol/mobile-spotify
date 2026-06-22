package com.spotify.playlistmanager.domain.dj.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Główny styl utworu — jednostka, na poziomie której działają profile,
 * pula puli (jeden generator per styl) i wzorzec slotów imprezy.
 *
 * `null` jako wynik wykrywania = utwór wykluczony z generatora "Impreza"
 * (np. nie został oznaczony genres pasującymi do salsy/bachaty).
 */
@Serializable
enum class Style {
    @SerialName("salsa")
    SALSA,

    @SerialName("bachata")
    BACHATA
}

/**
 * Podstyle używane przez funkcję kosztu (`substyle_pen`) i strategie podstylów
 * w `PartyPlanner`. Wykrywane przez [com.spotify.playlistmanager.domain.dj.StyleDetector]
 * z surowego pola `TrackAudioFeatures.genres`.
 *
 * Dla bachaty obecnie jeden podstyl ([BACHATA]) — spec sekcja 5.3:
 * "bachata jest praktycznie monostylowa".
 */
@Serializable
enum class Substyle {
    @SerialName("timba")
    TIMBA,

    @SerialName("son_cubano")
    SON_CUBANO,

    @SerialName("salsa_generic")
    SALSA_GENERIC,

    @SerialName("rumba")
    RUMBA,

    @SerialName("bachata")
    BACHATA
}
