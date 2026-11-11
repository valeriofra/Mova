package io.github.valeriofra.mova.download

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import io.github.valeriofra.mova.AppContainer
import io.github.valeriofra.mova.MovaApplication
import io.github.valeriofra.mova.core.extractor.YtDlp
import io.github.valeriofra.mova.core.file.FileNames
import io.github.valeriofra.mova.core.file.MimeTypes
import io.github.valeriofra.mova.core.model.MediaSource
import io.github.valeriofra.mova.data.db.DownloadEntity
import io.github.valeriofra.mova.data.db.DownloadError
import io.github.valeriofra.mova.data.db.DownloadStatus
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Garante que nunca haja dois processadores da fila no mesmo processo. */
private object QueueLock {
    val mutex = Mutex()
}

/** O usuário pausou ou cancelou a tarefa enquanto ela rodava. */
private class UserInterruption : Exception()

/**
 * Processa a fila de downloads: executa até N tarefas ao mesmo tempo
 * (configurável), atualiza o progresso no banco e publica os arquivos
 * concluídos. Roda como serviço em primeiro plano para não ser interrompido.
 */
class DownloadQueueWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val container: AppContainer get() = (applicationContext as MovaApplication).container
    private val dao get() = container.database.downloadDao()

    override suspend fun getForegroundInfo(): ForegroundInfo = container.notifier.foregroundInfo(emptyList())

    override suspend fun doWork(): Result = QueueLock.mutex.withLock {
        // Tarefas "em andamento" de uma execução anterior que foi encerrada.
        dao.requeueInterrupted(now())
        if (dao.queuedCount() == 0) return@withLock Result.success()

        promoteToForeground()
        try {
            runQueue()
        } finally {
            withContext(NonCancellable) { container.notifier.cancelProgress() }
        }
        Result.success()
    }

    private suspend fun promoteToForeground() {
        try {
            setForeground(container.notifier.foregroundInfo(emptyList()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Android 12+ pode impedir o serviço em primeiro plano quando o app está
            // em segundo plano. O trabalho continua normalmente; se o sistema o
            // interromper, as tarefas voltam para a fila e são retomadas depois.
            Log.w(TAG, "Não foi possível iniciar em primeiro plano", e)
        }
    }

    private suspend fun runQueue() = coroutineScope {
        val c = container
        val active = HashMap<Long, Job>()
        val wake = Channel<Unit>(Channel.CONFLATED)

        // Acorda o laço quando a fila ou as preferências mudam.
        val watcher = launch {
            combine(dao.observeQueuedCount(), c.settings.settings) { _, _ -> Unit }
                .collect { wake.trySend(Unit) }
        }
        val progress = launch {
            while (true) {
                c.notifier.updateProgress(dao.running())
                delay(PROGRESS_NOTIFICATION_INTERVAL_MS)
            }
        }

        try {
            while (true) {
                active.entries.removeAll { it.value.isCompleted }
                val settings = c.settings.current()
                val networkOk = c.network.isConnected() && !(settings.wifiOnly && c.network.isMetered())
                if (!networkOk) {
                    // Sem a rede exigida: devolve as tarefas à fila e espera a rede voltar.
                    active.values.forEach { it.cancel() }
                    active.values.joinAll()
                    c.scheduler.schedule()
                    break
                }

                val free = settings.maxConcurrent - active.size
                if (free > 0) {
                    for (task in dao.nextQueued(free)) {
                        val claimed = dao.transition(
                            task.id,
                            listOf(DownloadStatus.QUEUED),
                            DownloadStatus.RUNNING,
                            now(),
                        ) == 1
                        if (claimed) {
                            val job = launch { runTask(task) }
                            job.invokeOnCompletion { wake.trySend(Unit) }
                            active[task.id] = job
                        }
                    }
                }

                if (active.isEmpty() && dao.queuedCount() == 0) break
                wake.receive()
            }
        } finally {
            watcher.cancel()
            progress.cancelAndJoin()
        }
    }

    /** Transferência concluída e arquivo publicado. */
    private class Completed(val published: PublishedFile, val size: Long)

    private suspend fun runTask(task: DownloadEntity) {
        val c = container
        try {
            val completed = coroutineScope {
                val statusWatcher = launch {
                    dao.observeStatus(task.id).first { it != DownloadStatus.RUNNING }
                    throw UserInterruption()
                }
                val result = transferAndPublish(task)
                statusWatcher.cancel()
                result
            }
            val published = completed.published
            val updated = dao.markCompleted(
                id = task.id,
                fileName = published.fileName,
                uri = published.uri.toString(),
                location = published.location,
                size = completed.size,
                now = now(),
            )
            c.partialFiles.delete(task.id)
            if (updated == 0) {
                // Cancelado no último instante: não mantém o arquivo.
                c.publisher.delete(published.uri)
            } else {
                dao.get(task.id)?.let { c.notifier.notifyFinished(it, success = true) }
            }
        } catch (_: UserInterruption) {
            handleUserInterruption(task.id)
        } catch (e: DownloadFailure) {
            if (e.error == DownloadError.NETWORK && !c.network.isConnected()) {
                // A conexão caiu: a tarefa volta para a fila e continua quando a rede voltar.
                dao.transition(task.id, listOf(DownloadStatus.RUNNING), DownloadStatus.QUEUED, now())
            } else {
                Log.w(TAG, "Falha no download ${task.id}: ${e.error} ${e.httpCode}", e)
                if (e.error == DownloadError.NOT_FOUND || e.error == DownloadError.ACCESS_DENIED) {
                    c.partialFiles.delete(task.id)
                }
                if (dao.markFailed(task.id, e.error, e.httpCode, now()) > 0) {
                    dao.get(task.id)?.let { c.notifier.notifyFinished(it, success = false) }
                }
            }
        } catch (e: CancellationException) {
            // Trabalho interrompido pelo sistema: a tarefa volta para a fila.
            withContext(NonCancellable) {
                dao.transition(task.id, listOf(DownloadStatus.RUNNING), DownloadStatus.QUEUED, now())
            }
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Erro inesperado no download ${task.id}", e)
            if (dao.markFailed(task.id, DownloadError.UNKNOWN, null, now()) > 0) {
                dao.get(task.id)?.let { c.notifier.notifyFinished(it, success = false) }
            }
        }
    }

    private suspend fun handleUserInterruption(id: Long) {
        val current = dao.get(id)
        when (current?.status) {
            null, DownloadStatus.CANCELED -> container.partialFiles.delete(id)
            DownloadStatus.PAUSED -> if (!current.supportsResume) container.partialFiles.delete(id)
            else -> Unit
        }
    }

    private suspend fun transferAndPublish(initial: DownloadEntity): Completed {
        val c = container
        val file: File
        val fileName: String
        val mimeType: String?
        when (initial.source) {
            MediaSource.EXTRACTOR -> {
                file = downloadWithExtractor(initial)
                fileName = FileNames.sanitize(file.name).ifEmpty { initial.fileName }
                mimeType = MimeTypes.extensionOf(fileName)?.let(MimeTypes::mimeForExtension) ?: initial.mimeType
            }
            MediaSource.DIRECT -> {
                file = c.partialFiles.fileFor(initial.id)
                val task = downloadDirect(initial, file)
                fileName = task.fileName
                mimeType = task.mimeType
            }
        }

        val size = file.length()
        val settings = c.settings.current()
        val published = withContext(Dispatchers.IO) {
            c.publisher.publish(
                source = file,
                fileName = fileName,
                mimeType = mimeType,
                treeUri = settings.storageTreeUri,
                treeName = settings.storageTreeName,
            )
        }
        return Completed(published, size)
    }

    /** Link direto: transferência HTTP com retomada. */
    private suspend fun downloadDirect(initial: DownloadEntity, file: File): DownloadEntity {
        val c = container
        var task = initial
        val reporter = ProgressReporter(task.id)
        var attempt = 0
        while (true) {
            try {
                c.downloader.download(
                    task = task,
                    file = file,
                    onSourceInfo = { info ->
                        task = task.copy(
                            totalBytes = info.totalBytes,
                            supportsResume = info.supportsResume,
                            etag = info.etag,
                            lastModified = info.lastModified,
                        )
                        dao.updateSourceInfo(
                            id = task.id,
                            supportsResume = info.supportsResume,
                            etag = info.etag,
                            lastModified = info.lastModified,
                            total = info.totalBytes,
                            now = now(),
                        )
                    },
                    onProgress = { done, total -> reporter.report(done, total) },
                )
                return task
            } catch (e: DownloadFailure) {
                if (!e.error.retryable || attempt >= RETRY_DELAYS_MS.size || !c.network.isConnected()) throw e
                delay(RETRY_DELAYS_MS[attempt++])
            }
        }
    }

    /** Página de plataforma: download pelo yt-dlp (vídeo até 1080p ou áudio). */
    private suspend fun downloadWithExtractor(task: DownloadEntity): File = coroutineScope {
        val c = container
        val dir = c.partialFiles.dirFor(task.id)
        val reporter = ProgressReporter(task.id)
        val progress = MutableStateFlow<YtDlp.Progress?>(null)
        val writer = launch {
            progress.filterNotNull().collect { p ->
                val total = p.totalBytes
                val done = if (total != null) (total * (p.percent / 100.0)).toLong() else 0L
                reporter.report(done, total, p.bytesPerSecond)
            }
        }
        try {
            var attempt = 0
            while (true) {
                try {
                    return@coroutineScope c.ytDlp.download(task, dir) { progress.value = it }
                } catch (e: DownloadFailure) {
                    if (!e.error.retryable || attempt >= RETRY_DELAYS_MS.size || !c.network.isConnected()) throw e
                    delay(RETRY_DELAYS_MS[attempt++])
                }
            }
            @Suppress("UNREACHABLE_CODE")
            error("inalcançável")
        } finally {
            writer.cancel()
        }
    }

    /** Grava o progresso no banco no máximo a cada 500 ms, com velocidade suavizada. */
    private inner class ProgressReporter(private val id: Long) {
        private var lastTime = -1L
        private var lastBytes = 0L
        private var speed = 0.0

        suspend fun report(done: Long, total: Long?, knownSpeed: Long? = null) {
            val nowElapsed = SystemClock.elapsedRealtime()
            if (lastTime < 0) {
                lastTime = nowElapsed
                lastBytes = done
                dao.updateProgress(id, done, total, 0, now())
                return
            }
            val elapsed = nowElapsed - lastTime
            if (elapsed < PROGRESS_DB_INTERVAL_MS) return
            val instant = (done - lastBytes).coerceAtLeast(0) * 1000.0 / elapsed
            speed = when {
                knownSpeed != null -> knownSpeed.toDouble()
                speed == 0.0 -> instant
                else -> speed * 0.6 + instant * 0.4
            }
            lastTime = nowElapsed
            lastBytes = done
            dao.updateProgress(id, done, total, speed.toLong(), now())
        }
    }

    private fun now() = System.currentTimeMillis()

    private companion object {
        const val TAG = "MovaDownloads"
        const val PROGRESS_DB_INTERVAL_MS = 500L
        const val PROGRESS_NOTIFICATION_INTERVAL_MS = 1_000L
        val RETRY_DELAYS_MS = longArrayOf(2_000, 5_000, 10_000)
    }
}
