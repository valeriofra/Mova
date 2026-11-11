package io.github.valeriofra.mova.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import android.util.Size
import io.github.valeriofra.mova.core.model.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Gera e guarda em memória miniaturas dos arquivos da biblioteca. */
class ThumbnailLoader(
    private val context: Context,
    private val httpClient: () -> OkHttpClient,
) {

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val failures = java.util.Collections.synchronizedSet(HashSet<String>())

    fun cached(uri: String?): Bitmap? = uri?.let { cache.get(it) }

    suspend fun load(uri: Uri, kind: MediaKind, sizePx: Int): Bitmap? {
        val key = uri.toString()
        cache.get(key)?.let { return it }
        if (key in failures) return null
        if (kind != MediaKind.VIDEO && kind != MediaKind.IMAGE && kind != MediaKind.AUDIO) return null
        val bitmap = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null) }.getOrNull()
                ?: runCatching { fallback(uri, kind, sizePx) }.getOrNull()
        }
        if (bitmap != null) cache.put(key, bitmap) else failures.add(key)
        return bitmap
    }

    /** Miniatura de uma URL (capa informada pela plataforma na análise). */
    suspend fun loadRemote(url: String, sizePx: Int): Bitmap? {
        val key = "remote:$url"
        cache.get(key)?.let { return it }
        if (key in failures) return null
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url).header("User-Agent", "MOVA/0.2 (Android)").build()
                httpClient().newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val bytes = response.body.bytes()
                    decodeSampled(bytes, sizePx)
                }
            }.getOrNull()
        }
        if (bitmap != null) cache.put(key, bitmap) else failures.add(key)
        return bitmap
    }

    fun cachedRemote(url: String?): Bitmap? = url?.let { cache.get("remote:$it") }

    private fun decodeSampled(bytes: ByteArray, sizePx: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val shortest = minOf(options.outWidth, options.outHeight).coerceAtLeast(1)
        options.inJustDecodeBounds = false
        options.inSampleSize = (shortest / sizePx).coerceAtLeast(1)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun fallback(uri: Uri, kind: MediaKind, sizePx: Int): Bitmap? = when (kind) {
        MediaKind.IMAGE -> ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            decoder.setTargetSampleSize((longest / sizePx).coerceAtLeast(1))
        }
        MediaKind.VIDEO -> MediaMetadataRetriever().useRetriever(uri) {
            it.getScaledFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, sizePx, sizePx)
        }
        MediaKind.AUDIO -> MediaMetadataRetriever().useRetriever(uri) { retriever ->
            retriever.embeddedPicture?.let { bytes ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                val longest = maxOf(options.outWidth, options.outHeight)
                options.inJustDecodeBounds = false
                options.inSampleSize = (longest / sizePx).coerceAtLeast(1)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            }
        }
        else -> null
    }

    private inline fun <T> MediaMetadataRetriever.useRetriever(uri: Uri, block: (MediaMetadataRetriever) -> T): T =
        try {
            setDataSource(context, uri)
            block(this)
        } finally {
            release()
        }

    private companion object {
        const val CACHE_BYTES = 16 * 1024 * 1024
    }
}
