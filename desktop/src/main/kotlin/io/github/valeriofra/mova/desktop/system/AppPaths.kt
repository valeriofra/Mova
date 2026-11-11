package io.github.valeriofra.mova.desktop.system

import java.io.File

/**
 * Pastas do MOVA no computador.
 *
 * - Dados (ajustes, histórico, mecanismo): `%LOCALAPPDATA%\MOVA` no Windows e
 *   `~/.local/share/mova` (ou `$XDG_DATA_HOME/mova`) no Linux.
 * - Arquivos baixados: `Downloads/MOVA` na pasta do usuário (pode ser trocada em Ajustes).
 */
class AppPaths(
    val dataDir: File,
    val defaultDownloadsDir: File,
) {
    /** yt-dlp, FFmpeg e QuickJS baixados no primeiro uso. */
    val engineDir: File get() = File(dataDir, "engine").also { it.mkdirs() }

    /** Pastas de trabalho de cada download (partes, formatos intermediários). */
    val workDir: File get() = File(dataDir, "work").also { it.mkdirs() }

    val downloadsFile: File get() = File(dataDir, "downloads.json")
    val settingsFile: File get() = File(dataDir, "settings.json")
    val logFile: File get() = File(dataDir, "mova.log")
    val lockFile: File get() = File(dataDir, "mova.lock")

    fun workDirFor(id: Long): File = File(workDir, id.toString())

    companion object {
        fun detect(os: OsInfo = OsInfo.current, env: (String) -> String? = System::getenv): AppPaths {
            val home = File(System.getProperty("user.home"))
            val data = when (os.family) {
                OsInfo.Family.WINDOWS -> File(env("LOCALAPPDATA") ?: File(home, "AppData/Local").path, "MOVA")
                OsInfo.Family.MAC -> File(home, "Library/Application Support/MOVA")
                else -> File(env("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: File(home, ".local/share").path, "mova")
            }
            data.mkdirs()
            return AppPaths(data, File(userDownloadsDir(os, home, env), "MOVA"))
        }

        /** Pasta Downloads do usuário (no Linux, respeita o `user-dirs.dirs` do XDG). */
        private fun userDownloadsDir(os: OsInfo, home: File, env: (String) -> String?): File {
            if (os.family == OsInfo.Family.LINUX) {
                xdgDownloadDir(home, env)?.let { return it }
            }
            return File(home, "Downloads")
        }

        internal fun xdgDownloadDir(home: File, env: (String) -> String?): File? {
            env("XDG_DOWNLOAD_DIR")?.takeIf { it.isNotBlank() }?.let { return File(it) }
            val config = File(env("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() } ?: File(home, ".config").path, "user-dirs.dirs")
            if (!config.isFile) return null
            val line = runCatching { config.readLines() }.getOrDefault(emptyList())
                .firstOrNull { it.trim().startsWith("XDG_DOWNLOAD_DIR=") } ?: return null
            val value = line.substringAfter('=').trim().trim('"').replace("\$HOME", home.path)
            return value.takeIf { it.isNotBlank() }?.let(::File)
        }
    }
}
