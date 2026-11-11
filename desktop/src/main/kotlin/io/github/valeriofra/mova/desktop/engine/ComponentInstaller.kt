package io.github.valeriofra.mova.desktop.engine

import io.github.valeriofra.mova.desktop.net.executeCancellable
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Falha ao baixar ou instalar um componente do mecanismo. */
class ComponentInstallException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Baixa e instala os componentes do mecanismo em `engine/`, conferindo o
 * SHA-256 publicado pelo projeto de origem quando ele existe.
 */
class ComponentInstaller(
    private val client: OkHttpClient,
    private val engineDir: File,
) {

    /** Instala [spec]; [onProgress] recebe bytes baixados e total (quando conhecido). */
    suspend fun install(spec: ComponentSpec, onProgress: (Long, Long?) -> Unit) {
        val expected = spec.checksumsUrl?.let { url ->
            val sums = fetchText(url)
            Components.checksumFor(sums, spec.assetName)
                ?: throw ComponentInstallException("Checksum de ${spec.assetName} não encontrado em $url")
        }
        val downloads = File(engineDir, ".download").also { it.mkdirs() }
        val archive = File(downloads, spec.assetName)
        try {
            val actual = download(spec.url, archive, onProgress)
            if (expected != null && !actual.equals(expected, ignoreCase = true)) {
                throw ComponentInstallException("Checksum inválido para ${spec.assetName}: esperado $expected, obtido $actual")
            }
            withContext(Dispatchers.IO) { place(spec, archive) }
        } finally {
            archive.delete()
        }
    }

    private suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).executeCancellable { response ->
            if (!response.isSuccessful) throw ComponentInstallException("HTTP ${response.code} ao baixar $url")
            response.body.string()
        }
    }

    /** Baixa para [target] e devolve o SHA-256 calculado durante a transferência. */
    private suspend fun download(url: String, target: File, onProgress: (Long, Long?) -> Unit): String =
        withContext(Dispatchers.IO) {
            client.newCall(Request.Builder().url(url).build()).executeCancellable { response ->
                if (!response.isSuccessful) throw ComponentInstallException("HTTP ${response.code} ao baixar $url")
                val total = response.body.contentLength().takeIf { it > 0 }
                val digest = MessageDigest.getInstance("SHA-256")
                var done = 0L
                var lastReport = 0L
                response.body.byteStream().use { input ->
                    target.outputStream().buffered().use { output ->
                        val buffer = ByteArray(BUFFER)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            done += read
                            if (done - lastReport >= REPORT_EVERY) {
                                lastReport = done
                                currentCoroutineContext().ensureActive()
                                onProgress(done, total)
                            }
                        }
                    }
                }
                onProgress(done, total)
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        }

    private suspend fun place(spec: ComponentSpec, archive: File) {
        val dir = if (spec.installDir.isEmpty()) engineDir else File(engineDir, spec.installDir)
        when (spec.packaging) {
            Packaging.EXECUTABLE -> {
                val name = spec.fileName ?: spec.assetName
                dir.mkdirs()
                val target = File(dir, name)
                Files.move(archive.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                target.setExecutable(true, false)
            }
            Packaging.ZIP, Packaging.TAR_XZ -> {
                val staging = File(engineDir, ".staging-${spec.id.name.lowercase()}")
                staging.deleteRecursively()
                staging.mkdirs()
                try {
                    if (spec.packaging == Packaging.ZIP) unzipFlat(archive, staging, spec.keep) else untarFlat(archive, staging, spec.keep)
                    if (staging.listFiles().isNullOrEmpty()) {
                        throw ComponentInstallException("Nenhum arquivo útil em ${spec.assetName}")
                    }
                    dir.deleteRecursively()
                    if (!staging.renameTo(dir)) {
                        staging.copyRecursively(dir, overwrite = true)
                    }
                    dir.listFiles()?.forEach { it.setExecutable(true, false) }
                } finally {
                    staging.deleteRecursively()
                }
            }
        }
    }

    /** Extrai só os arquivos desejados, sem as pastas do pacote (evita caminhos maliciosos). */
    private fun unzipFlat(archive: File, into: File, keep: (String) -> Boolean) {
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.substringAfterLast('/').substringAfterLast('\\')
                if (!entry.isDirectory && name.isNotEmpty() && keep(name)) {
                    File(into, name).outputStream().use { zip.copyTo(it) }
                }
            }
        }
    }

    private fun untarFlat(archive: File, into: File, keep: (String) -> Boolean) {
        TarXz.extractFlat(archive, into, keep)
    }

    private companion object {
        const val BUFFER = 64 * 1024
        const val REPORT_EVERY = 256L * 1024
    }
}
