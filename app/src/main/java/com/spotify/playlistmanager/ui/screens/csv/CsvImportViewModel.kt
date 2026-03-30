package com.spotify.playlistmanager.ui.screens.csv

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotify.playlistmanager.data.csv.CsvParser
import com.spotify.playlistmanager.data.model.TrackAudioFeatures
import com.spotify.playlistmanager.domain.repository.ITrackFeaturesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CsvImportUiState(
    val phase: Phase = Phase.IDLE,
    val preview: List<TrackAudioFeatures> = emptyList(),
    val parseErrors: List<String> = emptyList(),
    val cachedCount: Int = 0,
    val importedCount: Int = 0,
    val skippedCount: Int = 0,
    val errorMessage: String? = null
) {
    enum class Phase { IDLE, PARSING, PREVIEW, IMPORTING, DONE, ERROR }
}

@HiltViewModel
class CsvImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ITrackFeaturesRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CsvImportUiState())
    val state: StateFlow<CsvImportUiState> = _state.asStateFlow()

    init {
        refreshCachedCount()
    }

    fun refreshCachedCount() {
        viewModelScope.launch {
            _state.update { it.copy(cachedCount = repository.count()) }
        }
    }

    /** Krok 1 – wczytaj i sparsuj plik, pokaż podgląd. */
    fun parseFile(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(phase = CsvImportUiState.Phase.PARSING) }
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { CsvParser.parse(it) }
                }
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        phase = CsvImportUiState.Phase.PREVIEW,
                        preview = result.features,
                        parseErrors = result.errors,
                        skippedCount = result.skipped
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        phase = CsvImportUiState.Phase.ERROR,
                        errorMessage = e.message ?: "Błąd odczytu pliku"
                    )
                }
            }
        }
    }

    /** Krok 2 – zapisz do Room. */
    fun confirmImport() {
        val toImport = _state.value.preview
        if (toImport.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(phase = CsvImportUiState.Phase.IMPORTING) }
            runCatching { repository.upsert(toImport) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            phase = CsvImportUiState.Phase.DONE,
                            importedCount = toImport.size
                        )
                    }
                    refreshCachedCount()
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            phase = CsvImportUiState.Phase.ERROR,
                            errorMessage = e.message ?: "Błąd zapisu do bazy"
                        )
                    }
                }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            repository.clearAll()
            refreshCachedCount()
            reset()
        }
    }

    fun reset() {
        _state.update { CsvImportUiState(cachedCount = it.cachedCount) }
    }
}