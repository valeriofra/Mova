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

/** Gera e guarda em memória miniaturas dos arquivos da biblioteca. */
class ThumbnailLoader(private val context: Context) {

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
