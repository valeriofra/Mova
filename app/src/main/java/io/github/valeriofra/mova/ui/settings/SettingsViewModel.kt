package io.github.valeriofra.mova.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.valeriofra.mova.data.DownloadRepository
import io.github.valeriofra.mova.data.settings.MovaSettings
import io.github.valeriofra.mova.data.settings.SettingsRepository
import io.github.valeriofra.mova.data.settings.ThemeMode
import io.github.valeriofra.mova.download.YtDlpEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val downloads: DownloadRepository,
    private val engine: YtDlpEngine,
) : ViewModel() {

    /** Estado do mecanismo de download (yt-dlp). */
    val engineState: StateFlow<YtDlpEngine.EngineState> = engine.state

    private val _engineUpdate = MutableStateFlow<YtDlpEngine.UpdateResult?>(null)
    /** Resultado da última atualização manual (para exibir uma confirmação). */
    val engineUpdate: StateFlow<YtDlpEngine.UpdateResult?> = _engineUpdate.asStateFlow()

    /** Prepara o mecanismo para mostrar a versão instalada. */
    fun loadEngine() {
        viewModelScope.launch { runCatching { engine.ensureReady() } }
    }

    fun updateEngine() {
        viewModelScope.launch { _engineUpdate.value = engine.update() }
    }

    fun engineUpdateShown() {
        _engineUpdate.value = null
    }

    val state: StateFlow<MovaSettings?> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _temporaryBytes = MutableStateFlow(0L)
    val temporaryBytes: StateFlow<Long> = _temporaryBytes.asStateFlow()

    private val _freedBytes = MutableStateFlow<Long?>(null)
    /** Bytes liberados na última limpeza (para exibir uma confirmação). */
    val freedBytes: StateFlow<Long?> = _freedBytes.asStateFlow()

    fun refreshTemporarySize() {
        viewModelScope.launch {
            _temporaryBytes.value = withContext(Dispatchers.IO) { downloads.temporaryFilesSize() }
        }
    }

    fun setWifiOnly(value: Boolean) {
        viewModelScope.launch {
            settings.setWifiOnly(value)
            // Reagenda com a nova exigência de rede, se houver algo pendente.
            downloads.ensureProcessing()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setMaxConcurrent(value: Int) {
        viewModelScope.launch { settings.setMaxConcurrent(value) }
    }

    fun setStorageFolder(uri: String, name: String?) {
        viewModelScope.launch { settings.setStorageTree(uri, name) }
    }

    fun useDefaultStorage() {
        viewModelScope.launch { settings.setStorageTree(null, null) }
    }

    fun cleanTemporaryFiles() {
        viewModelScope.launch {
            val freed = withContext(Dispatchers.IO) { downloads.cleanTemporaryFiles() }
            _freedBytes.value = freed
            refreshTemporarySize()
        }
    }

    fun freedMessageShown() {
        _freedBytes.value = null
    }
}
