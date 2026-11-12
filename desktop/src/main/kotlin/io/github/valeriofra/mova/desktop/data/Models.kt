package io.github.valeriofra.mova.desktop.data

import io.github.valeriofra.mova.core.model.MediaKind
import io.github.valeriofra.mova.core.model.MediaSource
import io.github.valeriofra.mova.core.model.Platform
import kotlinx.serialization.Serializable

@Serializable
enum class TaskStatus {
    QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELED;

    val isActive: Boolean get() = this == QUEUED || this == RUNNING || this == PAUSED
    val isFinished: Boolean get() = this == COMPLETED || this == FAILED || this == CANCELED
}

/** Motivo de uma falha, para explicar ao usuário. */
@Serializable
enum class TaskError(val retryable: Boolean) {
    NETWORK(true),
    SERVER(true),
    ACCESS_DENIED(false),
    NOT_FOUND(false),
    INSUFFICIENT_STORAGE(true),
    STORAGE(true),
    LOGIN_REQUIRED(false),
    PRIVATE(false),
    DRM(false),
    GEO_BLOCKED(false),
    RATE_LIMITED(true),
    UNSUPPORTED(false),
    ENGINE_SETUP(true),
    EXTRACTOR(true),
    UNKNOWN(true),
}

/** Um download (na fila, em andamento ou no histórico). */
@Serializable
data class DownloadTask(
    val id: Long,
    val url: String,
    val sourceUrl: String,
    val platform: Platform,
    val title: String,
    val fileName: String,
    val mimeType: String? = null,
    val mediaKind: MediaKind,
    val source: MediaSource,
    /** Formato escolhido (FormatChoice.encode()), para conteúdo de plataformas. */
    val formatSpec: String? = null,
    val playlistIndex: Int? = null,
    val thumbnailUrl: String? = null,
    val durationSeconds: Long? = null,
    val totalBytes: Long? = null,
    val downloadedBytes: Long = 0,
    val bytesPerSecond: Long = 0,
    val status: TaskStatus,
    val error: TaskError? = null,
    val errorMessage: String? = null,
    /** Caminho do arquivo salvo, depois de concluído. */
    val outputPath: String? = null,
    /** Pasta escolhida para este download (null = pasta padrão dos Ajustes). */
    val saveDir: String? = null,
    /** O arquivo foi apagado (pelo app ou por fora). */
    val fileMissing: Boolean = false,
    val supportsResume: Boolean = false,
    val etag: String? = null,
    val lastModified: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
) {
    val progress: Float?
        get() = totalBytes?.takeIf { it > 0 }?.let { (downloadedBytes.toFloat() / it).coerceIn(0f, 1f) }
}

/** Onde salvar os arquivos baixados. */
@Serializable
enum class SaveMode {
    /** Uma janela do sistema pergunta a pasta a cada download. */
    ASK,

    /** Sempre na pasta definida em Ajustes. */
    FOLDER,
}

/** Tema da interface. O escuro é a identidade principal do MOVA. */
@Serializable
enum class ThemeMode { DARK, LIGHT, SYSTEM }

@Serializable
data class DesktopSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    /** Idioma escolhido ("pt", "en", "es", "fr"); null = automático. */
    val language: String? = null,
    /** Perguntar a pasta a cada download ou salvar sempre em [downloadDir]. */
    val saveMode: SaveMode = SaveMode.ASK,
    /** Pasta fixa de destino; null = a pasta Downloads do sistema. */
    val downloadDir: String? = null,
    /** Última pasta escolhida na janela de salvar (ponto de partida da próxima). */
    val lastSaveDir: String? = null,
    val maxConcurrent: Int = DEFAULT_CONCURRENT,
    /** Exclusão automática depois de N dias (0 = desligada). */
    val autoDeleteDays: Int = 0,
    /** Tamanho da janela ao fechar (para reabrir igual); null = calcular pelo monitor. */
    val windowWidth: Int? = null,
    val windowHeight: Int? = null,
    val windowMaximized: Boolean? = null,
    /** Última verificação de atualização do yt-dlp (epoch ms). */
    val lastEngineUpdate: Long = 0,
) {
    companion object {
        const val DEFAULT_CONCURRENT = 2
        val CONCURRENT_RANGE = 1..3
        val AUTO_DELETE_OPTIONS = listOf(3, 15, 30)
    }
}
