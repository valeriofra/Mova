package io.github.valeriofra.mova.download

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import io.github.valeriofra.mova.core.extractor.ExtractionFailure
import io.github.valeriofra.mova.core.extractor.ExtractionResult
import io.github.valeriofra.mova.core.extractor.InstagramBridge
import io.github.valeriofra.mova.core.extractor.InstagramPublic
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Obtém o vídeo de um post público do Instagram sem login: abre a página
 * pública do post num navegador invisível (WebView), como um visitante sem
 * conta, e lê o endereço do vídeo que a própria página recebe. Tudo no
 * aparelho; nenhuma conta, senha ou servidor externo é usado.
 */
class InstagramWebExtractor(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())

    suspend fun extract(shortcode: String): ExtractionResult {
        val mediaId = runCatching { InstagramPublic.shortcodeToMediaId(shortcode) }.getOrNull()
            ?: return ExtractionResult.Failure(ExtractionFailure.UNSUPPORTED, "Código de post inválido")
        return withTimeoutOrNull(TIMEOUT_MS) {
            withContext(Dispatchers.Main) { load(shortcode, mediaId) }
        } ?: ExtractionResult.Failure(ExtractionFailure.NETWORK, "A página pública do Instagram não respondeu a tempo")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun load(shortcode: String, mediaId: String): ExtractionResult =
        suspendCancellableCoroutine { cont ->
            val webView = WebView(context.applicationContext)
            var done = false
            var injected = false

            fun finish(result: ExtractionResult) {
                if (done) return
                done = true
                runCatching {
                    webView.stopLoading()
                    webView.removeJavascriptInterface(BRIDGE_NAME)
                    webView.destroy()
                }
                if (cont.isActive) cont.resume(result)
            }

            val bridge = object {
                @JavascriptInterface
                fun onResult(kind: String, payload: String) {
                    // Chamado numa thread do WebView: interpreta aqui e finaliza na principal.
                    val result = runCatching { InstagramBridge.parse(kind, payload, shortcode) }
                        .getOrElse { ExtractionResult.Failure(ExtractionFailure.UNKNOWN, it.toString()) }
                    main.post { finish(result) }
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
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
            webView.addJavascriptInterface(bridge, BRIDGE_NAME)
            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    // Fica sempre dentro do Instagram (nada de abrir outros apps).
                    val host = request.url.host.orEmpty()
                    return !(host == "instagram.com" || host.endsWith(".instagram.com"))
                }

                override fun onPageFinished(view: WebView, url: String) {
                    if (done || injected) return
                    val path = Uri.parse(url).path.orEmpty()
                    if (path.startsWith("/accounts/login") || path.startsWith("/challenge")) {
                        finish(
                            ExtractionResult.Failure(
                                ExtractionFailure.LOGIN_REQUIRED,
                                "O Instagram pediu login para este post (pode ser privado ou limite de acessos sem conta).",
                            ),
                        )
                        return
                    }
                    injected = true
                    view.evaluateJavascript(InstagramBridge.script(mediaId), null)
                }
            }
            cont.invokeOnCancellation { main.post { finish(ExtractionResult.Failure(ExtractionFailure.UNKNOWN, "Cancelado")) } }
            Log.d(TAG, "Abrindo página pública do post $shortcode")
            webView.loadUrl("https://www.instagram.com/p/$shortcode/")
        }

    private companion object {
        const val TAG = "MovaInstagram"
        const val BRIDGE_NAME = "MovaBridge"
        const val TIMEOUT_MS = 30_000L
    }
}
