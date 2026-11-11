package io.github.valeriofra.mova.core.extractor

import java.math.BigInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Leitura dos dados que a página pública de um post do Instagram entrega a
 * quem não está conectado (o mesmo que um navegador sem login recebe).
 * Só funciona para conteúdo público; nada de privado é acessado.
 */
object InstagramPublic {

    /** Identificadores usados pela própria página pública (web, sem login). */
    const val WEB_APP_ID = "936619743392459"
    const val ASBD_ID = "359341"
    const val LOGGED_OUT_QUERY_NAME = "PolarisLoggedOutDesktopWWWPostRootContentQuery"
    const val LOGGED_OUT_QUERY_DOC_ID = "27130156389949648"

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Converte o código do post (ex.: "DYwztKbH_kN") no identificador numérico. */
    fun shortcodeToMediaId(shortcode: String): String {
        val code = if (shortcode.length > 28) shortcode.dropLast(28) else shortcode
        var value = BigInteger.ZERO
        val base = BigInteger.valueOf(ALPHABET.length.toLong())
        for (ch in code) {
            val digit = ALPHABET.indexOf(ch)
            require(digit >= 0) { "Código inválido: $shortcode" }
            value = value.multiply(base).add(BigInteger.valueOf(digit.toLong()))
        }
        return value.toString()
    }

    /** Interpreta um ou mais blocos JSON (da página ou da consulta pública). */
    fun parse(payloads: List<String>, shortcode: String): ExtractionResult {
        for (text in payloads) {
            val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: continue
            val media = findMedia(root) ?: continue
            return toResult(media, shortcode)
        }
        return ExtractionResult.Failure(
            ExtractionFailure.LOGIN_REQUIRED,
            "A página pública do Instagram não entregou o vídeo deste post",
        )
    }

    /** Procura o objeto do post: o primeiro com "carousel_media" ou "video_versions". */
    private fun findMedia(element: JsonElement): JsonObject? {
        when (element) {
            is JsonObject -> {
                val carousel = element["carousel_media"] as? JsonArray
                val versions = element["video_versions"] as? JsonArray
                if (!carousel.isNullOrEmpty() || !versions.isNullOrEmpty()) return element
                for (value in element.values) findMedia(value)?.let { return it }
            }
            is JsonArray -> for (value in element) findMedia(value)?.let { return it }
            else -> Unit
        }
        return null
    }

    private fun toResult(media: JsonObject, shortcode: String): ExtractionResult {
        val title = titleOf(media, shortcode)
        val uploader = (media["user"] as? JsonObject)?.string("username")
            ?: (media["owner"] as? JsonObject)?.string("username")
        val carousel = media["carousel_media"] as? JsonArray
        if (!carousel.isNullOrEmpty()) {
            // Só os itens com vídeo (fotos do carrossel são ignoradas).
            val videos = carousel.mapIndexedNotNull { i, item ->
                val obj = item as? JsonObject ?: return@mapIndexedNotNull null
                val video = bestVideo(obj) ?: return@mapIndexedNotNull null
                Triple(i + 1, obj, video)
            }
            return when (videos.size) {
                0 -> ExtractionResult.Failure(ExtractionFailure.UNAVAILABLE, "There is no video in this post")
                1 -> ExtractionResult.Single(singleMedia(media, videos.single().second, title, uploader, shortcode))
                else -> ExtractionResult.Collection(
                    title = title,
                    webpageUrl = null,
                    uploader = uploader,
                    entries = videos.map { (index, obj, video) ->
                        ExtractedEntry(
                            url = video.url,
                            title = "$title ($index)",
                            durationSeconds = obj.number("video_duration")?.toLong(),
                            thumbnailUrl = thumbnailOf(obj),
                            index = index,
                        )
                    },
                )
            }
        }
        if (bestVideo(media) == null) {
            return ExtractionResult.Failure(ExtractionFailure.UNAVAILABLE, "There is no video in this post")
        }
        return ExtractionResult.Single(singleMedia(media, media, title, uploader, shortcode))
    }

    private fun singleMedia(post: JsonObject, item: JsonObject, title: String, uploader: String?, shortcode: String): ExtractedMedia {
        val video = bestVideo(item)!!
        return ExtractedMedia(
            id = post.string("code") ?: shortcode,
            title = title,
            webpageUrl = "https://www.instagram.com/p/$shortcode/",
            extractor = "InstagramPublic",
            uploader = uploader,
            durationSeconds = (item.number("video_duration") ?: post.number("video_duration"))?.toLong(),
            thumbnailUrl = thumbnailOf(item) ?: thumbnailOf(post),
            resolutions = listOfNotNull(video.resolution),
            hasVideo = true,
            hasAudio = true,
            sizeByResolution = emptyMap(),
            audioSizeBytes = null,
            directUrl = video.url,
        )
    }

    private data class Version(val url: String, val resolution: Int?)

