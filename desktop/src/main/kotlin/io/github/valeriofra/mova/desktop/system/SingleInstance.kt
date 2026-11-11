package io.github.valeriofra.mova.desktop.system

import io.github.valeriofra.mova.desktop.i18n.Localization
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock

/** Garante uma única janela do MOVA (duas filas no mesmo histórico se atrapalhariam). */
object SingleInstance {
    private var channel: FileChannel? = null
    private var lock: FileLock? = null

    fun acquire(lockFile: File): Boolean = runCatching {
        lockFile.parentFile?.mkdirs()
        val file = RandomAccessFile(lockFile, "rw").channel
        val acquired = file.tryLock()
        if (acquired == null) {
            file.close()
            false
        } else {
            channel = file
            lock = acquired
            true
        }
    }.getOrDefault(true)

    fun alreadyRunningMessage(): String = when (Localization.effectiveLanguage(Localization.resolve(null))) {
        "pt" -> "O MOVA já está aberto."
        "es" -> "MOVA ya está abierto."
        "fr" -> "MOVA est déjà ouvert."
        else -> "MOVA is already running."
    }
}
