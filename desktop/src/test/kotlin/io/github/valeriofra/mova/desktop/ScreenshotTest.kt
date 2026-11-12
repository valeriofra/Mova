package io.github.valeriofra.mova.desktop

import androidx.compose.foundation.background
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.valeriofra.mova.core.analysis.AnalysisReport
import io.github.valeriofra.mova.core.analysis.FormatChoice
import io.github.valeriofra.mova.core.analysis.LinkAnalysis
import io.github.valeriofra.mova.core.analysis.MediaItem
import io.github.valeriofra.mova.core.analysis.RestrictionReason
import io.github.valeriofra.mova.core.link.LinkRecognizer
import io.github.valeriofra.mova.core.model.MediaKind
import io.github.valeriofra.mova.core.model.MediaSource
import io.github.valeriofra.mova.core.model.Platform
import io.github.valeriofra.mova.desktop.data.DownloadTask
import io.github.valeriofra.mova.desktop.data.TaskError
import io.github.valeriofra.mova.desktop.data.TaskStatus
import io.github.valeriofra.mova.desktop.engine.ComponentId
import io.github.valeriofra.mova.desktop.engine.EngineState
import io.github.valeriofra.mova.desktop.i18n.Localization
import io.github.valeriofra.mova.desktop.system.AppPaths
import io.github.valeriofra.mova.desktop.ui.Destination
import io.github.valeriofra.mova.desktop.ui.HomeModel
import io.github.valeriofra.mova.desktop.ui.HomeScreen
import io.github.valeriofra.mova.desktop.ui.MovaDesktopApp
import io.github.valeriofra.mova.desktop.ui.SettingsActions
import io.github.valeriofra.mova.desktop.ui.SettingsScreen
import io.github.valeriofra.mova.desktop.ui.SettingsTab
import io.github.valeriofra.mova.desktop.ui.components.LocalThumbnails
import io.github.valeriofra.mova.desktop.ui.theme.MovaTheme
import java.io.File
import java.nio.file.Files
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.skia.EncodedImageFormat
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Gera imagens das telas em desktop/build/screenshots (revisão visual e artefato do CI). */
class ScreenshotTest {
    private lateinit var dir: File
    private lateinit var container: AppContainer
    private val out = File("build/screenshots").also { it.mkdirs() }
    private val mb = 1024L * 1024
    private val now = System.currentTimeMillis()

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("mova-shots").toFile()
        container = AppContainer(AppPaths(File(dir, "data"), File(dir, "Downloads")))
    }

    @After
    fun tearDown() {
        container.scope.cancel()
        dir.deleteRecursively()
        Localization.apply("pt")
    }

    private fun task(
        id: Long, title: String, status: TaskStatus, total: Long? = 64 * mb, done: Long = 0, speed: Long = 0,
        kind: MediaKind = MediaKind.VIDEO, error: TaskError? = null, platform: Platform = Platform.YOUTUBE, ago: Long = 0,
    ): DownloadTask {
        val ext = if (kind == MediaKind.AUDIO) "mp3" else "mp4"
        val file = File(dir, "Downloads/$title.$ext")
        if (status == TaskStatus.COMPLETED) {
            file.parentFile.mkdirs()
            file.writeText("x")
        }
        return DownloadTask(
            id = id, url = "https://youtu.be/$id", sourceUrl = "https://youtu.be/$id", platform = platform, title = title,
            fileName = "$title.$ext", mediaKind = kind, source = MediaSource.EXTRACTOR, totalBytes = total, downloadedBytes = done,
            bytesPerSecond = speed, status = status, error = error, outputPath = file.absolutePath.takeIf { status == TaskStatus.COMPLETED },
            createdAt = now - ago - id, updatedAt = now - ago, completedAt = (now - ago).takeIf { status == TaskStatus.COMPLETED },
        )
    }

    private fun seed() {
        container.store.insert(
            listOf(
                task(1, "Documentário sobre o oceano", TaskStatus.RUNNING, 412 * mb, 187 * mb, 6 * mb),
                task(2, "Aula 03 — Fotografia noturna", TaskStatus.QUEUED, 96 * mb, platform = Platform.VIMEO),
                task(3, "Podcast episódio 42", TaskStatus.PAUSED, 58 * mb, 21 * mb, kind = MediaKind.AUDIO),
                task(4, "Reels da viagem", TaskStatus.FAILED, null, error = TaskError.LOGIN_REQUIRED, platform = Platform.INSTAGRAM),
                task(5, "Pôr do sol na praia", TaskStatus.COMPLETED, 48 * mb, 48 * mb, ago = 3_600_000),
                task(6, "Trilha sonora", TaskStatus.COMPLETED, 8 * mb, 8 * mb, kind = MediaKind.AUDIO, ago = 86_400_000),
                task(7, "Receita de pão caseiro", TaskStatus.COMPLETED, 120 * mb, 120 * mb, platform = Platform.TIKTOK, ago = 2 * 86_400_000),
            ),
        )
    }

    private fun shot(
        name: String,
        language: String = "pt",
        dark: Boolean = true,
        width: Int = 1200,
        height: Int = 780,
        content: @Composable () -> Unit,
    ) {
        Localization.apply(language)
        val scene = ImageComposeScene(width, height, Density(1.25f)) {
            MovaTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalThumbnails provides null) { content() }
            }
        }
        try {
            repeat(4) { scene.render(it * 50_000_000L) }
            val image = scene.render(1_000_000_000L)
            File(out, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            scene.close()
        }
    }

    @Composable
    private fun app(destination: Destination, home: HomeModel? = null, titleBar: Boolean = false) {
        val scope = rememberCoroutineScope()
        val model = home ?: HomeModel(container.analyzer, container.manager, scope)
        MovaDesktopApp(container, model, destination, {}, SnackbarHostState(), if (titleBar) 40.dp else 0.dp)
    }

    private fun video(title: String, url: String = "https://www.youtube.com/watch?v=dQw4w9WgXcQ", index: Int? = null) = MediaItem(
        url = url, sourceUrl = url, platform = Platform.YOUTUBE, fileName = "$title.mp4", title = title, mimeType = "video/mp4",
        mediaKind = MediaKind.VIDEO, sizeBytes = 86 * mb, supportsResume = true, etag = null, lastModified = null,
        source = MediaSource.EXTRACTOR, durationSeconds = 754, uploader = "Canal Natureza", resolutions = listOf(2160, 1080, 720, 480, 360),
        sizeByResolution = mapOf(1080 to 86 * mb, 720 to 41 * mb, 480 to 22 * mb, 360 to 14 * mb), audioSizeBytes = 11 * mb, playlistIndex = index,
    )

    private fun homeWith(analysis: (CoroutineScope) -> HomeModel.Analysis, input: String): HomeModel {
        val model = HomeModel(container.analyzer, container.manager, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        model.input = input
        model.analysis = analysis(CoroutineScope(Dispatchers.Unconfined))
        return model
    }

    @Test
    fun screens() {
        seed()
        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val link = LinkRecognizer().recognize(url)!!
        val single = homeWith({
            val item = video("Documentário: a vida nos recifes de coral")
            HomeModel.Analysis.Done(AnalysisReport(url, listOf(LinkAnalysis.Ready(url, link, listOf(item)))), setOf(item.key), FormatChoice.Video(1080))
        }, url)
        val listUrl = "https://www.youtube.com/playlist?list=PL123"
        val listLink = LinkRecognizer().recognize(listUrl)!!
        val playlist = homeWith({
            val items = (1..6).map { video("Aula $it — Fotografia para iniciantes", "https://youtu.be/v$it") }
            HomeModel.Analysis.Done(
                AnalysisReport(listUrl, listOf(LinkAnalysis.Ready(listUrl, listLink, items, collectionTitle = "Curso de fotografia"))),
                items.take(4).map { it.key }.toSet(), FormatChoice.Audio(io.github.valeriofra.mova.core.analysis.AudioFormat.MP3),
            )
        }, listUrl)
        val storyUrl = "https://www.instagram.com/stories/perfil/123/"
        val restricted = homeWith({
            HomeModel.Analysis.Done(
                AnalysisReport(storyUrl, listOf(LinkAnalysis.Restricted(storyUrl, LinkRecognizer().recognize(storyUrl)!!, RestrictionReason.LOGIN_REQUIRED, "ERROR: [Instagram] 123: login required"))),
                emptySet(), FormatChoice.Original,
            )
        }, storyUrl)

        shot("01_inicio") { app(Destination.HOME) }
        shot("02_video_encontrado") { app(Destination.HOME, single) }
        shot("03_playlist") { app(Destination.HOME, playlist) }
        shot("04_exige_login") { app(Destination.HOME, restricted) }
        shot("05_preparando_mecanismo") {
            val model = homeWith({ HomeModel.Analysis.Loading }, url)
            androidx.compose.foundation.layout.Box(
                androidx.compose.ui.Modifier.background(io.github.valeriofra.mova.desktop.ui.theme.MovaColors.Background),
            ) { HomeScreen(model, EngineState.Preparing(ComponentId.FFMPEG, 2, 3, 38 * mb, 91 * mb)) {} }
        }
        shot("06_fila") { app(Destination.QUEUE) }
        shot("07_biblioteca") { app(Destination.LIBRARY) }
        shot("08_ajustes") { app(Destination.SETTINGS) }
        listOf(SettingsTab.DOWNLOADS to "08b_ajustes_downloads", SettingsTab.STORAGE to "08c_ajustes_armazenamento", SettingsTab.ABOUT to "08d_ajustes_sobre").forEach { (tab, name) ->
            shot(name, width = 1000, height = 640) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.background(io.github.valeriofra.mova.desktop.ui.theme.MovaColors.Background)) {
                    SettingsScreen(container.settings.current, container.manager.destination.path, EngineState.Ready("2026.11.10", systemFfmpeg = false), 12L * mb, SettingsActions(), initialTab = tab)
                }
            }
        }

        shot("10_inicio_claro", dark = false) { app(Destination.HOME, single) }
        shot("11_fila_claro", dark = false) { app(Destination.QUEUE) }
        shot("12_ajustes_claro", dark = false) { app(Destination.SETTINGS) }

        // Barra de título integrada (Windows) e janela pequena (barra lateral só com ícones).
        shot("13_barra_integrada") { app(Destination.QUEUE, titleBar = true) }
        shot("14_janela_pequena", width = 900, height = 620) { app(Destination.HOME, single) }
        shot("15_ajustes_janela_pequena", width = 900, height = 620) { app(Destination.SETTINGS, titleBar = true) }

        shot("20_home_english", language = "en") { app(Destination.HOME) }
        shot("21_ajustes_espanol", language = "es") { app(Destination.SETTINGS) }
        shot("22_bibliotheque_francais", language = "fr") { app(Destination.LIBRARY) }
    }
}
