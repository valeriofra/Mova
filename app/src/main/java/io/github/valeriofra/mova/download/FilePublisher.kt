package io.github.valeriofra.mova.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import io.github.valeriofra.mova.data.db.DownloadError
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Arquivo salvo em local acessível ao usuário. */
data class PublishedFile(
    val uri: Uri,
    val fileName: String,
    /** Descrição legível do local, ex.: "Download/MOVA". */
    val location: String,
)

/**
 * Move um download concluído para a pasta final: por padrão Download/MOVA
 * (MediaStore, sem pedir permissão), ou uma pasta escolhida pelo usuário.
 */
class FilePublisher(private val context: Context) {

    private val resolver get() = context.contentResolver

    suspend fun publish(
        source: File,
        fileName: String,
        mimeType: String?,
        treeUri: String?,
        treeName: String?,
    ): PublishedFile {
        ensureSpace(source.length())
        if (treeUri != null) {
            val tree = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) }.getOrNull()
            if (tree != null && tree.canWrite()) {
                return publishToTree(tree, source, fileName, mimeType, treeName ?: tree.name ?: "Pasta escolhida")
            }
            // Sem acesso à pasta escolhida (ex.: permissão revogada): usa o padrão.
        }
        return publishToDownloads(source, fileName, mimeType)
    }

    private suspend fun publishToDownloads(source: File, fileName: String, mimeType: String?): PublishedFile {
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType ?: "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: throw DownloadFailure(DownloadError.STORAGE, message = "Não foi possível criar o arquivo")
        var success = false
        try {
            copy(source, uri)
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            success = true
        } finally {
            if (!success) runCatching { resolver.delete(uri, null, null) }
        }
        val finalName = queryDisplayName(uri) ?: fileName
        return PublishedFile(uri, finalName, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
    }

    private suspend fun publishToTree(
        tree: DocumentFile,
        source: File,
        fileName: String,
        mimeType: String?,
        treeName: String,
    ): PublishedFile {
        val document = tree.createFile(mimeType ?: "application/octet-stream", fileName)
            ?: throw DownloadFailure(DownloadError.STORAGE, message = "Não foi possível criar o arquivo na pasta")
        var success = false
        try {
            copy(source, document.uri)
            success = true
        } finally {
            if (!success) runCatching { document.delete() }
        }
        return PublishedFile(document.uri, document.name ?: fileName, treeName)
    }

    private suspend fun copy(source: File, target: Uri) {
        try {
            val output = resolver.openOutputStream(target, "w")
                ?: throw DownloadFailure(DownloadError.STORAGE, message = "Sem acesso ao arquivo de destino")
            output.use { out ->
                source.inputStream().use { input ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: IOException) {
            if (e is DownloadFailure) throw e
            val noSpace = e.message?.contains("ENOSPC") == true || e.message?.contains("No space", true) == true
            throw DownloadFailure(
                if (noSpace) DownloadError.INSUFFICIENT_STORAGE else DownloadError.STORAGE,
                message = e.message,
                cause = e,
            )
        }
    }

    /** Apaga um arquivo salvo (MediaStore ou pasta escolhida). */
    fun delete(uri: Uri): Boolean = runCatching {
        if (DocumentsContract.isDocumentUri(context, uri)) {
            DocumentsContract.deleteDocument(resolver, uri)
        } else {
            resolver.delete(uri, null, null) > 0
        }
    }.getOrDefault(false)

    /** Verifica se o arquivo ainda existe (o usuário pode tê-lo apagado por fora). */
    fun exists(uri: Uri): Boolean =
        try {
            resolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (_: FileNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }

    private fun queryDisplayName(uri: Uri): String? =
        runCatching {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()

    private fun ensureSpace(size: Long) {
        val available = runCatching {
            StatFs(Environment.getExternalStorageDirectory().absolutePath).availableBytes
        }.getOrNull() ?: return
        if (available < size + MIN_FREE_BYTES) {
            throw DownloadFailure(DownloadError.INSUFFICIENT_STORAGE)
        }
    }

    companion object {
        const val FOLDER = "MOVA"
        private const val MIN_FREE_BYTES = 16L * 1024 * 1024
    }
}
