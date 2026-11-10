package io.github.valeriofra.mova.download

import android.os.StatFs
import io.github.valeriofra.mova.data.db.DownloadEntity
import io.github.valeriofra.mova.data.db.DownloadError
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Falha de download com causa conhecida. */
class DownloadFailure(
    val error: DownloadError,
    val httpCode: Int? = null,
    message: String? = null,
    cause: Throwable? = null,
) : IOException(message ?: error.name, cause)

/** Dados atualizados da origem, descobertos ao iniciar a transferência. */
data class SourceInfo(
    val totalBytes: Long?,
    val supportsResume: Boolean,
    val etag: String?,
    val lastModified: String?,
)

/**
 * Transfere um arquivo por HTTP(S) para um arquivo local, retomando de onde
 * parou quando o servidor permite (cabeçalho Range).
 */
class HttpDownloader(
    private val client: OkHttpClient,
    private val minFreeBytes: Long = 32L * 1024 * 1024,
    /** Espaço livre no volume do arquivo; null quando não for possível medir. */
    private val availableBytes: (File) -> Long? = { file ->
        runCatching { StatFs(file.parentFile!!.absolutePath).availableBytes }.getOrNull()
    },
) {

    /**
     * Baixa [task] para [file]. Retorna o tamanho final do arquivo.
     * [onSourceInfo] é chamado uma vez com os dados da origem; [onProgress] durante a transferência.
     */
    suspend fun download(
        task: DownloadEntity,
        file: File,
        onSourceInfo: suspend (SourceInfo) -> Unit,
        onProgress: suspend (downloaded: Long, total: Long?) -> Unit,
    ): Long = withContext(Dispatchers.IO) {
        var existing = if (file.exists()) file.length() else 0L
        if (existing > 0 && !task.supportsResume) {
            file.delete()
            existing = 0L
        }
        val knownTotal = task.totalBytes
        if (knownTotal != null && knownTotal > 0 && existing == knownTotal) {
            onProgress(existing, knownTotal)
            return@withContext existing
        }

        val request = Request.Builder()
            .url(task.url)
            .header("User-Agent", USER_AGENT)
            .apply {
                if (existing > 0) {
                    header("Range", "bytes=$existing-")
                    val validator = task.etag?.takeUnless { it.startsWith("W/") } ?: task.lastModified
                    if (validator != null) header("If-Range", validator)
                }
            }
            .build()

        try {
            client.newCall(request).executeCancellable { response ->
                handleResponse(response, file, existing, onSourceInfo, onProgress)
            }
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            if (e is DownloadFailure) throw e
            throw DownloadFailure(DownloadError.NETWORK, message = e.message, cause = e)
        }
    }

    private suspend fun handleResponse(
        response: Response,
        file: File,
        existing: Long,
        onSourceInfo: suspend (SourceInfo) -> Unit,
        onProgress: suspend (Long, Long?) -> Unit,
    ): Long {
        val code = response.code
        when {
            code == 416 -> {
                // O servidor diz que não há mais bytes: o arquivo pode já estar completo.
                val total = parseContentRangeTotal(response.header("Content-Range"))
                if (total != null && existing == total) return existing
                file.delete()
                throw DownloadFailure(DownloadError.SERVER, code, "Intervalo inválido; recomeçando")
            }
            code == 401 || code == 403 || code == 407 || code == 451 ->
                throw DownloadFailure(DownloadError.ACCESS_DENIED, code)
            code == 404 || code == 410 -> throw DownloadFailure(DownloadError.NOT_FOUND, code)
            code == 429 || code >= 500 -> throw DownloadFailure(DownloadError.SERVER, code)
            code !in 200..299 -> throw DownloadFailure(DownloadError.UNKNOWN, code)
        }

        val partial = code == 206
        val startOffset: Long
        val total: Long?
        if (partial) {
            val rangeStart = parseContentRangeStart(response.header("Content-Range"))
            if (rangeStart != existing) {
                file.delete()
                throw DownloadFailure(DownloadError.SERVER, code, "Resposta parcial inesperada; recomeçando")
            }
            startOffset = existing
            total = parseContentRangeTotal(response.header("Content-Range"))
        } else {
            // O servidor ignorou o Range (ou o arquivo mudou): recomeça do zero.
            startOffset = 0L
            total = response.body.contentLength().takeIf { it >= 0 }
        }

        val acceptsRanges = partial || response.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
        onSourceInfo(
            SourceInfo(
                totalBytes = total,
                supportsResume = acceptsRanges && total != null,
                etag = response.header("ETag"),
                lastModified = response.header("Last-Modified"),
            ),
        )

        if (total != null) ensureSpace(file, total - startOffset)

        var downloaded = startOffset
        onProgress(downloaded, total)
        try {
            FileOutputStream(file, partial).use { output ->
                val source = response.body.byteStream()
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = source.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    onProgress(downloaded, total)
                }
                output.flush()
            }
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            if (e is DownloadFailure) throw e
            val noSpace = e.message?.contains("ENOSPC") == true || e.message?.contains("No space", true) == true
            throw DownloadFailure(
                if (noSpace) DownloadError.INSUFFICIENT_STORAGE else DownloadError.NETWORK,
                message = e.message,
                cause = e,
            )
        }

        if (total != null && downloaded != total) {
            throw DownloadFailure(DownloadError.NETWORK, message = "Transferência incompleta ($downloaded de $total)")
        }
        onProgress(downloaded, total ?: downloaded)
        return downloaded
    }

    private fun ensureSpace(file: File, needed: Long) {
        val available = availableBytes(file) ?: return
        if (available < needed + minFreeBytes) {
            throw DownloadFailure(DownloadError.INSUFFICIENT_STORAGE)
        }
    }

    companion object {
        const val USER_AGENT = "MOVA/0.1 (Android)"
        private const val BUFFER_SIZE = 64 * 1024

        private val CONTENT_RANGE = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""")

        internal fun parseContentRangeStart(header: String?): Long? =
            header?.let { CONTENT_RANGE.find(it)?.groupValues?.get(1)?.toLongOrNull() }

        internal fun parseContentRangeTotal(header: String?): Long? =
            header?.let { CONTENT_RANGE.find(it)?.groupValues?.get(3)?.toLongOrNull() }
                ?: header?.substringAfterLast('/')?.trim()?.toLongOrNull()
    }
}
