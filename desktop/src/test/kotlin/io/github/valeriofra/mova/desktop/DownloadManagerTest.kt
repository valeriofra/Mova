package io.github.valeriofra.mova.desktop

import io.github.valeriofra.mova.core.analysis.FormatChoice
import io.github.valeriofra.mova.core.analysis.MediaItem
import io.github.valeriofra.mova.core.extractor.YtDlp
import io.github.valeriofra.mova.core.model.MediaKind
import io.github.valeriofra.mova.core.model.MediaSource
import io.github.valeriofra.mova.core.model.Platform
import io.github.valeriofra.mova.desktop.data.CleanupResult
import io.github.valeriofra.mova.desktop.data.DownloadManager
import io.github.valeriofra.mova.desktop.data.DownloadStore
import io.github.valeriofra.mova.desktop.data.DownloadTask
import io.github.valeriofra.mova.desktop.data.MediaDownloader
import io.github.valeriofra.mova.desktop.data.SettingsStore
import io.github.valeriofra.mova.desktop.data.TaskStatus
import io.github.valeriofra.mova.desktop.i18n.Localization
import io.github.valeriofra.mova.desktop.net.HttpFileDownloader
import io.github.valeriofra.mova.desktop.system.AppPaths
import java.io.File
import java.nio.file.Files
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DownloadManagerTest {
    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var settings: SettingsStore
    private lateinit var store: DownloadStore
    private val server = MockWebServer()

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("mova-manager").toFile()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        settings = SettingsStore(File(dir, "settings.json"))
        store = DownloadStore(File(dir, "downloads.json"), scope)
        server.start()
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.close()
        dir.deleteRecursively()
    }

    private fun manager(clock: () -> Long = System::currentTimeMillis, media: MediaDownloader) = DownloadManager(
        store = store,
        settings = settings,
        media = media,
        http = HttpFileDownloader(OkHttpClient()),
        workDirFor = { File(dir, "work/$it") },
        defaultDownloadsDir = File(dir, "Downloads/MOVA"),
        scope = scope,
        clock = clock,
    ).also { it.start() }

    private fun item(title: String, source: MediaSource = MediaSource.EXTRACTOR, url: String = "https://youtu.be/$title") = MediaItem(
        url = url, sourceUrl = url, platform = if (source == MediaSource.EXTRACTOR) Platform.YOUTUBE else Platform.DIRECT,
        fileName = "$title.mp4", title = title, mimeType = "video/mp4", mediaKind = MediaKind.VIDEO, sizeBytes = null,
        supportsResume = true, etag = null, lastModified = null, source = source,
    )

    private fun waitFor(predicate: (List<DownloadTask>) -> Boolean): List<DownloadTask> = runBlocking {
        withTimeout(10_000) { store.tasks.first(predicate) }
    }

    @Test
    fun downloadsAndSavesWithoutOverwriting() {
        File(dir, "Downloads/MOVA").mkdirs()
        File(dir, "Downloads/MOVA/Clipe.mp4").writeText("já existia")
        val manager = manager { task, work, progress ->
            progress(YtDlp.Progress(50f, 1000, 100))
            File(work, "${task.title}.mp4").apply { writeText("conteúdo") }
        }
        manager.enqueue(listOf(item("Clipe")), FormatChoice.Video(720))
        val done = waitFor { tasks -> tasks.all { it.status == TaskStatus.COMPLETED } }.single()
        assertEquals("Clipe (1).mp4", done.fileName)
        assertEquals("conteúdo", File(done.outputPath!!).readText())
        assertEquals("video:720", done.formatSpec)
        assertFalse(File(dir, "work/${done.id}").exists())
    }

    @Test
    fun respectsConcurrencyLimit() {
        settings.setMaxConcurrent(1)
        val release = CompletableDeferred<Unit>()
        var running = 0
        var maxRunning = 0
        val manager = manager { task, work, _ ->
            synchronized(this) { running++; maxRunning = maxOf(maxRunning, running) }
            release.await()
            synchronized(this) { running-- }
            File(work, "${task.title}.mp4").apply { writeText("x") }
        }
        manager.enqueue(listOf(item("A"), item("B"), item("C")), FormatChoice.Video(1080))
        waitFor { tasks -> tasks.count { it.status == TaskStatus.RUNNING } == 1 }
        Thread.sleep(300)
        assertEquals(1, store.all.count { it.status == TaskStatus.RUNNING })
        release.complete(Unit)
        waitFor { tasks -> tasks.all { it.status == TaskStatus.COMPLETED } }
        assertEquals(1, maxRunning)
    }

    @Test
    fun pauseAndCancelStopTheWork() {
        val started = CompletableDeferred<Unit>()
        val manager = manager { _, _, _ ->
            started.complete(Unit)
            kotlinx.coroutines.awaitCancellation()
        }
        manager.enqueue(listOf(item("Longo")), FormatChoice.Video(1080))
        runBlocking { withTimeout(5_000) { started.await() } }
        val id = store.all.single().id
        manager.pause(id)
        waitFor { it.single().status == TaskStatus.PAUSED }
        manager.cancel(id)
        waitFor { it.single().status == TaskStatus.CANCELED }
    }

    @Test
    fun failureIsRecordedAndRetryable() {
        var attempts = 0
        val manager = manager { task, work, _ ->
            attempts++
            if (attempts == 1) throw io.github.valeriofra.mova.desktop.engine.EngineDownloadException(io.github.valeriofra.mova.core.extractor.ExtractionFailure.NETWORK, "timed out")
            File(work, "${task.title}.mp4").apply { writeText("ok") }
        }
        manager.enqueue(listOf(item("Instavel")), FormatChoice.Video(1080))
        val failed = waitFor { it.single().status == TaskStatus.FAILED }.single()
        assertEquals(io.github.valeriofra.mova.desktop.data.TaskError.NETWORK, failed.error)
        manager.retry(failed.id)
        waitFor { it.single().status == TaskStatus.COMPLETED }
    }

    @Test
    fun directLinkUsesHttp() {
        server.enqueue(MockResponse.Builder().body("12345").addHeader("Content-Type", "video/mp4").build())
        val manager = manager { _, _, _ -> error("não deveria usar o yt-dlp") }
        manager.enqueue(listOf(item("arquivo", MediaSource.DIRECT, server.url("/arquivo.mp4").toString())), FormatChoice.Original)
        val done = waitFor { it.single().status == TaskStatus.COMPLETED }.single()
        assertEquals("arquivo.mp4", done.fileName)
        assertEquals("12345", File(done.outputPath!!).readText())
    }

    @Test
    fun autoDeleteRemovesOldFilesAndRecords() {
        var now = TimeUnit.DAYS.toMillis(100)
        val manager = manager(clock = { now }) { task, work, _ -> File(work, "${task.title}.mp4").apply { writeText("x") } }
        manager.enqueue(listOf(item("Antigo")), FormatChoice.Video(1080))
        val old = waitFor { it.single().status == TaskStatus.COMPLETED }.single()
        now += TimeUnit.DAYS.toMillis(20)
        manager.enqueue(listOf(item("Novo")), FormatChoice.Video(1080))
        waitFor { tasks -> tasks.size == 2 && tasks.all { it.status == TaskStatus.COMPLETED } }

        assertEquals(CleanupResult(0, 0), manager.deleteOlderThan(0))
        val result = manager.deleteOlderThan(15)
        assertEquals(CleanupResult(filesDeleted = 1, recordsRemoved = 1), result)
        assertFalse(File(old.outputPath!!).exists())
        assertEquals(listOf("Novo"), store.all.map { it.title })
    }

    @Test
    fun interruptedDownloadsReturnToQueueOnRestart() {
        val file = File(dir, "restart.json")
        file.writeText("""[{"id":7,"url":"u","sourceUrl":"u","platform":"YOUTUBE","title":"t","fileName":"t.mp4","mediaKind":"VIDEO","source":"EXTRACTOR","status":"RUNNING","createdAt":1,"updatedAt":1}]""")
        val reopened = DownloadStore(file, scope)
        assertEquals(TaskStatus.QUEUED, reopened.all.single().status)
    }
}

