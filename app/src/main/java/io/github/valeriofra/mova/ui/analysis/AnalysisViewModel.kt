package io.github.valeriofra.mova.ui.analysis

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.valeriofra.mova.core.analysis.AnalysisReport
import io.github.valeriofra.mova.core.analysis.DownloadOptions
import io.github.valeriofra.mova.core.analysis.FormatChoice
import io.github.valeriofra.mova.core.analysis.LinkAnalyzer
import io.github.valeriofra.mova.core.analysis.MediaItem
import io.github.valeriofra.mova.data.DownloadRepository
import io.github.valeriofra.mova.data.settings.SettingsRepository
import io.github.valeriofra.mova.download.YtDlpEngine
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

/** O mecanismo de download está sendo preparado ou atualizado (a análise demora mais). */
enum class EngineBusy { PREPARING, UPDATING }

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
    engineState: Flow<YtDlpEngine.EngineState>,
) : ViewModel() {

    val engineBusy: StateFlow<EngineBusy?> = engineState.map {
        when (it) {
            YtDlpEngine.EngineState.Preparing -> EngineBusy.PREPARING
            is YtDlpEngine.EngineState.Updating -> EngineBusy.UPDATING
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Formato escolhido na folha de opções (usado também na seleção de itens). */
    private val _format = MutableStateFlow<FormatChoice>(FormatChoice.Original)
    val format: StateFlow<FormatChoice> = _format.asStateFlow()

    val input: String = savedStateHandle.get<String>("input").orEmpty()

    private val _state = MutableStateFlow<AnalysisUiState>(AnalysisUiState.Loading)
    val state: StateFlow<AnalysisUiState> = _state.asStateFlow()

    /** Itens marcados na tela de seleção (chave = MediaItem.key). */
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
            } catch (e: Throwable) {
                // Qualquer falha inesperada vira uma mensagem, nunca um fechamento do app.
                AnalysisUiState.Failed(e.toString())
            }
            // Evita que a tela de análise apenas "pisque".
            val elapsed = SystemClock.elapsedRealtime() - started
            if (elapsed < MIN_LOADING_MS) delay(MIN_LOADING_MS - elapsed)
            if (result is AnalysisUiState.Done) {
                _selected.value = result.report.downloadableItems.map { it.key }.toSet()
                _format.value = DownloadOptions.defaultFormat(result.report.downloadableItems)
            }
            _state.value = result
        }
    }

    val items: List<MediaItem>
        get() = (state.value as? AnalysisUiState.Done)?.report?.downloadableItems.orEmpty()

    fun toggle(item: MediaItem) {
        _selected.value = _selected.value.let { if (item.key in it) it - item.key else it + item.key }
    }

    fun setAllSelected(selectAll: Boolean) {
        _selected.value = if (selectAll) items.map { it.key }.toSet() else emptySet()
    }

    fun chooseFormat(format: FormatChoice) {
        _format.value = format
    }

    fun enqueue(items: List<MediaItem>, format: FormatChoice = _format.value) {
        if (enqueueing || items.isEmpty()) return
        enqueueing = true
        viewModelScope.launch {
            try {
                val count = downloads.enqueue(items, format)
                _enqueued.send(count)
            } finally {
                enqueueing = false
            }
        }
    }

    fun enqueueSelected() {
        val chosen = _selected.value
        enqueue(items.filter { it.key in chosen }, _format.value)
    }

    fun markNotificationsAsked() {
        viewModelScope.launch { settings.setNotificationsAsked() }
    }

    private companion object {
        const val MIN_LOADING_MS = 700L
    }
}
