package io.github.valeriofra.mova.desktop.net

import io.github.valeriofra.mova.desktop.BuildInfo
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Response

object Http {
    val USER_AGENT = "MOVA/${BuildInfo.VERSION} (Desktop)"

    fun client(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()
}

/**
 * Executa a chamada de forma bloqueante (em Dispatchers.IO), mas cancela a
 * conexão assim que a corrotina for cancelada (pausar/cancelar na hora).
 */
suspend fun <T> Call.executeCancellable(block: suspend (Response) -> T): T {
    val call = this
    return coroutineScope {
        val canceller = launch {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            call.execute().use { block(it) }
        } finally {
            canceller.cancel()
        }
    }
}
