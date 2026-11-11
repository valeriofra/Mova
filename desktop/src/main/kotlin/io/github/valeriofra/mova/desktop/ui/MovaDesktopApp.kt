package io.github.valeriofra.mova.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.valeriofra.mova.core.format.Formatters
import io.github.valeriofra.mova.desktop.AppContainer
import io.github.valeriofra.mova.desktop.BuildInfo
import io.github.valeriofra.mova.desktop.data.TaskStatus
import io.github.valeriofra.mova.desktop.engine.DesktopEngine
import io.github.valeriofra.mova.desktop.engine.EngineState
import io.github.valeriofra.mova.desktop.resources.*
import io.github.valeriofra.mova.desktop.system.DesktopActions
import io.github.valeriofra.mova.desktop.ui.components.MovaWordmark
import io.github.valeriofra.mova.desktop.ui.theme.MovaColors
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

enum class Destination(val icon: ImageVector, val label: StringResource) {
    HOME(Icons.Rounded.Home, Res.string.nav_home),
    QUEUE(Icons.Rounded.Download, Res.string.nav_queue),
    LIBRARY(Icons.Rounded.VideoLibrary, Res.string.nav_library),
    SETTINGS(Icons.Rounded.Settings, Res.string.nav_settings),
}

/** Janela principal: barra lateral + área da tela escolhida. */
@Composable
fun MovaDesktopApp(
    container: AppContainer,
    home: HomeModel,
    destination: Destination,
    onNavigate: (Destination) -> Unit,
    snackbar: SnackbarHostState,
) {
    val tasks by container.store.tasks.collectAsState()
    val settings by container.settings.settings.collectAsState()
    val engineState by container.engine.state.collectAsState()
    val scope = rememberCoroutineScope()
    var temporaryBytes by remember { mutableLongStateOf(0L) }

    fun notify(message: StringResource) = scope.launch { snackbar.showSnackbar(getString(message)) }

    LaunchedEffect(destination) {
        if (destination == Destination.LIBRARY) withContext(Dispatchers.IO) { container.manager.verifyFiles() }
        if (destination == Destination.SETTINGS) temporaryBytes = withContext(Dispatchers.IO) { container.manager.temporaryBytes(container.paths.workDir) }
    }

    Row(Modifier.fillMaxSize().background(MovaColors.Background)) {
        Sidebar(
            destination = destination,
            onNavigate = onNavigate,
            activeCount = tasks.count { it.status.isActive },
            engineState = engineState,
        )
        VerticalDivider(color = MovaColors.Outline)
        Box(Modifier.fillMaxSize()) {
            when (destination) {
                Destination.HOME -> HomeScreen(home, engineState) { count ->
                    if (count > 0) {
                        onNavigate(Destination.QUEUE)
                        scope.launch { snackbar.showSnackbar(getPluralString(Res.plurals.enqueued_message, count, count)) }
                    }
                }
                Destination.QUEUE -> QueueScreen(
                    tasks = tasks,
                    actions = QueueActions(
                        pause = container.manager::pause,
                        resume = container.manager::resume,
                        cancel = container.manager::cancel,
                        retry = container.manager::retry,
                        dismiss = container.manager::removeRecord,
                    ),
                    onOpenHome = { onNavigate(Destination.HOME) },
                    onOpenLibrary = { onNavigate(Destination.LIBRARY) },
                )
                Destination.LIBRARY -> LibraryScreen(
                    tasks = tasks,
                    autoDeleteDays = settings.autoDeleteDays,
                    actions = LibraryActions(
                        open = { task ->
                            val file = task.outputPath?.let(::File)
                            if (file == null || !file.exists()) {
                                container.manager.verifyFiles()
                                notify(Res.string.library_file_missing)
                            } else if (!DesktopActions.open(file)) {
                                notify(Res.string.library_no_app)
                            }
                        },
                        showInFolder = { task -> task.outputPath?.let { DesktopActions.showInFolder(File(it)) } },
                        delete = { task -> notify(if (container.manager.deleteFile(task.id)) Res.string.library_deleted else Res.string.library_delete_failed) },
                        removeRecord = { task -> container.manager.removeRecord(task.id) },
                        clearHistory = {
                            container.manager.clearHistory()
                            notify(Res.string.library_history_cleared)
                        },
                        openFolder = { DesktopActions.openFolder(container.manager.destination) },
                    ),
                    onOpenHome = { onNavigate(Destination.HOME) },
                )
                Destination.SETTINGS -> SettingsScreen(
                    settings = settings,
                    destination = container.manager.destination.absolutePath,
                    engineState = engineState,
                    temporaryBytes = temporaryBytes,
                    actions = SettingsActions(
                        themeMode = container.settings::setThemeMode,
                        language = container.settings::setLanguage,
                        chooseFolder = {
                            scope.launch {
                                val title = getString(Res.string.settings_folder_choose)
                                DesktopActions.chooseFolder(title, container.manager.destination)?.let { container.settings.setDownloadDir(it.absolutePath) }
                            }
                        },
                        defaultFolder = { container.settings.setDownloadDir(null) },
                        maxConcurrent = container.settings::setMaxConcurrent,
                        autoDelete = { days ->
                            container.settings.setAutoDeleteDays(days)
                            if (days > 0) {
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) { container.manager.deleteOlderThan(days) }
                                    snackbar.showSnackbar(
                                        if (result.filesDeleted > 0) {
                                            getPluralString(Res.plurals.settings_auto_delete_done, result.filesDeleted, result.filesDeleted)
                                        } else {
                                            getString(Res.string.settings_auto_delete_enabled)
                                        },
                                    )
                                }
                            }
                        },
                        cleanTemporary = {
                            scope.launch {
                                val freed = withContext(Dispatchers.IO) { container.manager.cleanTemporary(container.paths.workDir) }
                                temporaryBytes = withContext(Dispatchers.IO) { container.manager.temporaryBytes(container.paths.workDir) }
                                snackbar.showSnackbar(getString(Res.string.settings_temp_cleaned, Formatters.bytes(freed)))
                            }
                        },
                        updateEngine = {
                            scope.launch {
                                val text = when (val result = container.engine.update()) {
                                    is DesktopEngine.UpdateResult.Updated -> getString(Res.string.settings_engine_updated, result.version.orEmpty())
                                    is DesktopEngine.UpdateResult.UpToDate -> getString(Res.string.settings_engine_up_to_date)
                                    is DesktopEngine.UpdateResult.Failed -> getString(Res.string.settings_engine_update_failed)
                                }
                                snackbar.showSnackbar(text)
                            }
                        },
                        openEngineFolder = { DesktopActions.openFolder(container.paths.engineDir) },
                    ),
                )
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(24.dp)) { data ->
                Snackbar(data, containerColor = MovaColors.SurfaceHighest, contentColor = MovaColors.TextPrimary, actionColor = MovaColors.Accent)
            }
        }
    }
}

