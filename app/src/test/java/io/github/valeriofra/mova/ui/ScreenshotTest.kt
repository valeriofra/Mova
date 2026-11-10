package io.github.valeriofra.mova.ui

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.valeriofra.mova.core.analysis.AnalysisReport
import io.github.valeriofra.mova.core.analysis.LinkAnalysis
import io.github.valeriofra.mova.core.analysis.MediaItem
import io.github.valeriofra.mova.core.link.LinkRecognizer
import io.github.valeriofra.mova.core.model.MediaKind
import io.github.valeriofra.mova.core.model.Platform
import io.github.valeriofra.mova.data.db.DownloadEntity
import io.github.valeriofra.mova.data.db.DownloadError
import io.github.valeriofra.mova.data.db.DownloadStatus
import io.github.valeriofra.mova.data.settings.MovaSettings
import io.github.valeriofra.mova.ui.analysis.AnalysisContent
import io.github.valeriofra.mova.ui.analysis.AnalysisUiState
import io.github.valeriofra.mova.ui.analysis.DownloadOptionsContent
import io.github.valeriofra.mova.ui.analysis.ItemSelectionContent
import io.github.valeriofra.mova.ui.home.HomeContent
import io.github.valeriofra.mova.ui.home.LinkPreview
import io.github.valeriofra.mova.ui.library.LibraryContent
import io.github.valeriofra.mova.ui.library.LibraryUiState
import io.github.valeriofra.mova.ui.queue.QueueContent
import io.github.valeriofra.mova.ui.queue.QueueUiState
import io.github.valeriofra.mova.ui.settings.SettingsContent
import io.github.valeriofra.mova.ui.theme.MovaColors
import io.github.valeriofra.mova.ui.theme.MovaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renderiza as telas principais em PNG (app/build/outputs/roborazzi) para
 * revisão visual sem emulador. Usa dados de exemplo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi", application = Application::class)
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            MovaTheme {
                Surface(color = MovaColors.Background, modifier = Modifier.fillMaxSize()) { content() }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private val mb = 1024L * 1024
    private val now = 1_762_800_000_000L

    private fun entity(
        id: Long,
        name: String,
        kind: MediaKind,
        status: DownloadStatus,
        total: Long?,
        done: Long = 0,
        speed: Long = 0,
        error: DownloadError? = null,
        resume: Boolean = true,
    ) = DownloadEntity(
        id = id,
        url = "https://exemplo.com/$name",
        sourceUrl = "https://exemplo.com/$name",
        platform = Platform.DIRECT,
        fileName = name,
        title = name,
        mimeType = null,
        mediaKind = kind,
        totalBytes = total,
        downloadedBytes = done,
        bytesPerSecond = speed,
        status = status,
        errorCode = error,
        supportsResume = resume,
        etag = null,
        lastModified = null,
        outputLocation = "Download/MOVA",
        createdAt = now - id * 60_000,
        updatedAt = now - id * 60_000,
        completedAt = if (status == DownloadStatus.COMPLETED) now - id * 60_000 else null,
    )

    private fun item(name: String, kind: MediaKind, size: Long, resume: Boolean = true) = MediaItem(
        url = "https://exemplo.com/midia/$name",
        sourceUrl = "https://exemplo.com/midia/$name",
        platform = Platform.DIRECT,
        fileName = name,
        title = name.substringBeforeLast('.'),
        mimeType = null,
        mediaKind = kind,
        sizeBytes = size,
        supportsResume = resume,
        etag = null,
        lastModified = null,
    )

    private val recent = listOf(
        entity(1, "Video 02.mp4", MediaKind.VIDEO, DownloadStatus.RUNNING, 64 * mb, 46 * mb),
        entity(2, "Video 01.mp4", MediaKind.VIDEO, DownloadStatus.COMPLETED, 48 * mb, 48 * mb),
        entity(3, "Audio.mp3", MediaKind.AUDIO, DownloadStatus.COMPLETED, 8 * mb, 8 * mb),
    )

    @Test
    fun home() = shot("01_inicio") {
        HomeContent("", LinkPreview.Empty, recent, {}, {}, {}, {}, {}, {})
    }

    @Test
    fun homeRecognized() {
        val url = "https://youtu.be/dQw4w9WgXcQ"
        val preview = LinkPreview.Single(LinkRecognizer().recognize(url)!!)
        shot("02_inicio_link_reconhecido") {
            HomeContent(url, preview, emptyList(), {}, {}, {}, {}, {}, {})
        }
    }

    @Test
    fun analysisLoading() {
        compose.mainClock.autoAdvance = false
        shot("03_analisando") {
            AnalysisContent(AnalysisUiState.Loading, {}, {}, {}, {})
        }
    }

    @Test
    fun analysisReady() {
        val input = "https://exemplo.com/midia/Video%2001.mp4"
        val link = LinkRecognizer().recognize(input)!!
        val report = AnalysisReport(
            input,
            listOf(LinkAnalysis.Ready(input, link, listOf(item("Video 01.mp4", MediaKind.VIDEO, 48 * mb)))),
        )
        shot("04_conteudo_encontrado") {
            AnalysisContent(AnalysisUiState.Done(report), {}, {}, {}, {})
        }
    }

    @Test
    fun options() = shot("05_opcoes_de_download") {
        DownloadOptionsContent(
            items = listOf(
                item("Video 01.mp4", MediaKind.VIDEO, 48 * mb),
                item("Video 02.mp4", MediaKind.VIDEO, 63 * mb),
                item("Faixa.mp3", MediaKind.AUDIO, 8 * mb),
            ),
            onConfirm = {},
        )
    }

    @Test
    fun selection() {
        val items = listOf(
            item("Video 01.mp4", MediaKind.VIDEO, 48 * mb),
            item("Video 02.mp4", MediaKind.VIDEO, 63 * mb),
            item("Video 03.mp4", MediaKind.VIDEO, 98 * mb),
        )
        shot("06_selecao_de_itens") {
            ItemSelectionContent(items, items.take(2).map { it.url }.toSet(), {}, {}, {}, {})
        }
    }

    @Test
    fun recognizedWithoutDownload() {
        val input = "https://www.instagram.com/stories/usuario/3141592653589/"
        val link = LinkRecognizer().recognize(input)!!
        val report = AnalysisReport(input, listOf(LinkAnalysis.RecognizedWithoutDownload(input, link)))
        shot("07_plataforma_sem_download") {
            AnalysisContent(AnalysisUiState.Done(report), {}, {}, {}, {})
        }
    }

    @Test
    fun queue() {
        val state = QueueUiState(
            running = listOf(entity(1, "Video 02.mp4", MediaKind.VIDEO, DownloadStatus.RUNNING, 64 * mb, 46 * mb, (4.3 * mb).toLong())),
            queued = listOf(entity(4, "Entrevista.m4a", MediaKind.AUDIO, DownloadStatus.QUEUED, 22 * mb)),
            paused = listOf(entity(5, "Documentario.mkv", MediaKind.VIDEO, DownloadStatus.PAUSED, 700 * mb, 210 * mb)),
            failed = listOf(entity(6, "Foto.jpg", MediaKind.IMAGE, DownloadStatus.FAILED, null, error = DownloadError.NOT_FOUND)),
            recentlyCompleted = recent.filter { it.status == DownloadStatus.COMPLETED },
            loaded = true,
        )
        shot("08_fila") { QueueContent(state, {}, {}, {}, {}, {}, {}) }
    }

    @Test
    fun queueEmpty() = shot("09_fila_vazia") {
        QueueContent(QueueUiState(loaded = true), {}, {}, {}, {}, {}, {})
    }

    @Test
    fun library() {
        val files = recent.filter { it.status == DownloadStatus.COMPLETED } +
            entity(7, "Capa.png", MediaKind.IMAGE, DownloadStatus.COMPLETED, 2 * mb, 2 * mb)
        shot("10_biblioteca") {
            LibraryContent(LibraryUiState(files = files, totalFiles = files.size, loaded = true), {}, {}, {}, {}, {}, {}, {}, {})
        }
    }

    @Test
    fun settings() = shot("11_ajustes") {
        SettingsContent(MovaSettings(), 12 * mb, true, {}, {}, {}, {}, {}, {})
    }
}
