package io.github.valeriofra.mova.download

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Response

/**
 * Executa a chamada de forma bloqueante (deve rodar em Dispatchers.IO), mas
 * cancela a conexão assim que a corrotina for cancelada. Isso interrompe
 * leituras bloqueadas no socket, permitindo pausar/cancelar imediatamente.
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
