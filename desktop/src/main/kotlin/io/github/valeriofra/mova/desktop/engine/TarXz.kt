package io.github.valeriofra.mova.desktop.engine

import java.io.File
import java.io.IOException
import java.io.InputStream
import org.tukaani.xz.XZInputStream

/**
 * Lê arquivos .tar.xz em Java puro (o FFmpeg do Linux vem assim). Não usa o
 * `tar` do sistema, que pode não existir (ex.: dentro do Flatpak).
 */
object TarXz {

    /** Extrai os arquivos comuns cujo nome (sem pastas) passa em [keep], direto em [into]. */
    fun extractFlat(archive: File, into: File, keep: (String) -> Boolean): Int {
        into.mkdirs()
        var count = 0
        XZInputStream(archive.inputStream().buffered()).use { xz ->
            forEachEntry(xz) { path, size, regular, data ->
                val name = path.substringAfterLast('/')
                if (regular && name.isNotEmpty() && keep(name)) {
                    File(into, name).outputStream().use { out -> copyExactly(data, out, size) }
                    count++
                    true
                } else {
                    false
                }
            }
        }
        return count
    }

    /**
     * Percorre as entradas do tar. [onEntry] devolve true se consumiu os [size]
     * bytes de dados; senão eles são pulados aqui.
     */
    internal fun forEachEntry(input: InputStream, onEntry: (path: String, size: Long, regular: Boolean, data: InputStream) -> Boolean) {
        val header = ByteArray(BLOCK)
        var longName: String? = null
        while (true) {
            if (!readFully(input, header)) return
            if (header.all { it == 0.toByte() }) return
            val size = octal(header, 124, 12)
            val type = header[156].toInt().toChar()
            val name = longName ?: run {
                val prefix = text(header, 345, 155)
                val base = text(header, 0, 100)
                if (prefix.isNotEmpty() && text(header, 257, 6).startsWith("ustar")) "$prefix/$base" else base
            }
            longName = null
            when (type) {
                'L' -> {
                    val bytes = ByteArray(size.toInt())
                    if (!readFully(input, bytes)) throw IOException("tar truncado")
                    longName = String(bytes, Charsets.UTF_8).trimEnd('\u0000')
                    skip(input, padding(size))
                }
                else -> {
                    val regular = type == '0' || type == '\u0000'
                    val consumed = onEntry(name, size, regular, input)
                    if (!consumed) skip(input, size)
                    skip(input, padding(size))
                }
            }
        }
    }

    private const val BLOCK = 512

    private fun padding(size: Long): Long = (BLOCK - size % BLOCK) % BLOCK

    private fun text(block: ByteArray, offset: Int, length: Int): String {
        var end = offset
        while (end < offset + length && block[end] != 0.toByte()) end++
        return String(block, offset, end - offset, Charsets.UTF_8)
    }

    private fun octal(block: ByteArray, offset: Int, length: Int): Long {
        val value = text(block, offset, length).trim()
        return if (value.isEmpty()) 0 else value.toLong(8)
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Boolean {
        var read = 0
        while (read < buffer.size) {
            val n = input.read(buffer, read, buffer.size - read)
            if (n < 0) {
                if (read == 0) return false
                throw IOException("tar truncado")
            }
            read += n
        }
        return true
    }

    private fun skip(input: InputStream, count: Long) {
        var left = count
        val buffer = ByteArray(8192)
        while (left > 0) {
            val n = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
            if (n < 0) throw IOException("tar truncado")
            left -= n
        }
    }

    private fun copyExactly(input: InputStream, output: java.io.OutputStream, size: Long) {
        var left = size
        val buffer = ByteArray(64 * 1024)
        while (left > 0) {
            val n = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
            if (n < 0) throw IOException("tar truncado")
            output.write(buffer, 0, n)
            left -= n
        }
    }
}
