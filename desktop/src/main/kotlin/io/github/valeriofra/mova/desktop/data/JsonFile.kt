package io.github.valeriofra.mova.desktop.data

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/** Leitura e gravação seguras de um arquivo JSON (grava num temporário e troca de uma vez). */
class JsonFile<T>(private val file: File, private val serializer: KSerializer<T>) {

    fun read(): T? = runCatching {
        if (!file.isFile) null else json.decodeFromString(serializer, file.readText(Charsets.UTF_8))
    }.getOrElse {
        // Arquivo corrompido: guarda uma cópia para diagnóstico e recomeça.
        runCatching { file.copyTo(File(file.path + ".corrompido"), overwrite = true) }
        null
    }

    @Synchronized
    fun write(value: T) {
        file.parentFile?.mkdirs()
        val temp = File(file.path + ".tmp")
        temp.writeText(json.encodeToString(serializer, value), Charsets.UTF_8)
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
            coerceInputValues = true
        }
    }
}
