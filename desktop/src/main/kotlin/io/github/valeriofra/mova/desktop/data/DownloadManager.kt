package io.github.valeriofra.mova.desktop.data

import io.github.valeriofra.mova.core.analysis.DownloadOptions
import io.github.valeriofra.mova.core.analysis.FormatChoice
import io.github.valeriofra.mova.core.analysis.MediaItem
import io.github.valeriofra.mova.core.extractor.ExtractionFailure
import io.github.valeriofra.mova.core.extractor.YtDlp
import io.github.valeriofra.mova.core.file.FileNames
import io.github.valeriofra.mova.core.model.MediaKind
import io.github.valeriofra.mova.core.model.MediaSource
import io.github.valeriofra.mova.desktop.engine.EngineDownloadException
import io.github.valeriofra.mova.desktop.net.DownloadFailure
import io.github.valeriofra.mova.desktop.net.HttpFileDownloader
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Baixa o conteúdo de plataformas (yt-dlp) para dentro de uma pasta de trabalho. */
fun interface MediaDownloader {
    suspend fun download(task: DownloadTask, workDir: File, onProgress: (YtDlp.Progress) -> Unit): File
}

/** Avisos para a interface (notificação ao concluir ou falhar). */
sealed interface DownloadEvent {
    val task: DownloadTask

    data class Completed(override val task: DownloadTask) : DownloadEvent
    data class Failed(override val task: DownloadTask) : DownloadEvent
}

/** Resultado de uma exclusão automática. */
data class CleanupResult(val filesDeleted: Int, val recordsRemoved: Int)

/**
 * Fila de downloads do desktop: respeita o limite de downloads simultâneos,
 * pausa, retoma, cancela e salva os arquivos concluídos na pasta escolhida.
 */
