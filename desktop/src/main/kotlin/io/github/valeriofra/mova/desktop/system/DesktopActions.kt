package io.github.valeriofra.mova.desktop.system

import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.net.URI
import javax.swing.JFileChooser

/** Integrações com o sistema: abrir arquivos, pastas e links, área de transferência. */
object DesktopActions {

    private val os get() = OsInfo.current

    /** Abre o arquivo no programa padrão do sistema. */
    fun open(file: File): Boolean = runCatching {
        if (!file.exists()) return false
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(file)
        } else {
            launch(opener(file.absolutePath))
        }
        true
    }.getOrElse { runCatching { launch(opener(file.absolutePath)); true }.getOrDefault(false) }

    /** Mostra o arquivo selecionado no Explorador (Windows) ou abre a pasta (Linux). */
    fun showInFolder(file: File): Boolean = runCatching {
        when {
            os.isWindows && file.exists() -> launch(listOf("explorer.exe", "/select,", file.absolutePath))
            else -> {
                val dir = if (file.isDirectory) file else file.parentFile
                dir.mkdirs()
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(dir)
                } else {
                    launch(opener(dir.absolutePath))
                }
            }
        }
        true
    }.getOrDefault(false)

    fun openFolder(dir: File): Boolean = runCatching {
        dir.mkdirs()
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(dir)
        } else {
            launch(opener(dir.absolutePath))
        }
        true
    }.getOrDefault(false)

    /** Abre um link no navegador. */
    fun browse(url: String): Boolean = runCatching {
        if (!url.startsWith("https://", ignoreCase = true) && !url.startsWith("http://", ignoreCase = true)) return false
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        } else {
            launch(opener(url))
        }
        true
    }.getOrDefault(false)

    /** Texto da área de transferência, se houver. */
    fun clipboardText(): String? = runCatching {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) clipboard.getData(DataFlavor.stringFlavor) as? String else null
    }.getOrNull()

    /** Janela do sistema para escolher uma pasta. */
    fun chooseFolder(title: String, current: File?): File? {
        val chooser = JFileChooser(current?.takeIf { it.isDirectory } ?: File(System.getProperty("user.home"))).apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    // Nunca via "cmd /c start": o cmd interpretaria "&" de uma URL como outro comando.
    private fun opener(target: String): List<String> = when (os.family) {
        OsInfo.Family.WINDOWS -> listOf("rundll32.exe", "url.dll,FileProtocolHandler", target)
        OsInfo.Family.MAC -> listOf("open", target)
        else -> listOf("xdg-open", target)
    }

    private fun launch(command: List<String>) {
        ProcessBuilder(command).redirectErrorStream(true).start()
    }
}
