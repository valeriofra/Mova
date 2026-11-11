package io.github.valeriofra.mova.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.valeriofra.mova.core.model.MediaKind
import io.github.valeriofra.mova.core.model.MediaSource
import io.github.valeriofra.mova.core.model.Platform

enum class DownloadStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELED;

    val isFinished: Boolean get() = this == COMPLETED || this == FAILED || this == CANCELED
}

enum class DownloadError(val retryable: Boolean) {
    NETWORK(true),
    SERVER(true),
    NOT_FOUND(false),
    ACCESS_DENIED(false),
    INSUFFICIENT_STORAGE(false),
    STORAGE(false),
    LOGIN_REQUIRED(false),
    DRM(false),
    UNSUPPORTED(false),
    /** Falha do mecanismo de extração (geralmente resolvida atualizando o yt-dlp). */
    EXTRACTOR(false),
    UNKNOWN(false),
}

/** Uma tarefa de download. A mesma tabela alimenta Fila, Biblioteca e Histórico. */
@Entity(
    tableName = "downloads",
    indices = [Index("status"), Index("createdAt")],
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val sourceUrl: String,
    val platform: Platform,
    val fileName: String,
    val title: String,
    val mimeType: String?,
    val mediaKind: MediaKind,
    val totalBytes: Long?,
    val downloadedBytes: Long = 0,
    val bytesPerSecond: Long = 0,
    val status: DownloadStatus,
    val errorCode: DownloadError? = null,
    val httpCode: Int? = null,
    val supportsResume: Boolean,
    val etag: String?,
    val lastModified: String?,
    val outputUri: String? = null,
    val outputLocation: String? = null,
    val fileMissing: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
    /** Link direto (HTTP) ou página processada pelo yt-dlp. */
    @ColumnInfo(defaultValue = "DIRECT") val source: MediaSource = MediaSource.DIRECT,
    /** Formato escolhido (ver FormatChoice.encode), ex.: "video:1080", "audio:mp3". */
    val formatSpec: String? = null,
    val thumbnailUrl: String? = null,
    val durationSeconds: Long? = null,
    /** Posição na playlist/publicação, quando o item é baixado por ela. */
    val playlistIndex: Int? = null,
)
