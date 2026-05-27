package com.spotify.playlistmanager.ui.screens.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotify.playlistmanager.domain.repository.IQueueRepository
import com.spotify.playlistmanager.domain.repository.QueueEntry
import com.spotify.playlistmanager.util.OfflineModeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val queueRepository: IQueueRepository,
    offlineModeManager: OfflineModeManager
) : ViewModel() {

    val queue: StateFlow<List<QueueEntry>> = queueRepository.queueFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val isOfflineMode: StateFlow<Boolean> = offlineModeManager.isEnabled

    fun removeEntry(entryId: Long) {
        viewModelScope.launch { queueRepository.removeFromQueue(entryId) }
    }

    fun clearQueue() {
        viewModelScope.launch { queueRepository.clearQueue() }
    }
}
