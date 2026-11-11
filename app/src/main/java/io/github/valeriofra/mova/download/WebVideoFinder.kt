package io.github.valeriofra.mova.download

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import io.github.valeriofra.mova.core.extractor.ExtractionFailure
import io.github.valeriofra.mova.core.extractor.ExtractionResult
import io.github.valeriofra.mova.core.extractor.WebPageVideo
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Para sites que o yt-dlp não suporta (ex.: Kwai): abre a página num
 * navegador invisível, como um visitante comum, e procura o vídeo que a
 * própria página carrega. Tudo no aparelho, sem login.
 */
class WebVideoFinder(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())

    suspend fun find(url: String): ExtractionResult {
        val fallbackTitle = Uri.parse(url).host?.removePrefix("www.") ?: "video"
        return withTimeoutOrNull(TIMEOUT_MS) {
            withContext(Dispatchers.Main) { load(url, fallbackTitle) }
        } ?: ExtractionResult.Failure(ExtractionFailure.UNSUPPORTED, "Nenhum vídeo encontrado na página")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun load(url: String, fallbackTitle: String): ExtractionResult =
        suspendCancellableCoroutine { cont ->
            val webView = WebView(context.applicationContext)
            var done = false
            var started = false
            var attempts = 0
            var bestSoFar: WebPageVideo.Found? = null

            fun finish(result: ExtractionResult) {
                if (done) return
                done = true
                runCatching {
                    webView.stopLoading()
                    webView.destroy()
                }
                if (cont.isActive) cont.resume(result)
            }

            fun scan() {
                if (done) return
                attempts++
                webView.evaluateJavascript(WebPageVideo.SCRIPT) { value ->
                    if (done) return@evaluateJavascript
                    val found = runCatching { WebPageVideo.parse(value, fallbackTitle) }.getOrNull()
                    if (found != null && (bestSoFar == null || found.fromPlayer)) bestSoFar = found
                    val best = bestSoFar
                    when {
                        // Player ou og:video: confiável, termina na hora.
                        best != null && best.fromPlayer -> finish(best.result)
                        // Só nos dados da página: espera um pouco pelo player e aceita.
                        best != null && attempts >= ACCEPT_PAGE_DATA_AFTER -> finish(best.result)
                        attempts >= MAX_ATTEMPTS -> finish(
                            best?.result ?: ExtractionResult.Failure(
                                ExtractionFailure.UNSUPPORTED,
                                "Nenhum vídeo encontrado na página",
                            ),
                        )
                        else -> main.postDelayed({ scan() }, SCAN_INTERVAL_MS)
                    }
                }
            }

            with(webView.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadsImagesAutomatically = false
                blockNetworkImage = true
                mediaPlaybackRequiresUserGesture = true
            }
            CookieManager.getInstance().setAcceptCookie(true)
            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    // Bloqueia saídas para outros apps (intent://, market://...).
                    val scheme = request.url.scheme.orEmpty()
                    return scheme != "http" && scheme != "https"
                }

                override fun onPageFinished(view: WebView, url: String) {
                    if (done || started) return
                    started = true
                    main.postDelayed({ scan() }, SCAN_INTERVAL_MS)
                }
            }
            cont.invokeOnCancellation { main.post { finish(ExtractionResult.Failure(ExtractionFailure.UNKNOWN, "Cancelado")) } }
            Log.d(TAG, "Procurando vídeo na página $url")
            webView.loadUrl(url)
        }

    private companion object {
        const val TAG = "MovaWebVideo"
        const val TIMEOUT_MS = 25_000L
        const val SCAN_INTERVAL_MS = 1_200L
        const val ACCEPT_PAGE_DATA_AFTER = 3
        const val MAX_ATTEMPTS = 8
    }
}
