package io.github.valeriofra.mova.desktop.net

import io.github.valeriofra.mova.desktop.data.TaskError
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
    val error: TaskError,
    val httpCode: Int? = null,
    message: String? = null,
    cause: Throwable? = null,
) : IOException(message ?: error.name, cause)

/**
 * Transfere um arquivo por HTTP(S), retomando de onde parou quando o servidor
 * permite (cabeçalho Range). Usado nos links diretos de arquivos.
 */
class HttpFileDownloader(
    private val client: OkHttpClient,
    private val minFreeBytes: Long = 64L * 1024 * 1024,
    private val availableBytes: (File) -> Long? = { file -> runCatching { file.parentFile.usableSpace }.getOrNull() },
) {

    data class Request(val url: String, val supportsResume: Boolean, val totalBytes: Long?, val etag: String?, val lastModified: String?)

    /** Baixa para [file] e devolve o tamanho final. */
    suspend fun download(
        request: Request,
        file: File,
        onProgress: suspend (downloaded: Long, total: Long?) -> Unit,
    ): Long = withContext(Dispatchers.IO) {
        var existing = if (file.exists()) file.length() else 0L
        if (existing > 0 && !request.supportsResume) {
            file.delete()
            existing = 0L
        }
        val knownTotal = request.totalBytes
        if (knownTotal != null && knownTotal > 0 && existing == knownTotal) {
            onProgress(existing, knownTotal)
            return@withContext existing
        }
        val http = okhttp3.Request.Builder()
            .url(request.url)
            .header("User-Agent", Http.USER_AGENT)
            .apply {
                if (existing > 0) {
                    header("Range", "bytes=$existing-")
                    val validator = request.etag?.takeUnless { it.startsWith("W/") } ?: request.lastModified
                    if (validator != null) header("If-Range", validator)
                }
            }
            .build()
        try {
            client.newCall(http).executeCancellable { response -> handle(response, file, existing, onProgress) }
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            if (e is DownloadFailure) throw e
            throw DownloadFailure(TaskError.NETWORK, message = e.message, cause = e)
        }
    }

    private suspend fun handle(
        response: Response,
        file: File,
        existing: Long,
        onProgress: suspend (Long, Long?) -> Unit,
    ): Long {
        val code = response.code
        when {
            code == 416 -> {
                val total = parseContentRangeTotal(response.header("Content-Range"))
                if (total != null && existing == total) return existing
                file.delete()
                throw DownloadFailure(TaskError.SERVER, code, "Intervalo inválido; recomeçando")
            }
            code == 401 || code == 403 || code == 407 || code == 451 -> throw DownloadFailure(TaskError.ACCESS_DENIED, code)
            code == 404 || code == 410 -> throw DownloadFailure(TaskError.NOT_FOUND, code)
            code == 429 || code >= 500 -> throw DownloadFailure(TaskError.SERVER, code)
            code !in 200..299 -> throw DownloadFailure(TaskError.UNKNOWN, code)
        }
        val partial = code == 206
        val start: Long
        val total: Long?
        if (partial) {
            if (parseContentRangeStart(response.header("Content-Range")) != existing) {
                file.delete()
                throw DownloadFailure(TaskError.SERVER, code, "Resposta parcial inesperada; recomeçando")
            }
            start = existing
            total = parseContentRangeTotal(response.header("Content-Range"))
        } else {
            start = 0L
            total = response.body.contentLength().takeIf { it >= 0 }
        }
        if (total != null) {
            val available = availableBytes(file)
            if (available != null && available < total - start + minFreeBytes) {
                throw DownloadFailure(TaskError.INSUFFICIENT_STORAGE)
            }
        }
        var downloaded = start
        onProgress(downloaded, total)
        try {
            FileOutputStream(file, partial).use { output ->
                val source = response.body.byteStream()
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = source.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    onProgress(downloaded, total)
                }
            }
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            val noSpace = e.message?.contains("space", ignoreCase = true) == true
            throw DownloadFailure(if (noSpace) TaskError.INSUFFICIENT_STORAGE else TaskError.NETWORK, message = e.message, cause = e)
        }
        if (total != null && downloaded != total) {
            throw DownloadFailure(TaskError.NETWORK, message = "Transferência incompleta ($downloaded de $total)")
        }
        onProgress(downloaded, total ?: downloaded)
        return downloaded
    }

    companion object {
        private val CONTENT_RANGE = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""")

        fun parseContentRangeStart(header: String?): Long? =
            header?.let { CONTENT_RANGE.find(it)?.groupValues?.get(1)?.toLongOrNull() }

        fun parseContentRangeTotal(header: String?): Long? =
            header?.let { CONTENT_RANGE.find(it)?.groupValues?.get(3)?.toLongOrNull() }
                ?: header?.substringAfterLast('/')?.trim()?.toLongOrNull()
    }
}
