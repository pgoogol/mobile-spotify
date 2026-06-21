package com.spotify.playlistmanager.desktop.data

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Natywny dialog wyboru pliku CSV z cechami audio.
 *
 * Plik jest parsowany przez współdzielony `CsvParser` z :shared (ten sam, co na
 * Androidzie) i ładowany do [com.spotify.playlistmanager.desktop.data.repository.InMemoryTrackFeaturesRepository].
 */
object CsvImport {

    /** Otwiera modalny dialog wyboru pliku (musi być wołany z wątku UI). */
    fun pickFile(): File? {
        val dialog = FileDialog(null as Frame?, "Wybierz plik CSV z cechami audio", FileDialog.LOAD)
        dialog.isVisible = true
        val name = dialog.file ?: return null
        val dir = dialog.directory ?: return null
        return File(dir, name)
    }
}
