package io.github.valeriofra.mova.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.valeriofra.mova.data.DownloadRepository
import io.github.valeriofra.mova.data.db.DownloadEntity
import io.github.valeriofra.mova.data.db.DownloadStatus
import io.github.valeriofra.mova.data.settings.SettingsRepository
import io.github.valeriofra.mova.download.DownloadScheduler
import io.github.valeriofra.mova.download.QueueRunState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class QueueUiState(
    val running: List<DownloadEntity> = emptyList(),
    val queued: List<DownloadEntity> = emptyList(),
    val paused: List<DownloadEntity> = emptyList(),
    val failed: List<DownloadEntity> = emptyList(),
    val recentlyCompleted: List<DownloadEntity> = emptyList(),
    val waitingForNetwork: Boolean = false,
    val wifiOnly: Boolean = false,
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean
        get() = running.isEmpty() && queued.isEmpty() && paused.isEmpty() && failed.isEmpty()
}

class QueueViewModel(
    private val downloads: DownloadRepository,
    scheduler: DownloadScheduler,
    settings: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<QueueUiState> = combine(
        downloads.queue,
        downloads.recentlyCompleted(RECENT_COMPLETED),
        scheduler.observeState(),
        settings.settings,
    ) { queue, completed, runState, prefs ->
        val byStatus = queue.groupBy { it.status }
        val queued = byStatus[DownloadStatus.QUEUED].orEmpty()
        val running = byStatus[DownloadStatus.RUNNING].orEmpty()
        QueueUiState(
            running = running,
            queued = queued,
            paused = byStatus[DownloadStatus.PAUSED].orEmpty(),
            failed = byStatus[DownloadStatus.FAILED].orEmpty(),
            recentlyCompleted = completed,
            waitingForNetwork = queued.isNotEmpty() && running.isEmpty() &&
                runState == QueueRunState.WAITING_FOR_NETWORK,
            wifiOnly = prefs.wifiOnly,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QueueUiState())

    fun pause(id: Long) = launch { downloads.pause(id) }
    fun resume(id: Long) = launch { downloads.resume(id) }
    fun retry(id: Long) = launch { downloads.retry(id) }
    fun cancel(id: Long) = launch { downloads.cancel(id) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val RECENT_COMPLETED = 5
    }
}
