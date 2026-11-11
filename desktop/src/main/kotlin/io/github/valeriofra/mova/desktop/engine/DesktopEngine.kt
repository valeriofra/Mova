package io.github.valeriofra.mova.desktop.engine

import io.github.valeriofra.mova.core.analysis.FormatChoice
import io.github.valeriofra.mova.core.extractor.ExtractionFailure
import io.github.valeriofra.mova.core.extractor.ExtractionResult
import io.github.valeriofra.mova.core.extractor.MediaExtractor
import io.github.valeriofra.mova.core.extractor.YtDlp
import io.github.valeriofra.mova.core.file.FileNames
import io.github.valeriofra.mova.desktop.system.OsInfo
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Situação do mecanismo de download, exibida em Ajustes e na análise. */
sealed interface EngineState {
    data object NotReady : EngineState

    /** Baixando os componentes no primeiro uso. */
    data class Preparing(
        val component: ComponentId,
        val step: Int,
        val steps: Int,
        val downloadedBytes: Long,
        val totalBytes: Long?,
    ) : EngineState

    data class Updating(val version: String?) : EngineState
    data class Ready(val version: String?, val systemFfmpeg: Boolean) : EngineState
    data class Failed(val message: String?) : EngineState
}

/** Falha do yt-dlp ao baixar, com o motivo classificado. */
class EngineDownloadException(val reason: ExtractionFailure, message: String?) : IOException(message)

/**
 * O yt-dlp no computador: prepara os componentes, mantém o yt-dlp atualizado
 * (canal nightly, como no Android), analisa links e baixa no formato escolhido.
 */
