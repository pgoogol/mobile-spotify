package com.spotify.playlistmanager.domain.model

import kotlin.math.abs
import kotlin.math.min

/**
 * Camelot Wheel — system kompatybilności harmonicznej dla DJ-ów.
 *
 * Koło Camelota to uproszczona reprezentacja kół kwintowych, gdzie każdy
 * klucz muzyczny ma numer (1–12) i literę (A = minor, B = major).
 * Sąsiednie numery i przejścia A↔B są harmonicznie kompatybilne.
 *
 * Przykład: 8B (Ab major) jest kompatybilny z 7B, 9B, 8A, 7A, 9A.
 *
 * Użycie w generatorze: po dopasowaniu utworów do krzywej energii,
 * [HarmonicOptimizer] reorderuje sąsiadów o zbliżonym composite score
 * tak, aby minimalizować dysonanse harmoniczne między kolejnymi utworami.
 */
object CamelotWheel {

    /**
     * Sparsowany klucz Camelota.
     * @param number numer na kole (1–12)
     * @param letter litera trybu: 'A' = minor, 'B' = major
     */
    data class CamelotKey(val number: Int, val letter: Char) {
        override fun toString(): String = "$number$letter"
    }

    // ── Mapowanie klucz muzyczny → Camelot ───────────────────────────────

    private val KEY_TO_CAMELOT = mapOf(
        // Major (B)
        "C" to CamelotKey(8, 'B'),   "Db" to CamelotKey(3, 'B'),
        "D" to CamelotKey(10, 'B'),  "Eb" to CamelotKey(5, 'B'),
        "E" to CamelotKey(12, 'B'),  "F" to CamelotKey(7, 'B'),
        "F#" to CamelotKey(2, 'B'),  "Gb" to CamelotKey(2, 'B'),
        "G" to CamelotKey(9, 'B'),   "Ab" to CamelotKey(4, 'B'),
        "A" to CamelotKey(11, 'B'),  "Bb" to CamelotKey(6, 'B'),
        "B" to CamelotKey(1, 'B'),

        // Minor (A)
        "Cm" to CamelotKey(5, 'A'),   "C#m" to CamelotKey(12, 'A'),
        "Dbm" to CamelotKey(12, 'A'), "Dm" to CamelotKey(7, 'A'),
        "Ebm" to CamelotKey(2, 'A'),  "D#m" to CamelotKey(2, 'A'),
        "Em" to CamelotKey(9, 'A'),   "Fm" to CamelotKey(4, 'A'),
        "F#m" to CamelotKey(11, 'A'), "Gbm" to CamelotKey(11, 'A'),
        "Gm" to CamelotKey(6, 'A'),   "G#m" to CamelotKey(1, 'A'),
        "Abm" to CamelotKey(1, 'A'),  "Am" to CamelotKey(8, 'A'),
        "Bbm" to CamelotKey(3, 'A'),  "A#m" to CamelotKey(3, 'A'),
        "Bm" to CamelotKey(10, 'A')
    )

    /**
     * Parsuje string Camelota (np. "8B", "11A") na [CamelotKey].
     * Akceptuje też klucze muzyczne ("Ab", "Gm") przez mapowanie.
     * Zwraca null gdy nie da się sparsować.
     */
    fun parseCamelot(raw: String): CamelotKey? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        // Próba 1: format "8B", "11A"
        val camelotRegex = Regex("""^(\d{1,2})([AaBb])$""")
        camelotRegex.matchEntire(trimmed)?.let { match ->
            val number = match.groupValues[1].toIntOrNull() ?: return null
            val letter = match.groupValues[2].uppercase()[0]
            if (number in 1..12 && letter in listOf('A', 'B')) {
                return CamelotKey(number, letter)
            }
        }

        // Próba 2: klucz muzyczny
        return KEY_TO_CAMELOT[trimmed]
    }

    /**
     * Oblicza odległość kołową (shortest path) między dwoma numerami na kole 1–12.
     */
    private fun circularDistance(a: Int, b: Int): Int {
        val diff = abs(a - b)
        return min(diff, 12 - diff)
    }

    /**
     * Score kompatybilności harmonicznej [0.0 – 1.0].
     *
     * 1.0  = ten sam klucz
     * 0.9  = ±1 na kole, ten sam tryb (A/B)
     * 0.85 = ten sam numer, zmiana trybu (A↔B) — "relative key"
     * 0.7  = ±1 na kole + zmiana trybu
     * 0.5  = ±2 na kole, ten sam tryb
     * 0.3  = ±2 na kole + zmiana trybu
     * 0.0  = odległość > 2
     */
    fun compatibilityScore(a: CamelotKey, b: CamelotKey): Float {
        val dist = circularDistance(a.number, b.number)
        val sameLetter = a.letter == b.letter

        return when {
            dist == 0 && sameLetter  -> 1.0f    // identyczny klucz
            dist == 0 && !sameLetter -> 0.85f   // relative key (A↔B)
            dist == 1 && sameLetter  -> 0.9f    // sąsiedni, ten sam tryb
            dist == 1 && !sameLetter -> 0.7f    // sąsiedni + zmiana trybu
            dist == 2 && sameLetter  -> 0.5f    // dwa kroki, ten sam tryb
            dist == 2 && !sameLetter -> 0.3f    // dwa kroki + zmiana trybu
            else                     -> 0.0f    // za daleko
        }
    }

    /**
     * Czy dwa klucze są kompatybilne (score >= 0.7)?
     */
    fun areCompatible(a: CamelotKey, b: CamelotKey): Boolean =
        compatibilityScore(a, b) >= 0.7f

    /**
     * Zwraca zbiór kluczy kompatybilnych (score >= 0.7) z danym kluczem.
     */
    fun compatibleKeys(key: CamelotKey): Set<CamelotKey> {
        val result = mutableSetOf<CamelotKey>()
        val otherLetter = if (key.letter == 'A') 'B' else 'A'

        // Ten sam klucz
        result.add(key)
        // Relative key
        result.add(CamelotKey(key.number, otherLetter))
        // ±1, ten sam tryb
        result.add(CamelotKey(wrapNumber(key.number - 1), key.letter))
        result.add(CamelotKey(wrapNumber(key.number + 1), key.letter))
        // ±1, zmiana trybu
        result.add(CamelotKey(wrapNumber(key.number - 1), otherLetter))
        result.add(CamelotKey(wrapNumber(key.number + 1), otherLetter))

        return result
    }

    /** Owija numer kołowy 1–12. */
    private fun wrapNumber(n: Int): Int = when {
        n < 1  -> n + 12
        n > 12 -> n - 12
        else   -> n
    }
}
