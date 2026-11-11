package io.github.valeriofra.mova.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import io.github.valeriofra.mova.desktop.data.DownloadEvent
import io.github.valeriofra.mova.desktop.data.ThemeMode
import io.github.valeriofra.mova.desktop.i18n.Localization
import io.github.valeriofra.mova.desktop.resources.*
import io.github.valeriofra.mova.desktop.system.AppPaths
import io.github.valeriofra.mova.desktop.system.SingleInstance
import io.github.valeriofra.mova.desktop.ui.Destination
import io.github.valeriofra.mova.desktop.ui.HomeModel
import io.github.valeriofra.mova.desktop.ui.MovaDesktopApp
import io.github.valeriofra.mova.desktop.ui.components.LocalThumbnails
import io.github.valeriofra.mova.desktop.ui.theme.MovaTheme
import java.awt.Dimension
import java.io.File
import java.time.Instant
import javax.swing.JOptionPane
import javax.swing.UIManager
import kotlin.system.exitProcess
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

fun main() {
    val paths = AppPaths.detect()
    installCrashLog(paths.logFile)
    if (!SingleInstance.acquire(paths.lockFile)) {
        JOptionPane.showMessageDialog(null, SingleInstance.alreadyRunningMessage(), "MOVA", JOptionPane.INFORMATION_MESSAGE)
        exitProcess(0)
    }
    // Diálogos do sistema (escolher pasta) com a aparência nativa.
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }

    val container = AppContainer(paths)
    Localization.apply(container.settings.current.language)
    container.start()

    application {
        val settings by container.settings.settings.collectAsState()
        val windowState = rememberWindowState(width = 1200.dp, height = 800.dp, position = WindowPosition(Alignment.Center))
        val trayState = rememberTrayState()
        val homeScope = rememberCoroutineScope()
        val home = remember { HomeModel(container.analyzer, container.manager, homeScope) }
        var destination by remember { mutableStateOf(Destination.HOME) }
        val snackbar = remember { SnackbarHostState() }
        val icon = painterResource(Res.drawable.mova_icon)

        fun quit() {
            container.shutdown()
            exitApplication()
        }

        if (isTraySupported) {
            Tray(
                icon = icon,
                state = trayState,
                tooltip = "MOVA",
                onAction = { windowState.isMinimized = false },
                menu = {
                    Item(stringResource(Res.string.tray_open), onClick = { windowState.isMinimized = false })
                    Item(stringResource(Res.string.tray_quit), onClick = ::quit)
                },
            )
        }

        // Aviso do sistema ao concluir ou falhar um download.
        LaunchedEffect(Unit) {
            container.manager.events.collect { event ->
                if (!isTraySupported) return@collect
                val notification = when (event) {
                    is DownloadEvent.Completed -> Notification(getString(Res.string.notif_finished_title), event.task.fileName, Notification.Type.Info)
                    is DownloadEvent.Failed -> Notification(getString(Res.string.notif_failed_title), event.task.title, Notification.Type.Warning)
                }
                trayState.sendNotification(notification)
            }
        }

        Window(onCloseRequest = ::quit, state = windowState, title = "MOVA", icon = icon) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(1000, 660) }
            val systemDark = isSystemInDarkTheme()
            val dark = when (settings.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> systemDark
            }
            // Trocar o idioma recompõe a interface inteira com os novos textos.
            key(settings.language) {
                remember(settings.language) { Localization.apply(settings.language) }
                MovaTheme(darkTheme = dark) {
                    CompositionLocalProvider(LocalThumbnails provides container.thumbnails) {
                        MovaDesktopApp(container, home, destination, { destination = it }, snackbar)
                    }
                }
            }
        }
    }
}

/** Erros inesperados vão para mova.log (na pasta de dados), para diagnóstico. */
private fun installCrashLog(log: File) {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
        runCatching {
            log.appendText("${Instant.now()} [${thread.name}] ${error.stackTraceToString()}\n")
        }
        previous?.uncaughtException(thread, error)
    }
}
