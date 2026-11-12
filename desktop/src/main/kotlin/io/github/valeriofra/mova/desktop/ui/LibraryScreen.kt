package io.github.valeriofra.mova.desktop.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.valeriofra.mova.core.format.Formatters
import io.github.valeriofra.mova.core.model.MediaKind
import io.github.valeriofra.mova.desktop.data.DownloadTask
import io.github.valeriofra.mova.desktop.data.TaskStatus
import io.github.valeriofra.mova.desktop.resources.*
import io.github.valeriofra.mova.desktop.ui.components.AccentTextButton
import io.github.valeriofra.mova.desktop.ui.components.EmptyState
import io.github.valeriofra.mova.desktop.ui.components.InfoBanner
import io.github.valeriofra.mova.desktop.ui.components.MediaThumbnail
import io.github.valeriofra.mova.desktop.ui.components.MovaCard
import io.github.valeriofra.mova.desktop.ui.components.MovaChip
import io.github.valeriofra.mova.desktop.ui.components.PrimaryButton
import io.github.valeriofra.mova.desktop.ui.components.ScreenTitle
import androidx.compose.foundation.lazy.rememberLazyListState
import io.github.valeriofra.mova.desktop.ui.components.ListScrollbar
import io.github.valeriofra.mova.desktop.ui.components.SCROLLBAR_GUTTER
import io.github.valeriofra.mova.desktop.ui.components.SecondaryButton
import io.github.valeriofra.mova.desktop.ui.components.StatusPill
import io.github.valeriofra.mova.desktop.ui.components.LocalScreenPadding
import io.github.valeriofra.mova.desktop.ui.theme.MovaColors
import java.text.DateFormat
import java.util.Date
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

enum class LibraryTab { FILES, HISTORY }

enum class LibraryFilter(val label: StringResource) {
    ALL(Res.string.library_filter_all),
    VIDEO(Res.string.library_filter_video),
    AUDIO(Res.string.library_filter_audio),
    IMAGE(Res.string.library_filter_image),
    OTHER(Res.string.library_filter_other);

    fun matches(kind: MediaKind): Boolean = when (this) {
        ALL -> true
        VIDEO -> kind == MediaKind.VIDEO
        AUDIO -> kind == MediaKind.AUDIO
        IMAGE -> kind == MediaKind.IMAGE
        OTHER -> kind == MediaKind.DOCUMENT || kind == MediaKind.OTHER
    }
}

data class LibraryActions(
    val open: (DownloadTask) -> Unit = {},
    val showInFolder: (DownloadTask) -> Unit = {},
    val delete: (DownloadTask) -> Unit = {},
    val removeRecord: (DownloadTask) -> Unit = {},
    val clearHistory: () -> Unit = {},
    val openFolder: () -> Unit = {},
)

@Composable
fun LibraryScreen(
    tasks: List<DownloadTask>,
    autoDeleteDays: Int,
    actions: LibraryActions,
    onOpenHome: () -> Unit,
    initialTab: LibraryTab = LibraryTab.FILES,
) {
    var tab by remember { mutableStateOf(initialTab) }
    var filter by remember { mutableStateOf(LibraryFilter.ALL) }
    var query by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<DownloadTask?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val files = tasks.filter { it.status == TaskStatus.COMPLETED && !it.fileMissing }.sortedByDescending { it.completedAt }
    val history = tasks.filter { it.status.isFinished }.sortedByDescending { it.updatedAt }

    Column(Modifier.fillMaxSize().padding(horizontal = LocalScreenPadding.current, vertical = 20.dp)) {
        ScreenTitle(stringResource(Res.string.library_title)) {
            if (tab == LibraryTab.HISTORY && history.isNotEmpty()) {
                SecondaryButton(stringResource(Res.string.library_clear_history), { confirmClear = true }, icon = Icons.Rounded.DeleteSweep)
                Spacer(Modifier.width(10.dp))
            }
            SecondaryButton(stringResource(Res.string.library_open_folder), actions.openFolder, icon = Icons.Rounded.FolderOpen)
        }
        val tabs = listOf(LibraryTab.FILES to Res.string.library_tab_files, LibraryTab.HISTORY to Res.string.library_tab_history)
        SecondaryTabRow(
            selectedTabIndex = tab.ordinal,
            containerColor = Color.Transparent,
            contentColor = MovaColors.Accent,
            divider = {},
            indicator = { TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tab.ordinal), color = MovaColors.Accent) },
            modifier = Modifier.widthIn(max = 360.dp),
        ) {
            tabs.forEach { (value, label) ->
                Tab(
                    selected = tab == value,
                    onClick = { tab = value },
                    text = { Text(stringResource(label)) },
                    selectedContentColor = MovaColors.Accent,
                    unselectedContentColor = MovaColors.TextSecondary,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        when (tab) {
            LibraryTab.FILES -> FilesTab(files, autoDeleteDays, filter, { filter = it }, query, { query = it }, actions, { confirmDelete = it }, onOpenHome)
            LibraryTab.HISTORY -> HistoryTab(history, actions)
        }
    }

    confirmDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(Res.string.library_delete_title)) },
            text = { Text(stringResource(Res.string.library_delete_message, task.fileName)) },
            confirmButton = {
                AccentTextButton(stringResource(Res.string.action_delete), {
                    actions.delete(task)
                    confirmDelete = null
                })
            },
            dismissButton = { AccentTextButton(stringResource(Res.string.action_keep), { confirmDelete = null }) },
            containerColor = MovaColors.SurfaceHigh,
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(Res.string.library_clear_history)) },
            text = { Text(stringResource(Res.string.library_clear_history_message)) },
            confirmButton = {
                AccentTextButton(stringResource(Res.string.action_clear), {
                    actions.clearHistory()
                    confirmClear = false
                })
            },
            dismissButton = { AccentTextButton(stringResource(Res.string.action_keep), { confirmClear = false }) },
            containerColor = MovaColors.SurfaceHigh,
        )
    }
}

