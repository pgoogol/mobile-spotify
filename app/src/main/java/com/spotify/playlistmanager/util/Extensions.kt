package com.spotify.playlistmanager.util

/** Formatuje ms -> "m:ss" */
fun Int.toMinutesSeconds(): String {
    val total = this / 1000
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}

/** Formatuje ms -> "h:mm:ss" lub "m:ss" */
fun Long.toHoursMinutesSeconds(): String {
    val total = this / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Ogranicza String do maxLen znaków, dodając "…" */
fun String.truncate(maxLen: Int): String =
    if (length <= maxLen) this else take(maxLen - 1) + "…"
