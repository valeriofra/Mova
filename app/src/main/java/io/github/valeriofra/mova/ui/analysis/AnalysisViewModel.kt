package io.github.valeriofra.mova.ui.analysis

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.valeriofra.mova.core.analysis.AnalysisReport
import io.github.valeriofra.mova.core.analysis.LinkAnalyzer
import io.github.valeriofra.mova.core.analysis.MediaItem
import io.github.valeriofra.mova.data.DownloadRepository
import io.github.valeriofra.mova.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AnalysisUiState {
    data object Loading : AnalysisUiState
    data class Done(val report: AnalysisReport) : AnalysisUiState
    data class Failed(val message: String?) : AnalysisUiState
}

class AnalysisViewModel(
    savedStateHandle: SavedStateHandle,
    private val analyzer: LinkAnalyzer,
    private val downloads: DownloadRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val input: String = savedStateHandle.get<String>("input").orEmpty()

    private val _state = MutableStateFlow<AnalysisUiState>(AnalysisUiState.Loading)
    val state: StateFlow<AnalysisUiState> = _state.asStateFlow()

    /** Itens marcados na tela de seleção (chave = URL do item). */
    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    val notificationsAsked: StateFlow<Boolean> = settings.settings.map { it.notificationsAsked }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _enqueued = Channel<Int>(Channel.BUFFERED)
    /** Emite a quantidade de itens adicionados à fila. */
    val enqueued: Flow<Int> = _enqueued.receiveAsFlow()

    private var analysisJob: Job? = null
    private var enqueueing = false

    init {
        analyze()
    }

    fun analyze() {
        analysisJob?.cancel()
        _state.value = AnalysisUiState.Loading
        analysisJob = viewModelScope.launch {
            val started = SystemClock.elapsedRealtime()
            val result = try {
                AnalysisUiState.Done(analyzer.analyze(input))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AnalysisUiState.Failed(e.message)
            }
            // Evita que a tela de análise apenas "pisque".
            val elapsed = SystemClock.elapsedRealtime() - started
            if (elapsed < MIN_LOADING_MS) delay(MIN_LOADING_MS - elapsed)
            if (result is AnalysisUiState.Done) {
                _selected.value = result.report.downloadableItems.map { it.url }.toSet()
            }
            _state.value = result
        }
    }

    val items: List<MediaItem>
        get() = (state.value as? AnalysisUiState.Done)?.report?.downloadableItems.orEmpty()

    fun toggle(item: MediaItem) {
        _selected.value = _selected.value.let { if (item.url in it) it - item.url else it + item.url }
    }

    fun setAllSelected(selectAll: Boolean) {
        _selected.value = if (selectAll) items.map { it.url }.toSet() else emptySet()
    }

    fun enqueue(items: List<MediaItem>) {
        if (enqueueing || items.isEmpty()) return
        enqueueing = true
        viewModelScope.launch {
            try {
                val count = downloads.enqueue(items)
                _enqueued.send(count)
            } finally {
                enqueueing = false
            }
        }
    }

    fun enqueueSelected() {
        val chosen = _selected.value
        enqueue(items.filter { it.url in chosen })
    }

    fun markNotificationsAsked() {
        viewModelScope.launch { settings.setNotificationsAsked() }
    }

    private companion object {
        const val MIN_LOADING_MS = 700L
    }
}