@Composable
private fun FilesTab(
    files: List<DownloadTask>,
    autoDeleteDays: Int,
    filter: LibraryFilter,
    onFilter: (LibraryFilter) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    actions: LibraryActions,
    onDelete: (DownloadTask) -> Unit,
    onOpenHome: () -> Unit,
) {
    if (files.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.VideoLibrary,
            title = stringResource(Res.string.library_empty_title),
            message = stringResource(Res.string.library_empty_message),
            action = { PrimaryButton(stringResource(Res.string.queue_empty_action), onOpenHome) },
        )
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryFilter.entries.forEach { MovaChip(stringResource(it.label), filter == it, { onFilter(it) }) }
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            placeholder = { Text(stringResource(Res.string.library_search), color = MovaColors.TextTertiary) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MovaColors.TextSecondary) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MovaColors.BlueBright,
                unfocusedBorderColor = MovaColors.Outline,
                focusedContainerColor = MovaColors.Surface,
                unfocusedContainerColor = MovaColors.Surface,
                focusedTextColor = MovaColors.TextPrimary,
                unfocusedTextColor = MovaColors.TextPrimary,
            ),
            modifier = Modifier.padding(start = 12.dp).widthIn(min = 150.dp, max = 240.dp),
        )
    }
    Spacer(Modifier.height(12.dp))
    val visible = files.filter { filter.matches(it.mediaKind) && (query.isBlank() || it.fileName.contains(query.trim(), ignoreCase = true) || it.title.contains(query.trim(), ignoreCase = true)) }
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
    LazyColumn(state = listState, contentPadding = PaddingValues(end = SCROLLBAR_GUTTER, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (autoDeleteDays > 0) {
            item(key = "auto-delete") {
                InfoBanner(Icons.Rounded.AutoDelete, pluralStringResource(Res.plurals.library_auto_delete_notice, autoDeleteDays, autoDeleteDays), color = MovaColors.Warning)
            }
        }
        if (visible.isEmpty()) {
            item(key = "empty") {
                Text(stringResource(Res.string.library_filter_empty), style = MaterialTheme.typography.bodyMedium, color = MovaColors.TextSecondary, modifier = Modifier.padding(vertical = 12.dp))
            }
        }
        items(visible, key = { it.id }) { task -> FileRow(task, actions, onDelete) }
    }
    ListScrollbar(listState)
    }
}

@Composable
private fun FileRow(task: DownloadTask, actions: LibraryActions, onDelete: (DownloadTask) -> Unit) {
    MovaCard(Modifier.fillMaxWidth(), onClick = { actions.open(task) }) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            MediaThumbnail(task.thumbnailUrl, task.mediaKind)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(task.fileName, style = MaterialTheme.typography.titleSmall, color = MovaColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    listOfNotNull(task.totalBytes?.let(Formatters::bytes), task.completedAt?.let(::formatDate), platformName(task.platform)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MovaColors.TextSecondary, maxLines = 1,
                )
            }
            ActionIcon(Icons.Rounded.PlayArrow, stringResource(Res.string.action_open)) { actions.open(task) }
            ActionIcon(Icons.Rounded.Folder, stringResource(Res.string.library_show_in_folder)) { actions.showInFolder(task) }
            ActionIcon(Icons.Rounded.DeleteOutline, stringResource(Res.string.action_delete)) { onDelete(task) }
        }
    }
}

@Composable
private fun HistoryTab(history: List<DownloadTask>, actions: LibraryActions) {
    if (history.isEmpty()) {
        EmptyState(Icons.Rounded.History, stringResource(Res.string.library_history_empty_title), stringResource(Res.string.library_history_empty_message))
        return
    }
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
    LazyColumn(state = listState, contentPadding = PaddingValues(end = SCROLLBAR_GUTTER, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(history, key = { it.id }) { task ->
            MovaCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    MediaThumbnail(task.thumbnailUrl, task.mediaKind, width = 72.dp, height = 40.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(task.title, style = MaterialTheme.typography.titleSmall, color = MovaColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(formatDate(task.updatedAt), platformName(task.platform), task.sourceUrl).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MovaColors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    val (label, color) = when {
                        task.status == TaskStatus.COMPLETED && task.fileMissing -> Res.string.library_status_deleted to MovaColors.TextSecondary
                        task.status == TaskStatus.COMPLETED -> Res.string.status_completed to MovaColors.Success
                        task.status == TaskStatus.FAILED -> Res.string.status_failed to MovaColors.Error
                        else -> Res.string.status_canceled to MovaColors.TextSecondary
                    }
                    StatusPill(stringResource(label), color)
                    ActionIcon(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(Res.string.analysis_open_browser)) {
                        io.github.valeriofra.mova.desktop.system.DesktopActions.browse(task.sourceUrl)
                    }
                    ActionIcon(Icons.Rounded.RemoveCircleOutline, stringResource(Res.string.library_remove_record)) { actions.removeRecord(task) }
                }
            }
        }
    }
    ListScrollbar(listState)
    }
}

fun formatDate(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
