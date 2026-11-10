package io.github.valeriofra.mova.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Insert
    suspend fun insertAll(items: List<DownloadEntity>): List<Long>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun get(id: Long): DownloadEntity?

    @Query("SELECT status FROM downloads WHERE id = :id")
    fun observeStatus(id: Long): Flow<DownloadStatus?>

    // --- Fila -----------------------------------------------------------------

    @Query(
        "SELECT * FROM downloads WHERE status IN ('QUEUED','RUNNING','PAUSED','FAILED') " +
            "ORDER BY createdAt ASC, id ASC",
    )
    fun observeQueue(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' ORDER BY completedAt DESC LIMIT :limit")
    fun observeRecentlyCompleted(limit: Int): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'QUEUED' ORDER BY createdAt ASC, id ASC LIMIT :limit")
    suspend fun nextQueued(limit: Int): List<DownloadEntity>

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'QUEUED'")
    fun observeQueuedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'QUEUED'")
    suspend fun queuedCount(): Int

    @Query("SELECT COUNT(*) FROM downloads WHERE status IN ('QUEUED','RUNNING')")
    suspend fun pendingCount(): Int

    @Query("SELECT * FROM downloads WHERE status = 'RUNNING' ORDER BY createdAt ASC")
    suspend fun running(): List<DownloadEntity>

    @Query("SELECT id FROM downloads WHERE status IN ('QUEUED','RUNNING','PAUSED','FAILED')")
    suspend fun idsWithPossiblePartialFiles(): List<Long>

    // --- Biblioteca e histórico ---------------------------------------------------

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' AND fileMissing = 0 ORDER BY completedAt DESC")
    fun observeLibrary(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN ('COMPLETED','FAILED','CANCELED') ORDER BY updatedAt DESC")
    fun observeHistory(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<DownloadEntity>>

    // --- Transições de estado ----------------------------------------------------

    /** Muda o estado apenas se o atual estiver em [from]. Retorna as linhas alteradas. */
    @Query("UPDATE downloads SET status = :to, bytesPerSecond = 0, updatedAt = :now WHERE id = :id AND status IN (:from)")
    suspend fun transition(id: Long, from: List<DownloadStatus>, to: DownloadStatus, now: Long): Int

    /** Tarefas marcadas como em andamento por um processo que foi encerrado. */
    @Query("UPDATE downloads SET status = 'QUEUED', bytesPerSecond = 0, updatedAt = :now WHERE status = 'RUNNING'")
    suspend fun requeueInterrupted(now: Long): Int

    @Query(
        "UPDATE downloads SET status = 'QUEUED', errorCode = NULL, httpCode = NULL, updatedAt = :now " +
            "WHERE id = :id AND status = 'FAILED'",
    )
    suspend fun retry(id: Long, now: Long): Int

    @Query(
        "UPDATE downloads SET downloadedBytes = :downloaded, totalBytes = :total, bytesPerSecond = :speed, " +
            "updatedAt = :now WHERE id = :id",
    )
    suspend fun updateProgress(id: Long, downloaded: Long, total: Long?, speed: Long, now: Long)

    @Query(
        "UPDATE downloads SET supportsResume = :supportsResume, etag = :etag, lastModified = :lastModified, " +
            "totalBytes = :total, updatedAt = :now WHERE id = :id",
    )
    suspend fun updateSourceInfo(
        id: Long,
        supportsResume: Boolean,
        etag: String?,
        lastModified: String?,
        total: Long?,
        now: Long,
    )

    @Query(
        "UPDATE downloads SET status = 'COMPLETED', fileName = :fileName, outputUri = :uri, " +
            "outputLocation = :location, downloadedBytes = :size, totalBytes = :size, bytesPerSecond = 0, " +
            "errorCode = NULL, httpCode = NULL, completedAt = :now, updatedAt = :now " +
            "WHERE id = :id AND status = 'RUNNING'",
    )
    suspend fun markCompleted(id: Long, fileName: String, uri: String, location: String, size: Long, now: Long): Int

    @Query(
        "UPDATE downloads SET status = 'FAILED', errorCode = :error, httpCode = :httpCode, bytesPerSecond = 0, " +
            "updatedAt = :now WHERE id = :id AND status = 'RUNNING'",
    )
    suspend fun markFailed(id: Long, error: DownloadError, httpCode: Int?, now: Long): Int

    @Query("UPDATE downloads SET fileMissing = 1, updatedAt = :now WHERE id = :id")
    suspend fun markFileMissing(id: Long, now: Long)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)

    /** Limpa registros encerrados sem arquivo. Arquivos da biblioteca são preservados. */
    @Query("DELETE FROM downloads WHERE status IN ('FAILED','CANCELED') OR (status = 'COMPLETED' AND fileMissing = 1)")
    suspend fun clearHistory(): Int

    @Query("DELETE FROM downloads WHERE status = 'FAILED'")
    suspend fun clearFailed(): Int
}