class LocalizationTest {
    @Test
    fun followsSystemThenCountryThenEnglish() {
        assertEquals("pt", Localization.resolve(null, Locale.forLanguageTag("pt-BR")).language)
        assertEquals("pt", Localization.resolve(null, Locale.forLanguageTag("de-BR")).language)
        assertEquals("BR", Localization.resolve(null, Locale.forLanguageTag("de-BR")).country)
        assertEquals("es", Localization.resolve(null, Locale.forLanguageTag("ca-ES")).language)
        assertEquals("ja", Localization.resolve(null, Locale.forLanguageTag("ja-JP")).language)
        assertEquals("en", Localization.effectiveLanguage(Locale.forLanguageTag("ja-JP")))
        assertEquals("fr", Localization.resolve("fr", Locale.forLanguageTag("pt-BR")).language)
    }
}

class AppPathsTest {
    @Test
    fun readsXdgDownloadFolder() {
        val home = Files.createTempDirectory("home").toFile()
        File(home, ".config").mkdirs()
        File(home, ".config/user-dirs.dirs").writeText("XDG_DESKTOP_DIR=\"\$HOME/Área de trabalho\"\nXDG_DOWNLOAD_DIR=\"\$HOME/Transferências\"\n")
        assertEquals(File(home, "Transferências"), AppPaths.xdgDownloadDir(home) { null })
        assertTrue(home.deleteRecursively())
    }
}
