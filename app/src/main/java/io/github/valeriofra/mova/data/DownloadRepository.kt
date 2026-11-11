package io.github.valeriofra.mova.data

import io.github.valeriofra.mova.core.analysis.DownloadOptions
import io.github.valeriofra.mova.core.analysis.FormatChoice
import io.github.valeriofra.mova.core.analysis.MediaItem
import io.github.valeriofra.mova.core.file.FileNames
import io.github.valeriofra.mova.core.model.MediaKind
import io.github.valeriofra.mova.core.model.MediaSource
import io.github.valeriofra.mova.data.db.DownloadDao
import io.github.valeriofra.mova.data.db.DownloadEntity
import io.github.valeriofra.mova.data.db.DownloadStatus
import io.github.valeriofra.mova.download.DownloadScheduler
import io.github.valeriofra.mova.download.PartialFiles
import kotlinx.coroutines.flow.Flow

/** Ponto único para criar e controlar tarefas de download. */
class DownloadRepository(
    private val dao: DownloadDao,
    private val scheduler: DownloadScheduler,
    private val partialFiles: PartialFiles,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val queue: Flow<List<DownloadEntity>> = dao.observeQueue()
    val library: Flow<List<DownloadEntity>> = dao.observeLibrary()
    val history: Flow<List<DownloadEntity>> = dao.observeHistory()

    fun recent(limit: Int): Flow<List<DownloadEntity>> = dao.observeRecent(limit)

    fun recentlyCompleted(limit: Int): Flow<List<DownloadEntity>> = dao.observeRecentlyCompleted(limit)

    /** Coloca os itens na fila, no formato escolhido, e garante que o processamento comece. */
    suspend fun enqueue(items: List<MediaItem>, format: FormatChoice = FormatChoice.Original): Int {
        if (items.isEmpty()) return 0
        val now = clock()
        val entities = items.mapIndexed { index, item ->
            val audio = (format as? FormatChoice.Audio)?.format
            val isExtractor = item.source == MediaSource.EXTRACTOR
            val fileName = when {
                audio != null -> FileNames.titleOf(item.fileName) + "." + audio.extension
                else -> item.fileName
            }
            DownloadEntity(
                url = item.url,
                sourceUrl = item.sourceUrl,
                platform = item.platform,
                fileName = fileName,
                title = item.title,
                mimeType = audio?.mimeType ?: item.mimeType,
                mediaKind = if (audio != null) MediaKind.AUDIO else item.mediaKind,
                totalBytes = DownloadOptions.estimatedSize(item, format),
                status = DownloadStatus.QUEUED,
                supportsResume = item.supportsResume,
                etag = item.etag,
                lastModified = item.lastModified,
                // Mantém a ordem escolhida pelo usuário.
                createdAt = now + index,
                updatedAt = now,
                source = item.source,
                formatSpec = if (isExtractor) format.encode() else null,
                thumbnailUrl = item.thumbnailUrl,
                durationSeconds = item.durationSeconds,
                playlistIndex = item.playlistIndex,
            )
        }
        dao.insertAll(entities)
        scheduler.schedule()
        return entities.size
    }

    suspend fun pause(id: Long) {
        dao.transition(id, listOf(DownloadStatus.QUEUED, DownloadStatus.RUNNING), DownloadStatus.PAUSED, clock())
    }

    suspend fun resume(id: Long) {
        if (dao.transition(id, listOf(DownloadStatus.PAUSED), DownloadStatus.QUEUED, clock()) > 0) {
            scheduler.schedule()
        }
    }

    suspend fun retry(id: Long) {
        if (dao.retry(id, clock()) > 0) scheduler.schedule()
    }

    suspend fun cancel(id: Long) {
        val active = listOf(
            DownloadStatus.QUEUED,
            DownloadStatus.RUNNING,
            DownloadStatus.PAUSED,
            DownloadStatus.FAILED,
        )
        if (dao.transition(id, active, DownloadStatus.CANCELED, clock()) > 0) {
            partialFiles.delete(id)
        }
    }

    /** Remove o registro do histórico (o arquivo salvo, se existir, não é apagado aqui). */
    suspend fun removeRecord(id: Long) {
        dao.delete(id)
        partialFiles.delete(id)
    }

    suspend fun clearHistory(): Int = dao.clearHistory()

    suspend fun markFileMissing(id: Long) = dao.markFileMissing(id, clock())

    /** Se houver tarefas pendentes (ex.: após reiniciar o aparelho), garante o processamento. */
    suspend fun ensureProcessing() {
        if (dao.pendingCount() > 0) scheduler.schedule()
    }

    /** Apaga arquivos parciais que não pertencem a nenhuma tarefa ativa. */
    suspend fun cleanTemporaryFiles(): Long {
        val keep = dao.idsWithPossiblePartialFiles().toSet()
        return partialFiles.deleteOrphans(keep)
    }

    fun temporaryFilesSize(): Long = partialFiles.totalSize()
}
