package io.github.valeriofra.mova.core.extractor

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Alternativa para sites que o yt-dlp não suporta (ex.: Kwai): a página é
 * aberta num navegador invisível e este script procura o vídeo que a própria
 * página carrega — o player (<video>), as etiquetas de compartilhamento
 * (og:video) e endereços de vídeo nos dados da página. Vídeos protegidos
 * (blob:/DRM) são ignorados.
 */
object WebPageVideo {

    /** Expressão JavaScript que devolve um JSON com os endereços encontrados. */
    val SCRIPT: String = """
        (function(){
          var urls = [];
          function add(u, kind){
            if (!u) return;
            try { u = new URL(u, location.href).href; } catch (e) { return; }
            if (!/^https?:/i.test(u)) return;
            for (var i = 0; i < urls.length; i++) if (urls[i].url === u) return;
            urls.push({ url: u, kind: kind });
          }
          document.querySelectorAll('video').forEach(function(v){
            add(v.currentSrc || v.src, 'video');
            v.querySelectorAll('source').forEach(function(s){ add(s.src, 'video'); });
          });
          ['og:video:secure_url','og:video:url','og:video','twitter:player:stream'].forEach(function(p){
            var m = document.querySelector('meta[property="' + p + '"],meta[name="' + p + '"]');
            if (m) add(m.content, 'meta');
          });
          var html = document.documentElement.innerHTML
            .replace(/\\u002F/gi, '/').replace(/\\u0026/gi, '&').replace(/\\\//g, '/');
          var re = /https?:\/\/[^"'\s<>\\]+?\.(?:mp4|m4v|webm|mov|m3u8)(?:\?[^"'\s<>\\]*)?/gi;
          var m, n = 0;
          while ((m = re.exec(html)) && n < 50) { add(m[0].replace(/&amp;/g, '&'), 'page'); n++; }
          var meta = function(p){ var e = document.querySelector('meta[property="' + p + '"],meta[name="' + p + '"]'); return e ? e.content : ''; };
          var video = document.querySelector('video');
          return JSON.stringify({
            urls: urls,
            title: meta('og:title') || document.title || '',
            thumbnail: meta('og:image') || (video ? video.poster : '') || '',
            page: location.href
          });
        })()
    """.trimIndent()

    /** Vídeo encontrado; [fromPlayer] indica que veio do player ou do og:video (mais confiável). */
    data class Found(val result: ExtractionResult.Single, val fromPlayer: Boolean)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val DIRECT_EXTENSIONS = Regex("""\.(mp4|m4v|webm|mov)(\?|$)""", RegexOption.IGNORE_CASE)
    private val STREAM_EXTENSIONS = Regex("""\.m3u8(\?|$)""", RegexOption.IGNORE_CASE)

    /**
     * Interpreta o valor devolvido pelo WebView.evaluateJavascript (uma string
     * JSON entre aspas) ou o próprio JSON. Retorna null se nada foi encontrado.
     */
    fun parse(evaluateResult: String?, fallbackTitle: String): Found? {
        if (evaluateResult.isNullOrBlank() || evaluateResult == "null") return null
        val text = runCatching {
            val element = json.parseToJsonElement(evaluateResult)
            (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: evaluateResult
        }.getOrDefault(evaluateResult)
        val root = runCatching { json.parseToJsonElement(text) as JsonObject }.getOrNull() ?: return null

        val candidates = (root["urls"] as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val url = (o["url"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val kind = (o["kind"] as? JsonPrimitive)?.contentOrNull ?: "page"
            url to kind
        }
        val (best, bestKind) = candidates
            .filter { (url, kind) -> kind != "page" || DIRECT_EXTENSIONS.containsMatchIn(url) || STREAM_EXTENSIONS.containsMatchIn(url) }
            .minByOrNull { (url, kind) -> rank(url, kind) }
            ?: return null

        val title = root.string("title")?.trim()?.takeIf { it.isNotEmpty() } ?: fallbackTitle
        val single = ExtractionResult.Single(
            ExtractedMedia(
                id = null,
                title = title.take(120),
                webpageUrl = root.string("page"),
                extractor = "WebPage",
                uploader = null,
                durationSeconds = null,
                thumbnailUrl = root.string("thumbnail")?.takeIf { it.startsWith("http") },
                resolutions = emptyList(),
                hasVideo = true,
                hasAudio = true,
                sizeByResolution = emptyMap(),
                audioSizeBytes = null,
                directUrl = best,
            ),
        )
        return Found(single, fromPlayer = bestKind != "page")
    }

    /** Menor é melhor: player > og:video > dados da página; arquivo direto > HLS. */
    private fun rank(url: String, kind: String): Int {
        val source = when (kind) {
            "video" -> 0
            "meta" -> 10
            else -> 20
        }
        val format = when {
            DIRECT_EXTENSIONS.containsMatchIn(url) -> 0
            STREAM_EXTENSIONS.containsMatchIn(url) -> 1
            else -> 2
        }
        val preview = if (Regex("preview|thumb|teaser|sample", RegexOption.IGNORE_CASE).containsMatchIn(url)) 5 else 0
        return source + format + preview
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
}
