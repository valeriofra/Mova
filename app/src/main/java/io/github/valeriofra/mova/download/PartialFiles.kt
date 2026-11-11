package io.github.valeriofra.mova.download

import android.content.Context
import java.io.File

/**
 * Arquivos temporários dos downloads em andamento. Ficam na área privada do
 * app e só são movidos para a pasta pública quando o download termina.
 */
class PartialFiles(context: Context) {

    private val directory = File(context.noBackupFilesDir, "partial")

    fun fileFor(id: Long): File {
        directory.mkdirs()
        return File(directory, "$id.part")
    }

    /** Pasta temporária usada pelo yt-dlp (vários arquivos parciais por tarefa). */
    fun dirFor(id: Long): File = File(directory, "$id.d").also { it.mkdirs() }

    fun delete(id: Long) {
        File(directory, "$id.part").delete()
        File(directory, "$id.d").deleteRecursively()
    }

    fun totalSize(): Long = directory.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    /** Apaga arquivos cujo id não está em [keepIds]. Retorna os bytes liberados. */
    fun deleteOrphans(keepIds: Set<Long>): Long {
        var freed = 0L
        directory.listFiles()?.forEach { file ->
            val id = file.name.removeSuffix(".part").removeSuffix(".d").toLongOrNull()
            if (id == null || id !in keepIds) {
                val size = file.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
                if (file.deleteRecursively()) freed += size
            }
        }
        return freed
    }
}