class DownloadManager(
    private val store: DownloadStore,
    private val settings: SettingsStore,
    private val media: MediaDownloader,
    private val http: HttpFileDownloader,
    private val workDirFor: (Long) -> File,
    private val defaultDownloadsDir: File,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<DownloadEvent> = _events.asSharedFlow()

    /** Começa a observar a fila (chamado uma vez, ao abrir o app). */
    fun start() {
        scope.launch {
            combine(store.tasks, settings.settings.map { it.maxConcurrent }.distinctUntilChanged()) { _, max -> max }
                .collect { max -> schedule(max) }
        }
    }

    /** Pasta onde os arquivos concluídos são salvos. */
    val destination: File get() = settings.current.downloadDir?.let(::File) ?: defaultDownloadsDir

    // ------------------------------------------------------------------
    // Ações
    // ------------------------------------------------------------------

    /** Coloca os itens na fila no formato escolhido. */
    fun enqueue(items: List<MediaItem>, format: FormatChoice): Int {
        if (items.isEmpty()) return 0
        val now = clock()
        val tasks = items.mapIndexed { index, item ->
            val audio = (format as? FormatChoice.Audio)?.format
            val extractor = item.source == MediaSource.EXTRACTOR
            DownloadTask(
                id = 0,
                url = item.url,
                sourceUrl = item.sourceUrl,
                platform = item.platform,
                title = item.title,
                fileName = if (audio != null) FileNames.titleOf(item.fileName) + "." + audio.extension else item.fileName,
                mimeType = audio?.mimeType ?: item.mimeType,
                mediaKind = if (audio != null) MediaKind.AUDIO else item.mediaKind,
                source = item.source,
                formatSpec = if (extractor) format.encode() else null,
                playlistIndex = item.playlistIndex,
                thumbnailUrl = item.thumbnailUrl,
                durationSeconds = item.durationSeconds,
                totalBytes = DownloadOptions.estimatedSize(item, format),
                status = TaskStatus.QUEUED,
                supportsResume = item.supportsResume,
                etag = item.etag,
                lastModified = item.lastModified,
                // Mantém a ordem escolhida pelo usuário.
                createdAt = now + index,
                updatedAt = now,
            )
        }
        return store.insert(tasks).size
    }

    fun pause(id: Long) {
        store.update(id) { if (it.status == TaskStatus.QUEUED || it.status == TaskStatus.RUNNING) it.copy(status = TaskStatus.PAUSED, bytesPerSecond = 0) else null }
        jobs[id]?.cancel()
    }

    fun resume(id: Long) {
        store.update(id) { if (it.status == TaskStatus.PAUSED) it.copy(status = TaskStatus.QUEUED) else null }
    }

    fun retry(id: Long) {
        store.update(id) {
            if (it.status == TaskStatus.FAILED || it.status == TaskStatus.CANCELED) {
                it.copy(status = TaskStatus.QUEUED, error = null, errorMessage = null, bytesPerSecond = 0)
            } else {
                null
            }
        }
    }

    fun cancel(id: Long) {
        val changed = store.update(id) { if (it.status.isActive || it.status == TaskStatus.FAILED) it.copy(status = TaskStatus.CANCELED, bytesPerSecond = 0) else null }
        if (changed != null) stopAndClean(id)
    }

    /** Tira o registro do app (o arquivo salvo continua no computador). */
    fun removeRecord(id: Long) {
        stopAndClean(id)
        store.remove(listOf(id))
    }

    /** Apaga o arquivo do computador; o registro fica no histórico. */
    fun deleteFile(id: Long): Boolean {
        val task = store.get(id) ?: return false
        val file = task.outputPath?.let(::File)
        val deleted = file == null || !file.exists() || file.delete()
        if (deleted) store.update(id) { it.copy(fileMissing = true) }
        return deleted
    }

    /** Remove do histórico o que não tem mais arquivo (erros, cancelados e apagados). */
    fun clearHistory(): Int {
        val ids = store.all.filter { it.status == TaskStatus.FAILED || it.status == TaskStatus.CANCELED || (it.status == TaskStatus.COMPLETED && it.fileMissing) }.map { it.id }
        ids.forEach(::stopAndClean)
        store.remove(ids)
        return ids.size
    }

    /** Marca como ausentes os arquivos apagados fora do app. */
    fun verifyFiles() {
        store.all.filter { it.status == TaskStatus.COMPLETED && !it.fileMissing }.forEach { task ->
            val path = task.outputPath ?: return@forEach
            if (!File(path).exists()) store.update(task.id) { it.copy(fileMissing = true) }
        }
    }

    /**
     * Exclusão automática: apaga os arquivos baixados há mais de [days] dias e
     * tira do app os registros desse período. Fila e downloads em andamento ficam.
     */
    fun deleteOlderThan(days: Int): CleanupResult {
        if (days <= 0) return CleanupResult(0, 0)
        val cutoff = clock() - TimeUnit.DAYS.toMillis(days.toLong())
        var deleted = 0
        val remove = mutableListOf<Long>()
        store.all.filter { it.status.isFinished && (it.completedAt ?: it.updatedAt) < cutoff }.forEach { task ->
            val file = task.outputPath?.let(::File)
            when {
                task.status != TaskStatus.COMPLETED || task.fileMissing || file == null || !file.exists() -> remove += task.id
                file.delete() -> {
                    deleted++
                    remove += task.id
                }
                // Sem permissão para apagar: mantém e tenta de novo depois.
            }
        }
        remove.forEach { workDirFor(it).deleteRecursively() }
        store.remove(remove)
        return CleanupResult(deleted, remove.size)
    }

    /** Espaço ocupado por partes de downloads incompletos. */
    fun temporaryBytes(root: File): Long = root.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    /** Apaga partes que não pertencem a nenhum download que ainda pode continuar. */
    fun cleanTemporary(root: File): Long {
        val keep = store.all.filter { it.status.isActive || it.status == TaskStatus.FAILED }.map { it.id.toString() }.toSet()
        var freed = 0L
        root.listFiles()?.filter { it.name !in keep }?.forEach { dir ->
            freed += dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
            dir.deleteRecursively()
        }
        return freed
    }

    // ------------------------------------------------------------------
    // Execução
    // ------------------------------------------------------------------

    @Synchronized
    private fun schedule(max: Int = settings.current.maxConcurrent) {
        val free = max - jobs.size
        if (free <= 0) return
        store.all
            .filter { it.status == TaskStatus.QUEUED && it.id !in jobs.keys }
            .sortedWith(compareBy({ it.createdAt }, { it.id }))
            .take(free)
            .forEach(::launch)
    }

    private fun launch(task: DownloadTask) {
        val job = scope.launch(Dispatchers.IO, start = kotlinx.coroutines.CoroutineStart.LAZY) { run(task.id) }
        jobs[task.id] = job
        job.invokeOnCompletion {
            jobs.remove(task.id)
            schedule()
        }
        job.start()
    }

    private suspend fun run(id: Long) {
        val task = store.update(id) { if (it.status == TaskStatus.QUEUED) it.copy(status = TaskStatus.RUNNING, error = null, errorMessage = null, bytesPerSecond = 0) else null }
            ?: return
        val workDir = workDirFor(id).also { it.mkdirs() }
        val progress = ProgressTracker(clock) { done, total, speed ->
            store.update(id) { current ->
                if (current.status != TaskStatus.RUNNING) null else current.copy(downloadedBytes = done, totalBytes = total ?: current.totalBytes, bytesPerSecond = speed)
            }
        }
        try {
            val downloaded = if (task.source == MediaSource.EXTRACTOR) {
                media.download(task, workDir) { p ->
                    val total = p.totalBytes
                    progress.report(if (total != null) (total * p.percent / 100f).toLong() else 0L, total, p.bytesPerSecond)
                }
            } else {
                val part = File(workDir, "download.part")
                http.download(
                    HttpFileDownloader.Request(task.url, task.supportsResume, task.totalBytes, task.etag, task.lastModified),
                    part,
                ) { done, total -> progress.report(done, total, null) }
                part
            }
            val saved = publish(downloaded, if (task.source == MediaSource.EXTRACTOR) downloaded.name else task.fileName)
            val done = store.update(id) {
                it.copy(
                    status = TaskStatus.COMPLETED, outputPath = saved.absolutePath, fileName = saved.name,
                    totalBytes = saved.length(), downloadedBytes = saved.length(), bytesPerSecond = 0,
                    completedAt = clock(), fileMissing = false,
                )
            }
            workDir.deleteRecursively()
            done?.let { _events.tryEmit(DownloadEvent.Completed(it)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val (error, message) = when (e) {
                is DownloadFailure -> e.error to (e.message ?: e.httpCode?.let { "HTTP $it" })
                is EngineDownloadException -> errorFor(e.reason) to e.message
                is IOException -> (if (e.message?.contains("space", true) == true) TaskError.INSUFFICIENT_STORAGE else TaskError.ENGINE_SETUP) to e.message
                else -> TaskError.UNKNOWN to e.toString()
            }
            val failed = store.update(id) { if (it.status == TaskStatus.RUNNING) it.copy(status = TaskStatus.FAILED, error = error, errorMessage = message, bytesPerSecond = 0) else null }
            failed?.let { _events.tryEmit(DownloadEvent.Failed(it)) }
        }
    }

    /** Move o arquivo pronto para a pasta de destino, sem sobrescrever outro com o mesmo nome. */
    private fun publish(file: File, name: String): File {
        val dir = destination.also { it.mkdirs() }
        val target = uniqueFile(dir, FileNames.sanitize(name).ifBlank { file.name })
        try {
            Files.move(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            throw DownloadFailure(TaskError.STORAGE, message = "Não foi possível salvar em ${dir.path}: ${e.message}", cause = e)
        }
        return target
    }

    private fun stopAndClean(id: Long) {
        val job = jobs[id]
        if (job == null) {
            workDirFor(id).deleteRecursively()
        } else {
            job.invokeOnCompletion { workDirFor(id).deleteRecursively() }
            job.cancel()
        }
    }

    companion object {
        /** "video.mp4" → "video (1).mp4" se já existir. */
        fun uniqueFile(dir: File, name: String): File {
            val first = File(dir, name)
            if (!first.exists()) return first
            val base = name.substringBeforeLast('.', name)
            val ext = name.substringAfterLast('.', "").let { if (it.isEmpty() || it == name) "" else ".$it" }
            var n = 1
            while (true) {
                val candidate = File(dir, "$base ($n)$ext")
                if (!candidate.exists()) return candidate
                n++
            }
        }

        fun errorFor(reason: ExtractionFailure): TaskError = when (reason) {
            ExtractionFailure.UNSUPPORTED -> TaskError.UNSUPPORTED
            ExtractionFailure.PRIVATE -> TaskError.PRIVATE
            ExtractionFailure.LOGIN_REQUIRED -> TaskError.LOGIN_REQUIRED
            ExtractionFailure.DRM -> TaskError.DRM
            ExtractionFailure.GEO_BLOCKED -> TaskError.GEO_BLOCKED
            ExtractionFailure.UNAVAILABLE -> TaskError.NOT_FOUND
            ExtractionFailure.RATE_LIMITED -> TaskError.RATE_LIMITED
            ExtractionFailure.NETWORK -> TaskError.NETWORK
            ExtractionFailure.UNKNOWN -> TaskError.EXTRACTOR
        }
    }
}

/** Agrupa as atualizações de progresso (no máximo ~3 por segundo) e calcula a velocidade. */
private class ProgressTracker(
    private val clock: () -> Long,
    private val emit: (done: Long, total: Long?, speed: Long) -> Unit,
) {
    private var lastEmit = 0L
    private var sampleTime = 0L
    private var sampleBytes = 0L
    private var speed = 0L

    fun report(done: Long, total: Long?, reportedSpeed: Long?) {
        val now = clock()
        if (reportedSpeed != null) {
            speed = reportedSpeed
        } else if (sampleTime == 0L || done < sampleBytes) {
            sampleTime = now
            sampleBytes = done
        } else if (now - sampleTime >= 1000) {
            val instant = (done - sampleBytes) * 1000 / (now - sampleTime)
            speed = if (speed == 0L) instant else (speed * 0.6 + instant * 0.4).toLong()
            sampleTime = now
            sampleBytes = done
        }
        if (now - lastEmit >= 300 || (total != null && done >= total)) {
            lastEmit = now
            emit(done, total, speed)
        }
    }
}
