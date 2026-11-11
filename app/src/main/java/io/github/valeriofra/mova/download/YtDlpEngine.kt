package io.github.valeriofra.mova.download

import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import io.github.valeriofra.mova.core.analysis.FormatChoice
import io.github.valeriofra.mova.core.extractor.ExtractionFailure
import io.github.valeriofra.mova.core.extractor.ExtractionResult
import io.github.valeriofra.mova.core.extractor.MediaExtractor
import io.github.valeriofra.mova.core.extractor.YtDlp
import io.github.valeriofra.mova.core.file.FileNames
import io.github.valeriofra.mova.data.db.DownloadEntity
import io.github.valeriofra.mova.data.db.DownloadError
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Mecanismo de extração: o yt-dlp executado no próprio aparelho (com Python,
 * FFmpeg e QuickJS embutidos). Obtém informações das plataformas e baixa os
 * vídeos/áudios no formato escolhido.
 */
class YtDlpEngine(private val context: Context) : MediaExtractor {

    sealed interface EngineState {
        data object NotReady : EngineState
        data object Preparing : EngineState
        data class Ready(val version: String?) : EngineState
        data class Updating(val version: String?) : EngineState
        data class Failed(val message: String?) : EngineState
    }

    sealed interface UpdateResult {
        data class Updated(val version: String?) : UpdateResult
        data class UpToDate(val version: String?) : UpdateResult
        data class Failed(val message: String?) : UpdateResult
    }

    private val initMutex = Mutex()
    private val updateMutex = Mutex()

    @Volatile
    private var ready = false

    @Volatile
    private var updatedThisSession = false

