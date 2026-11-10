package io.github.valeriofra.mova.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.valeriofra.mova.core.model.MediaKind
import io.github.valeriofra.mova.data.DownloadRepository
import io.github.valeriofra.mova.data.db.DownloadEntity
import io.github.valeriofra.mova.download.FilePublisher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LibraryFilter { ALL, VIDEO, AUDIO, IMAGE, OTHER }

enum class LibraryTab { FILES, HISTORY }

data class LibraryUiState(
    val tab: LibraryTab = LibraryTab.FILES,
    val filter: LibraryFilter = LibraryFilter.ALL,
    val files: List<DownloadEntity> = emptyList(),
    val totalFiles: Int = 0,
    val history: List<DownloadEntity> = emptyList(),
    val loaded: Boolean = false,
)

class LibraryViewModel(
    private val downloads: DownloadRepository,
    private val publisher: FilePublisher,
) : ViewModel() {

    private val tab = MutableStateFlow(LibraryTab.FILES)
    private val filter = MutableStateFlow(LibraryFilter.ALL)

    val state: StateFlow<LibraryUiState> = combine(
        downloads.library,
        downloads.history,
        tab,
        filter,
    ) { library, history, currentTab, currentFilter ->
        LibraryUiState(
            tab = currentTab,
            filter = currentFilter,
            files = library.filter { it.matches(currentFilter) },
            totalFiles = library.size,
            history = history,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    private val _messages = MutableStateFlow<LibraryMessage?>(null)
    val messages: StateFlow<LibraryMessage?> = _messages.asStateFlow()

    fun selectTab(value: LibraryTab) {
        tab.value = value
    }

    fun selectFilter(value: LibraryFilter) {
        filter.value = value
    }

    /** Marca como ausentes os arquivos apagados fora do app. */
    fun verifyFiles() {
        viewModelScope.launch {
            val items = downloads.library.first()
            withContext(Dispatchers.IO) {
                items.forEach { item ->
                    val uri = item.outputUri ?: return@forEach
                    if (!publisher.exists(Uri.parse(uri))) downloads.markFileMissing(item.id)
                }
            }
        }
    }

    fun deleteFile(item: DownloadEntity) {
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                item.outputUri?.let { publisher.delete(Uri.parse(it)) } ?: true
            }
            if (deleted) {
                downloads.markFileMissing(item.id)
                _messages.value = LibraryMessage.Deleted
            } else {
                _messages.value = LibraryMessage.DeleteFailed
            }
        }
    }

    fun onFileMissing(item: DownloadEntity) {
        viewModelScope.launch { downloads.markFileMissing(item.id) }
        _messages.value = LibraryMessage.FileMissing
    }

    fun removeRecord(item: DownloadEntity) {
        viewModelScope.launch { downloads.removeRecord(item.id) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            downloads.clearHistory()
            _messages.value = LibraryMessage.HistoryCleared
        }
    }

    fun messageShown() {
        _messages.value = null
    }

    private fun DownloadEntity.matches(filter: LibraryFilter): Boolean = when (filter) {
        LibraryFilter.ALL -> true
        LibraryFilter.VIDEO -> mediaKind == MediaKind.VIDEO
        LibraryFilter.AUDIO -> mediaKind == MediaKind.AUDIO
        LibraryFilter.IMAGE -> mediaKind == MediaKind.IMAGE
        LibraryFilter.OTHER -> mediaKind == MediaKind.DOCUMENT || mediaKind == MediaKind.OTHER
    }
}

enum class LibraryMessage { Deleted, DeleteFailed, FileMissing, HistoryCleared, NoAppToOpen }
