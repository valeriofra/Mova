package io.github.valeriofra.mova.desktop.system

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitDialogParent
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import java.awt.Window
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Janela nativa do sistema para escolher uma pasta: a do Explorador no Windows e
 * a do portal de arquivos no Linux (no Flatpak, ela também libera o acesso à
 * pasta escolhida). Se a janela nativa falhar, usa a do Java.
 */
object FolderPicker {

    suspend fun choose(title: String, start: File?, parent: Window?): File? {
        val initial = start?.takeIf { it.isDirectory } ?: start?.parentFile?.takeIf { it.isDirectory }
        return try {
            FileKit.openDirectoryPicker(
                directory = initial?.let(::PlatformFile),
                dialogSettings = FileKitDialogSettings(title = title, parent = parent?.let { FileKitDialogParent.awt(it) }),
            )?.file
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            withContext(Dispatchers.Main) { DesktopActions.chooseFolder(title, initial) }
        }
    }
}