class DesktopEngine(
    private val engineDir: File,
    private val installer: ComponentInstaller,
    private val os: OsInfo = OsInfo.current,
    private val runner: ProcessRunner = ProcessRunner(),
    /** Última atualização (epoch ms) e como gravá-la. */
    private val lastUpdate: () -> Long,
    private val saveLastUpdate: (Long) -> Unit,
    /** FFmpeg já instalado no sistema (Linux), se houver. */
    private val systemFfmpeg: () -> File? = { findOnPath(if (os.isWindows) null else "ffmpeg") },
    private val clock: () -> Long = System::currentTimeMillis,
) : MediaExtractor {

    private val layout = Components.Layout(engineDir, os)
    private val setupMutex = Mutex()
    private val updateMutex = Mutex()
    private val _state = MutableStateFlow<EngineState>(EngineState.NotReady)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    @Volatile
    private var version: String? = null

    @Volatile
    private var updatedThisSession = false

    /** Componentes que faltam neste computador. */
    fun missingComponents(): List<ComponentSpec> = Components.specsFor(os).filter { spec ->
        when (spec.id) {
            ComponentId.FFMPEG -> systemFfmpeg() == null && !layout.ffmpeg.isFile
            else -> !layout.mainFile(spec.id).isFile
        }
    }

    /** Garante que tudo esteja instalado (baixa o que faltar no primeiro uso). */
    suspend fun ensureReady() {
        if (_state.value is EngineState.Ready && missingComponents().isEmpty()) return
        setupMutex.withLock {
            val missing = missingComponents()
            try {
                missing.forEachIndexed { index, spec ->
                    _state.value = EngineState.Preparing(spec.id, index + 1, missing.size, 0, spec.approxBytes)
                    installer.install(spec) { done, total ->
                        _state.value = EngineState.Preparing(spec.id, index + 1, missing.size, done, total ?: spec.approxBytes)
                    }
                }
                version = readVersion()
                _state.value = EngineState.Ready(version, systemFfmpeg() != null)
            } catch (e: CancellationException) {
                _state.value = EngineState.NotReady
                throw e
            } catch (e: Exception) {
                _state.value = EngineState.Failed(e.message)
                throw e
            }
        }
    }

    /** Atualiza o yt-dlp para a compilação nightly mais recente. */
    suspend fun update(): UpdateResult = updateMutex.withLock {
        try {
            ensureReady()
            val before = version ?: readVersion()
            _state.value = EngineState.Updating(before)
            val result = runner.run(listOf(layout.ytDlp.absolutePath, "--update-to", "nightly"), environment(), keepLines = 40)
            updatedThisSession = true
            saveLastUpdate(clock())
            val after = readVersion()
            version = after
            _state.value = EngineState.Ready(after, systemFfmpeg() != null)
            when {
                !result.ok -> UpdateResult.Failed(errorLine(result.combined))
                after != null && after != before -> UpdateResult.Updated(after)
                else -> UpdateResult.UpToDate(after)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = if (missingComponents().isEmpty()) EngineState.Ready(version, systemFfmpeg() != null) else EngineState.Failed(e.message)
            UpdateResult.Failed(e.message)
        }
    }

    /** Prepara o mecanismo e atualiza o yt-dlp se a última verificação tiver mais de um dia. */
    suspend fun warmUp() {
        runCatching { ensureReady() }
        if (missingComponents().isEmpty() && clock() - lastUpdate() > UPDATE_INTERVAL_MS) update()
    }

    override suspend fun extract(url: String, allowPlaylist: Boolean): ExtractionResult {
        ensureReady()
        val first = analyze(url, allowPlaylist)
        // Plataformas mudam com frequência: se falhar, atualiza o yt-dlp e tenta de novo (uma vez por sessão).
        if (first is ExtractionResult.Failure && first.reason in RETRY_AFTER_UPDATE && !updatedThisSession) {
            if (update() is UpdateResult.Updated) return analyze(url, allowPlaylist)
        }
        return first
    }

    private suspend fun analyze(url: String, allowPlaylist: Boolean): ExtractionResult {
        val result = runner.run(command(YtDlp.analyzeArgs(allowPlaylist) + url), environment(), keepLines = null)
        if (!result.ok) {
            val message = errorLine(result.stderr.ifBlank { result.stdout })
            return ExtractionResult.Failure(YtDlp.classifyError(result.combined), message)
        }
        return YtDlp.parse(result.stdout)
    }

    /**
     * Baixa para dentro de [workDir] e devolve o arquivo final.
     * Lança [EngineDownloadException] com o motivo quando o yt-dlp falha.
     */
    suspend fun download(
        url: String,
        choice: FormatChoice,
        playlistIndex: Int?,
        title: String,
        referer: String?,
        workDir: File,
        onProgress: (YtDlp.Progress) -> Unit,
    ): File {
        ensureReady()
        workDir.mkdirs()
        val effective = if (choice == FormatChoice.Original) FormatChoice.Video(FormatChoice.MAX_RESOLUTION) else choice
        val args = YtDlp.downloadArgs(
            choice = effective,
            outputDir = workDir.absolutePath,
            playlistIndex = playlistIndex,
            fileBaseName = FileNames.sanitize(title),
            referer = referer,
        ) + listOf("--newline", url)
        val result = runner.run(command(args), environment(), keepLines = 60) { line ->
            YtDlp.parseProgress(line)?.let(onProgress)
        }
        if (!result.ok) {
            throw EngineDownloadException(YtDlp.classifyError(result.combined), errorLine(result.combined))
        }
        return resultFile(workDir) ?: throw EngineDownloadException(ExtractionFailure.UNKNOWN, "O arquivo baixado não foi encontrado")
    }

    /** yt-dlp + opções comuns (FFmpeg, JavaScript, cache dentro da pasta do app). */
    internal fun command(args: List<String>): List<String> = buildList {
        add(layout.ytDlp.absolutePath)
        val ffmpeg = systemFfmpeg()?.parentFile ?: layout.ffmpegDir.takeIf { layout.ffmpeg.isFile }
        if (ffmpeg != null) {
            add("--ffmpeg-location"); add(ffmpeg.absolutePath)
        }
        if (layout.jsRuntime.isFile) {
            add("--js-runtimes"); add("quickjs:${layout.jsRuntime.absolutePath}")
        }
        add("--cache-dir"); add(File(engineDir, "cache").absolutePath)
        addAll(args)
    }

    /** Saída em UTF-8 (títulos com acentos) mesmo no console do Windows. */
    private fun environment(): Map<String, String> = mapOf("PYTHONUTF8" to "1", "PYTHONIOENCODING" to "utf-8")

    private suspend fun readVersion(): String? = runCatching {
        runner.run(listOf(layout.ytDlp.absolutePath, "--version"), environment(), keepLines = 5)
            .takeIf { it.ok }?.stdout?.lineSequence()?.map { it.trim() }?.lastOrNull { it.isNotEmpty() }
    }.getOrNull()

    sealed interface UpdateResult {
        data class Updated(val version: String?) : UpdateResult
        data class UpToDate(val version: String?) : UpdateResult
        data class Failed(val message: String?) : UpdateResult
    }

    companion object {
        const val UPDATE_INTERVAL_MS = 24L * 60 * 60 * 1000

        // Formatos intermediários do yt-dlp: "titulo.f137.mp4", "titulo.f251-drc.webm".
        private val INTERMEDIATE = Regex("""\.f\d[\w-]*\.\w+$""")

        private val RETRY_AFTER_UPDATE = setOf(
            ExtractionFailure.UNKNOWN,
            ExtractionFailure.LOGIN_REQUIRED,
            ExtractionFailure.UNAVAILABLE,
            ExtractionFailure.UNSUPPORTED,
        )

        /** O arquivo final: ignora partes temporárias e formatos intermediários. */
        fun resultFile(dir: File): File? =
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

        /** A linha "ERROR: ..." do yt-dlp (ou a última linha com texto). */
        fun errorLine(output: String?): String? =
            output?.lineSequence()?.map { it.trim() }?.lastOrNull { it.startsWith("ERROR") }
                ?.removePrefix("ERROR:")?.trim()
                ?: output?.lineSequence()?.lastOrNull { it.isNotBlank() }?.trim()

        /** Procura um executável no PATH. */
        fun findOnPath(name: String?, path: String? = System.getenv("PATH")): File? {
            if (name == null || path.isNullOrBlank()) return null
            return path.split(File.pathSeparatorChar)
                .asSequence()
                .filter { it.isNotBlank() }
                .map { File(it, name) }
                .firstOrNull { it.isFile && it.canExecute() }
        }
    }
}