    private val prefs = context.getSharedPreferences("ytdlp", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<EngineState>(EngineState.NotReady)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    /** Prepara o mecanismo (na primeira vez, extrai Python e FFmpeg: alguns segundos). */
    suspend fun ensureReady() {
        if (ready) return
        initMutex.withLock {
            if (ready) return
            _state.value = EngineState.Preparing
            try {
                withContext(Dispatchers.IO) {
                    YoutubeDL.getInstance().init(context)
                    FFmpeg.getInstance().init(context)
                }
                ready = true
                _state.value = EngineState.Ready(version())
            } catch (e: CancellationException) {
                _state.value = EngineState.NotReady
                throw e
            } catch (e: Throwable) {
                // Inclui Errors de bibliotecas nativas/reflexão: nunca derrubar o app.
                Log.e(TAG, "Falha ao preparar o mecanismo", e)
                _state.value = EngineState.Failed(e.toString())
                throw IOException("Não foi possível preparar o mecanismo de download: $e", e)
            }
        }
    }

    fun version(): String? = runCatching { YoutubeDL.getInstance().versionName(context) }.getOrNull()
        ?: runCatching { YoutubeDL.getInstance().version(context) }.getOrNull()

    // ------------------------------------------------------------------
    // Análise
    // ------------------------------------------------------------------

    override suspend fun extract(url: String, allowPlaylist: Boolean): ExtractionResult {
        // A versão embutida no APK envelhece rápido: no primeiro uso, atualiza antes.
        if (!updatedThisSession && prefs.getLong(KEY_LAST_UPDATE, 0L) == 0L) update()
        val first = runExtract(url, allowPlaylist)
        // As plataformas mudam com frequência; uma versão nova do yt-dlp costuma resolver.
        if (first is ExtractionResult.Failure && first.reason in RETRY_AFTER_UPDATE && !updatedThisSession) {
            if (update() is UpdateResult.Updated) return runExtract(url, allowPlaylist)
        }
        return first
    }

    private suspend fun runExtract(url: String, allowPlaylist: Boolean): ExtractionResult {
        ensureReady()
        val request = YoutubeDLRequest(url).addCommands(YtDlp.analyzeArgs(allowPlaylist))
        return try {
            YtDlp.parse(execute(request, null).out)
        } catch (e: YoutubeDLException) {
            Log.w(TAG, "Falha ao analisar $url", e)
            ExtractionResult.Failure(YtDlp.classifyError(e.message), errorLine(e.message))
        } catch (e: IOException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e(TAG, "Erro inesperado ao analisar $url", e)
            ExtractionResult.Failure(ExtractionFailure.UNKNOWN, e.toString())
        }
    }

    // ------------------------------------------------------------------
    // Download
    // ------------------------------------------------------------------

    /** Baixa [task] para [dir] e retorna o arquivo final. Pode ser retomado (arquivos .part). */
    suspend fun download(task: DownloadEntity, dir: File, onProgress: (YtDlp.Progress) -> Unit): File {
        ensureReady()
        val choice = FormatChoice.decode(task.formatSpec).let {
            if (it == FormatChoice.Original) FormatChoice.Video(FormatChoice.MAX_RESOLUTION) else it
        }
        val request = YoutubeDLRequest(task.url)
            .addCommands(
                YtDlp.downloadArgs(
                    choice = choice,
                    outputDir = dir.absolutePath,
                    playlistIndex = task.playlistIndex,
                    fileBaseName = FileNames.sanitize(task.title),
                    referer = refererFor(task),
                ),
            )
        try {
            execute(request) { line ->
                // Roda numa thread da biblioteca: um erro aqui derrubaria o app.
                try {
                    YtDlp.parseProgress(line)?.let(onProgress)
                } catch (e: Throwable) {
                    Log.w(TAG, "Linha de progresso ignorada", e)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw e
        } catch (e: YoutubeDLException) {
            Log.w(TAG, "Falha no download ${task.id}", e)
            val message = e.message
            val error = if (message?.contains("No space left", ignoreCase = true) == true) {
                DownloadError.INSUFFICIENT_STORAGE
            } else {
                errorFor(YtDlp.classifyError(message))
            }
            throw DownloadFailure(error, message = errorLine(message), cause = e)
        } catch (e: Throwable) {
            Log.e(TAG, "Erro inesperado no download ${task.id}", e)
            throw DownloadFailure(DownloadError.EXTRACTOR, message = e.toString(), cause = e)
        }
        return resultFile(dir)
            ?: throw DownloadFailure(DownloadError.EXTRACTOR, message = "O arquivo baixado não foi encontrado")
    }

    /** Arquivos de CDN (achados na página) podem exigir a página de origem como Referer. */
    private fun refererFor(task: DownloadEntity): String? {
        val fileHost = android.net.Uri.parse(task.url).host
        val pageHost = android.net.Uri.parse(task.sourceUrl).host
        return task.sourceUrl.takeIf { fileHost != null && pageHost != null && fileHost != pageHost }
    }

    private fun errorFor(failure: ExtractionFailure): DownloadError = when (failure) {
        ExtractionFailure.UNSUPPORTED -> DownloadError.UNSUPPORTED
        ExtractionFailure.PRIVATE, ExtractionFailure.GEO_BLOCKED -> DownloadError.ACCESS_DENIED
        ExtractionFailure.LOGIN_REQUIRED -> DownloadError.LOGIN_REQUIRED
        ExtractionFailure.DRM -> DownloadError.DRM
        ExtractionFailure.UNAVAILABLE -> DownloadError.NOT_FOUND
        ExtractionFailure.RATE_LIMITED -> DownloadError.SERVER
        ExtractionFailure.NETWORK -> DownloadError.NETWORK
        ExtractionFailure.UNKNOWN -> DownloadError.EXTRACTOR
    }

    /** O arquivo final: ignora partes temporárias e formatos intermediários (.f137.mp4). */
    private fun resultFile(dir: File): File? =
        dir.listFiles()
            ?.filter { file ->
                file.isFile &&
                    !file.name.endsWith(".part") &&
                    !file.name.endsWith(".ytdl") &&
                    !file.name.endsWith(".temp") &&
                    !file.name.contains(".part-Frag") &&
                    !INTERMEDIATE.containsMatchIn(file.name)
            }
            ?.maxByOrNull { it.length() }

    // ------------------------------------------------------------------
    // Atualização do yt-dlp
    // ------------------------------------------------------------------

    /**
     * Atualiza o yt-dlp para a compilação "nightly" mais recente: é a que recebe
     * primeiro as correções quando as plataformas mudam (recomendada pelo projeto).
     */
    suspend fun update(): UpdateResult = updateMutex.withLock {
        try {
            ensureReady()
            _state.value = EngineState.Updating(version())
            val status = withContext(Dispatchers.IO) {
                YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.NIGHTLY)
            }
            updatedThisSession = true
            prefs.edit().putLong(KEY_LAST_UPDATE, System.currentTimeMillis()).apply()
            val version = version()
            _state.value = EngineState.Ready(version)
            if (status == YoutubeDL.UpdateStatus.DONE) UpdateResult.Updated(version) else UpdateResult.UpToDate(version)
        } catch (e: Throwable) {
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "Falha ao atualizar o yt-dlp", e)
            _state.value = if (ready) EngineState.Ready(version()) else EngineState.Failed(e.message)
            UpdateResult.Failed(e.message)
        }
    }

    /** Prepara o mecanismo e atualiza o yt-dlp se a última verificação tiver mais de um dia. */
    suspend fun warmUp() {
        runCatching { ensureReady() }
        val last = prefs.getLong(KEY_LAST_UPDATE, 0L)
        if (System.currentTimeMillis() - last > UPDATE_INTERVAL_MS) update()
    }

    // ------------------------------------------------------------------

    /**
     * Executa o yt-dlp (bloqueante, em Dispatchers.IO). Se a corrotina for
     * cancelada (pausar/cancelar), o processo é encerrado imediatamente.
     */
    private suspend fun execute(request: YoutubeDLRequest, onLine: ((String) -> Unit)?): YoutubeDLResponse =
        withContext(Dispatchers.IO) {
            val processId = UUID.randomUUID().toString()
            coroutineScope {
                val canceller = launch {
                    try {
                        awaitCancellation()
                    } finally {
                        YoutubeDL.getInstance().destroyProcessById(processId)
                    }
                }
                try {
                    YoutubeDL.getInstance().execute(request, processId) { _, _, line -> onLine?.invoke(line) }
                } catch (e: YoutubeDL.CanceledException) {
                    currentCoroutineContext().ensureActive()
                    throw IOException("Processo interrompido", e)
                } catch (e: InterruptedException) {
                    currentCoroutineContext().ensureActive()
                    throw IOException("Processo interrompido", e)
                } finally {
                    canceller.cancel()
                }
            }
        }

    private fun errorLine(message: String?): String? =
        message?.lineSequence()?.map { it.trim() }?.lastOrNull { it.startsWith("ERROR") }
            ?.removePrefix("ERROR:")?.trim()
            ?: message?.lineSequence()?.lastOrNull { it.isNotBlank() }?.trim()

    private companion object {
        const val TAG = "MovaYtDlp"
        const val KEY_LAST_UPDATE = "last_update"
        const val UPDATE_INTERVAL_MS = 24L * 60 * 60 * 1000
        // Formatos intermediários do yt-dlp: "titulo.f137.mp4", "titulo.f251-drc.webm".
        val INTERMEDIATE = Regex("""\.f\d[\w-]*\.\w+$""")
        val RETRY_AFTER_UPDATE = setOf(
            ExtractionFailure.UNKNOWN,
            ExtractionFailure.LOGIN_REQUIRED,
            ExtractionFailure.UNAVAILABLE,
            ExtractionFailure.UNSUPPORTED,
        )
    }
}
