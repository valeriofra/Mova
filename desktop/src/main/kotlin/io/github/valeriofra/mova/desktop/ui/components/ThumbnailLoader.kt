package io.github.valeriofra.mova.desktop.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.github.valeriofra.mova.desktop.net.Http
import io.github.valeriofra.mova.desktop.net.executeCancellable
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Image

/** Baixa e guarda em memória as capas dos vídeos (no máximo [capacity] imagens). */
class ThumbnailLoader(private val client: OkHttpClient, private val capacity: Int = 120) {
    private val cache: MutableMap<String, ImageBitmap> = Collections.synchronizedMap(
        object : LinkedHashMap<String, ImageBitmap>(capacity, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean = size > capacity
        },
    )

    fun cached(url: String): ImageBitmap? = cache[url]

    suspend fun load(url: String): ImageBitmap? {
        cache[url]?.let { return it }
        val image = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url).header("User-Agent", Http.USER_AGENT).build()
                client.newCall(request).executeCancellable { response ->
                    if (!response.isSuccessful) return@executeCancellable null
                    val bytes = response.body.bytes()
                    if (bytes.size > MAX_BYTES) null else Image.makeFromEncoded(bytes).toComposeImageBitmap()
                }
            }.getOrNull()
        }
        if (image != null) cache[url] = image
        return image
    }

    private companion object {
        const val MAX_BYTES = 8 * 1024 * 1024
    }
}
