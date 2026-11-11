package io.github.valeriofra.mova.desktop.engine

import java.io.File
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

/** Executa programas externos (yt-dlp) e acompanha a saída linha a linha. */
class ProcessRunner {

    data class Result(val exitCode: Int, val stdout: String, val stderr: String) {
        val ok: Boolean get() = exitCode == 0

        /** Saída completa, para classificar erros. */
        val combined: String get() = listOf(stderr, stdout).filter { it.isNotBlank() }.joinToString("\n")
    }

    /**
     * Roda [command] até o fim. Se a corrotina for cancelada (pausar/cancelar),
     * o processo e os filhos dele (ex.: FFmpeg) são encerrados na hora.
     *
     * @param keepLines quantas linhas finais guardar de cada saída (null = todas).
     */
    suspend fun run(
        command: List<String>,
        environment: Map<String, String> = emptyMap(),
        workDir: File? = null,
        keepLines: Int? = null,
        onLine: ((String) -> Unit)? = null,
    ): Result = withContext(Dispatchers.IO) {
        val process = ProcessBuilder(command)
            .apply {
                workDir?.let { directory(it) }
                environment().putAll(environment)
            }
            .start()
        process.outputStream.close()
        try {
            coroutineScope {
                val stdout = async(Dispatchers.IO) { collect(process.inputStream, keepLines, onLine) }
                val stderr = async(Dispatchers.IO) { collect(process.errorStream, keepLines, onLine) }
                val code = try {
                    runInterruptible { process.waitFor() }
                } catch (e: CancellationException) {
                    // Encerra antes de esperar as leituras: com o processo vivo, elas nunca terminariam.
                    destroyTree(process)
                    throw e
                }
                Result(code, stdout.await(), stderr.await())
            }
        } finally {
            if (process.isAlive) destroyTree(process)
        }
    }

    private fun collect(stream: InputStream, keepLines: Int?, onLine: ((String) -> Unit)?): String {
        val lines = ArrayDeque<String>()
        stream.bufferedReader(Charsets.UTF_8).useLines { sequence ->
            for (line in sequence) {
                onLine?.let { callback -> runCatching { callback(line) } }
                lines.addLast(line)
                if (keepLines != null && lines.size > keepLines) lines.removeFirst()
            }
        }
        return lines.joinToString("\n")
    }

    private fun destroyTree(process: Process) {
        runCatching { process.toHandle().descendants().forEach { it.destroyForcibly() } }
        process.destroyForcibly()
    }
}
