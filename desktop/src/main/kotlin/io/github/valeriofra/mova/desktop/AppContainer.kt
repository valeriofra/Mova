package io.github.valeriofra.mova.desktop

import io.github.valeriofra.mova.core.analysis.FormatChoice
import io.github.valeriofra.mova.core.analysis.LinkAnalyzer
import io.github.valeriofra.mova.desktop.data.DownloadManager
import io.github.valeriofra.mova.desktop.data.DownloadStore
import io.github.valeriofra.mova.desktop.data.DownloadTask
import io.github.valeriofra.mova.desktop.data.MediaDownloader
import io.github.valeriofra.mova.desktop.data.SettingsStore
import io.github.valeriofra.mova.desktop.engine.ComponentInstaller
import io.github.valeriofra.mova.desktop.engine.DesktopEngine
import io.github.valeriofra.mova.desktop.net.Http
import io.github.valeriofra.mova.desktop.net.HttpFileDownloader
import io.github.valeriofra.mova.desktop.net.HttpProbe
import io.github.valeriofra.mova.desktop.system.AppPaths
import io.github.valeriofra.mova.desktop.system.OsInfo
import io.github.valeriofra.mova.desktop.ui.components.ThumbnailLoader
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Dependências do app desktop (injeção manual, como no Android). */
class AppContainer(val paths: AppPaths, val os: OsInfo = OsInfo.current) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val http = Http.client()

    val settings = SettingsStore(paths.settingsFile)
    val store = DownloadStore(paths.downloadsFile, scope)
    val engine = DesktopEngine(
        engineDir = paths.engineDir,
        installer = ComponentInstaller(http, paths.engineDir),
        os = os,
        lastUpdate = { settings.current.lastEngineUpdate },
        saveLastUpdate = settings::setLastEngineUpdate,
    )
    val analyzer = LinkAnalyzer(HttpProbe(http), extractor = engine)
    val thumbnails = ThumbnailLoader(http)
    val manager = DownloadManager(
        store = store,
        settings = settings,
        media = MediaDownloader { task, dir, onProgress ->
            engine.download(task.url, FormatChoice.decode(task.formatSpec), task.playlistIndex, task.title, refererFor(task), dir, onProgress)
        },
        http = HttpFileDownloader(http),
        workDirFor = paths::workDirFor,
        defaultDownloadsDir = paths.defaultDownloadsDir,
        scope = scope,
    )

    /** Começa a fila, prepara o mecanismo e agenda a exclusão automática. */
    fun start() {
        manager.start()
        manager.verifyFiles()
        scope.launch {
            delay(WARM_UP_DELAY_MS)
            runCatching { engine.warmUp() }
        }
        scope.launch {
            while (isActive) {
                val days = settings.current.autoDeleteDays
                if (days > 0) runCatching { manager.deleteOlderThan(days) }
                delay(TimeUnit.HOURS.toMillis(6))
            }
        }
    }

    fun shutdown() {
        store.flush()
    }

    private companion object {
        const val WARM_UP_DELAY_MS = 2_000L

        /** Quando o arquivo vem de outro domínio (CDN), informa a página de origem. */
        fun refererFor(task: DownloadTask): String? {
            val fileHost = runCatching { URI(task.url).host }.getOrNull()
            val pageHost = runCatching { URI(task.sourceUrl).host }.getOrNull()
            return task.sourceUrl.takeIf { fileHost != null && pageHost != null && fileHost != pageHost }
        }
    }
}
