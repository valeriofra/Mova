package io.github.valeriofra.mova

import android.content.Context
import io.github.valeriofra.mova.core.analysis.LinkAnalyzer
import io.github.valeriofra.mova.data.DownloadRepository
import io.github.valeriofra.mova.data.db.MovaDatabase
import io.github.valeriofra.mova.data.settings.SettingsRepository
import io.github.valeriofra.mova.download.DownloadNotifier
import io.github.valeriofra.mova.download.DownloadScheduler
import io.github.valeriofra.mova.download.FilePublisher
import io.github.valeriofra.mova.download.HttpDownloader
import io.github.valeriofra.mova.download.NetworkMonitor
import io.github.valeriofra.mova.download.OkHttpProbe
import io.github.valeriofra.mova.download.PartialFiles
import io.github.valeriofra.mova.download.YtDlpEngine
import io.github.valeriofra.mova.ui.components.ThumbnailLoader
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

/**
 * Injeção de dependência manual: o app ainda é pequeno o bastante para não
 * justificar um framework (decisão prevista no planejamento).
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: MovaDatabase by lazy { MovaDatabase.create(appContext) }
    val settings = SettingsRepository(appContext)
    val network = NetworkMonitor(appContext)
    val partialFiles = PartialFiles(appContext)
    val notifier = DownloadNotifier(appContext)
    val publisher = FilePublisher(appContext)
    val thumbnails by lazy { ThumbnailLoader(appContext) { httpClient } }

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    val downloader by lazy { HttpDownloader(httpClient) }
    /** yt-dlp no aparelho: baixa das plataformas (YouTube, Instagram, TikTok...). */
    val ytDlp = YtDlpEngine(appContext)
    val analyzer by lazy { LinkAnalyzer(OkHttpProbe(httpClient), extractor = ytDlp) }
    val scheduler = DownloadScheduler(appContext, settings)
    val downloads by lazy { DownloadRepository(database.downloadDao(), scheduler, partialFiles) }
}
