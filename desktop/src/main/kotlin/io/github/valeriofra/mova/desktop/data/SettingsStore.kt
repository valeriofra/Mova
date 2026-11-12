package io.github.valeriofra.mova.desktop.data

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Ajustes do app, gravados em `settings.json`. */
class SettingsStore(file: File) {
    private val storage = JsonFile(file, DesktopSettings.serializer())
    private val _settings = MutableStateFlow(storage.read() ?: DesktopSettings())
    val settings: StateFlow<DesktopSettings> = _settings.asStateFlow()

    val current: DesktopSettings get() = _settings.value

    fun update(transform: (DesktopSettings) -> DesktopSettings) {
        _settings.update(transform)
        runCatching { storage.write(_settings.value) }
    }

    fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }
    fun setLanguage(language: String?) = update { it.copy(language = language) }
    fun setDownloadDir(path: String?) = update { it.copy(downloadDir = path) }
    fun setSaveMode(mode: SaveMode) = update { it.copy(saveMode = mode) }
    fun setLastSaveDir(path: String?) = update { it.copy(lastSaveDir = path) }
    fun setMaxConcurrent(value: Int) = update { it.copy(maxConcurrent = value.coerceIn(DesktopSettings.CONCURRENT_RANGE)) }
    fun setAutoDeleteDays(days: Int) = update { it.copy(autoDeleteDays = days.coerceAtLeast(0)) }
    fun setLastEngineUpdate(time: Long) = update { it.copy(lastEngineUpdate = time) }
}
