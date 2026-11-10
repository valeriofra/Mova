package io.github.valeriofra.mova.download

import io.github.valeriofra.mova.core.analysis.ProbeResult
import io.github.valeriofra.mova.core.analysis.RemoteFileProbe
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Consulta um arquivo remoto sem baixá-lo: tenta HEAD e, se o servidor não
 * aceitar, pede apenas o primeiro byte com GET + Range.
 */
class OkHttpProbe(private val client: OkHttpClient) : RemoteFileProbe {

    override suspend fun probe(url: String): ProbeResult = withContext(Dispatchers.IO) {
        val request = try {
            Request.Builder().url(url)
        } catch (e: IllegalArgumentException) {
            throw IOException("URL inválida", e)
        }
        val head = execute(request.head().header("User-Agent", HttpDownloader.USER_AGENT).build())
        val needsFallback = head.httpCode in FALLBACK_CODES ||
            (head.httpCode in 200..299 && head.contentType == null && head.contentLength == null)
        if (!needsFallback) return@withContext head

        val ranged = execute(
            Request.Builder().url(url)
                .header("User-Agent", HttpDownloader.USER_AGENT)
                .header("Range", "bytes=0-0")
                .build(),
        )
        if (ranged.httpCode in 200..299 || head.httpCode !in 200..299) ranged else head
    }

    private suspend fun execute(request: Request): ProbeResult =
        client.newCall(request).executeCancellable { it.toProbeResult() }

    private fun Response.toProbeResult(): ProbeResult {
        val contentRange = header("Content-Range")
        val length = if (code == 206) {
            HttpDownloader.parseContentRangeTotal(contentRange)
        } else {
            header("Content-Length")?.toLongOrNull()
        }
        val acceptsRanges = code == 206 ||
            header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
        return ProbeResult(
            finalUrl = request.url.toString(),
            httpCode = code,
            contentType = header("Content-Type"),
            contentLength = length,
            contentDisposition = header("Content-Disposition"),
            acceptsRanges = acceptsRanges,
            etag = header("ETag"),
            lastModified = header("Last-Modified"),
        )
    }

    private companion object {
        // Servidores que recusam HEAD (ou URLs assinadas apenas para GET).
        val FALLBACK_CODES = setOf(400, 403, 405, 501)
    }
}
