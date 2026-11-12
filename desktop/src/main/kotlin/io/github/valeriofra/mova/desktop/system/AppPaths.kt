package io.github.valeriofra.mova.desktop.system

import com.sun.jna.platform.win32.KnownFolders
import com.sun.jna.platform.win32.Shell32Util
import java.io.File

/**
 * Pastas do MOVA no computador.
 *
 * - Dados (ajustes, histórico, mecanismo): `%LOCALAPPDATA%\MOVA` no Windows e
 *   `~/.local/share/mova` (ou `$XDG_DATA_HOME/mova`) no Linux.
 * - Arquivos baixados: a pasta Downloads do usuário (no Windows, a real, mesmo se
 *   movida para outro disco; no Linux, a do XDG, ex.: ~/Transferências). Pode ser
 *   trocada em Ajustes.
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
            return AppPaths(data, userDownloadsDir(os, home, env))
        }

        /** Pasta Downloads do usuário (no Linux, respeita o `user-dirs.dirs` do XDG). */
        private fun userDownloadsDir(os: OsInfo, home: File, env: (String) -> String?): File {
            when (os.family) {
                OsInfo.Family.WINDOWS -> windowsDownloadsDir()?.let { return it }
                OsInfo.Family.LINUX -> xdgDownloadDir(home, env)?.let { return it }
                else -> Unit
            }
            return File(home, "Downloads")
        }

        /** Pasta conhecida "Downloads" do Windows (FOLDERID_Downloads). */
        private fun windowsDownloadsDir(): File? = runCatching {
            File(Shell32Util.getKnownFolderPath(KnownFolders.FOLDERID_Downloads))
        }.getOrNull()?.takeIf { it.path.isNotBlank() }

        internal fun xdgDownloadDir(home: File, env: (String) -> String?): File? {
            env("XDG_DOWNLOAD_DIR")?.takeIf { it.isNotBlank() }?.let { return File(it) }
            // No Flatpak, XDG_CONFIG_HOME aponta para a pasta do app; o arquivo do sistema fica em ~/.config.
            val config = listOfNotNull(env("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }, File(home, ".config").path)
                .map { File(it, "user-dirs.dirs") }
                .firstOrNull { it.isFile } ?: return null
            val line = runCatching { config.readLines() }.getOrDefault(emptyList())
                .firstOrNull { it.trim().startsWith("XDG_DOWNLOAD_DIR=") } ?: return null
            val value = line.substringAfter('=').trim().trim('"').replace("\$HOME", home.path)
            return value.takeIf { it.isNotBlank() }?.let(::File)
        }
    }
}