    /** Melhor versão do vídeo até 1080p (o Instagram não passa disso). */
    private fun bestVideo(obj: JsonObject): Version? {
        val versions = (obj["video_versions"] as? JsonArray)?.mapNotNull { v ->
            val o = v as? JsonObject ?: return@mapNotNull null
            val url = o.string("url") ?: return@mapNotNull null
            val w = o.number("width")?.toInt()
            val h = o.number("height")?.toInt()
            Version(url, if (w != null && h != null) minOf(w, h) else h)
        }.orEmpty()
        if (versions.isEmpty()) return null
        return versions.filter { (it.resolution ?: 0) <= 1080 }.maxByOrNull { it.resolution ?: 0 }
            ?: versions.minByOrNull { it.resolution ?: Int.MAX_VALUE }
    }

    private fun thumbnailOf(obj: JsonObject): String? {
        val candidates = ((obj["image_versions2"] as? JsonObject)?.get("candidates") as? JsonArray) ?: return null
        return candidates.firstNotNullOfOrNull { (it as? JsonObject)?.string("url") }
    }

    private fun titleOf(media: JsonObject, shortcode: String): String {
        val caption = (media["caption"] as? JsonObject)?.string("text")
            ?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }
            ?.take(80)
        val user = (media["user"] as? JsonObject)?.string("username")
        return caption ?: user?.let { "Instagram de @$it" } ?: "Instagram $shortcode"
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.number(key: String): Double? {
        val p = this[key] as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        return p.doubleOrNull ?: p.longOrNull?.toDouble()
    }
}

/** Resultado enviado pela página pública (WebView) ao app. */
object InstagramBridge {
    const val KIND_PAGE = "page"
    const val KIND_GRAPHQL = "graphql"
    const val KIND_ERROR = "error"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(kind: String, payload: String, shortcode: String): ExtractionResult = when (kind) {
        KIND_PAGE -> {
            val scripts = runCatching {
                (json.parseToJsonElement(payload) as JsonArray).mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            }.getOrDefault(emptyList())
            InstagramPublic.parse(scripts, shortcode)
        }
        KIND_GRAPHQL -> InstagramPublic.parse(listOf(payload), shortcode)
        else -> ExtractionResult.Failure(ExtractionFailure.LOGIN_REQUIRED, payload.take(300))
    }

    /**
     * Script executado na página pública do post: usa os dados que a própria
     * página recebeu; se não houver, faz a mesma consulta que a página faz
     * para visitantes sem conta.
     */
    fun script(mediaId: String): String {
        require(mediaId.all { it.isDigit() })
        return """
            (function(){
              function send(kind, payload){ try { MovaBridge.onResult(kind, payload); } catch (e) {} }
              try {
                var found = [];
                document.querySelectorAll('script[type="application/json"]').forEach(function(s){
                  var t = s.textContent || '';
                  if (t.indexOf('video_versions') >= 0 || t.indexOf('carousel_media') >= 0) found.push(t);
                });
                if (found.length) { send('$KIND_PAGE', JSON.stringify(found)); return; }
                var html = document.documentElement.innerHTML;
                var lsd = null;
                var m = html.match(/"LSD",\[\],\{"token":"([^"]+)"/);
                if (m) lsd = m[1];
                if (!lsd) {
                  var e = document.getElementById('__eqmc');
                  if (e) { try { lsd = JSON.parse(e.textContent).l; } catch (x) {} }
                }
                var csrf = (document.cookie.match(/(?:^|;\s*)csrftoken=([^;]+)/) || [])[1];
                var body = new URLSearchParams({
                  lsd: lsd || '',
                  fb_api_caller_class: 'RelayModern',
                  fb_api_req_friendly_name: '${InstagramPublic.LOGGED_OUT_QUERY_NAME}',
                  server_timestamps: 'true',
                  variables: JSON.stringify({ media_id: '$mediaId' }),
                  doc_id: '${InstagramPublic.LOGGED_OUT_QUERY_DOC_ID}'
                });
                var headers = {
                  'Content-Type': 'application/x-www-form-urlencoded',
                  'X-IG-App-ID': '${InstagramPublic.WEB_APP_ID}',
                  'X-ASBD-ID': '${InstagramPublic.ASBD_ID}',
                  'X-FB-Friendly-Name': '${InstagramPublic.LOGGED_OUT_QUERY_NAME}',
                  'X-FB-LSD': lsd || '',
                  'X-Requested-With': 'XMLHttpRequest'
                };
                if (csrf) headers['X-CSRFToken'] = csrf;
                fetch('/api/graphql', { method: 'POST', headers: headers, body: body.toString(), credentials: 'include' })
                  .then(function(r){ return r.text(); })
                  .then(function(t){ send('$KIND_GRAPHQL', t); })
                  .catch(function(e){ send('$KIND_ERROR', 'Falha na consulta pública: ' + e); });
              } catch (e) { send('$KIND_ERROR', 'Falha na página pública: ' + e); }
            })();
        """.trimIndent()
    }
}