@Composable
private fun Sidebar(destination: Destination, onNavigate: (Destination) -> Unit, activeCount: Int, engineState: EngineState) {
    Column(Modifier.width(232.dp).fillMaxHeight().background(MovaColors.Surface).padding(horizontal = 14.dp, vertical = 20.dp)) {
        MovaWordmark(Modifier.padding(start = 8.dp, bottom = 28.dp))
        Destination.entries.forEach { item ->
            val selected = item == destination
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) MovaColors.Highlight else MovaColors.Surface)
                    .clickable { onNavigate(item) }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val color = if (selected) MovaColors.Accent else MovaColors.TextSecondary
                Icon(item.icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Text(stringResource(item.label), style = MaterialTheme.typography.titleSmall, color = if (selected) MovaColors.TextPrimary else MovaColors.TextSecondary, modifier = Modifier.weight(1f))
                if (item == Destination.QUEUE && activeCount > 0) {
                    Badge(containerColor = MovaColors.Blue, contentColor = androidx.compose.ui.graphics.Color.White) { Text(activeCount.toString()) }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        val (statusText, statusColor) = when (engineState) {
            is EngineState.Ready -> stringResource(Res.string.sidebar_engine_ready) to MovaColors.Success
            is EngineState.Preparing -> stringResource(Res.string.settings_engine_preparing) to MovaColors.Warning
            is EngineState.Updating -> stringResource(Res.string.settings_engine_updating) to MovaColors.Warning
            is EngineState.Failed -> stringResource(Res.string.settings_engine_failed) to MovaColors.Error
            EngineState.NotReady -> stringResource(Res.string.settings_engine_not_ready) to MovaColors.TextTertiary
        }
        Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Bolt, contentDescription = null, tint = statusColor, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(statusText, style = MaterialTheme.typography.bodySmall, color = MovaColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(4.dp))
        Text("v${BuildInfo.VERSION}", style = MaterialTheme.typography.labelSmall, color = MovaColors.TextTertiary, modifier = Modifier.padding(start = 8.dp))
    }
}
