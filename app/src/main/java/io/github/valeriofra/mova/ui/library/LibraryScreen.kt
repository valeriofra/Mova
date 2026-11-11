package io.github.valeriofra.mova.ui.library

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.valeriofra.mova.R
import io.github.valeriofra.mova.core.format.Formatters
import io.github.valeriofra.mova.data.db.DownloadEntity
import io.github.valeriofra.mova.data.db.DownloadStatus
import io.github.valeriofra.mova.ui.components.AccentTextButton
import io.github.valeriofra.mova.ui.components.EmptyState
import io.github.valeriofra.mova.ui.components.InfoBanner
import io.github.valeriofra.mova.ui.components.MediaThumbnail
import io.github.valeriofra.mova.ui.components.MovaCard
import io.github.valeriofra.mova.ui.components.MovaTopBar
import io.github.valeriofra.mova.ui.components.PrimaryButton
import io.github.valeriofra.mova.ui.components.StatusPill
import io.github.valeriofra.mova.ui.components.TitleAndSubtitle
import io.github.valeriofra.mova.ui.labelRes
import io.github.valeriofra.mova.ui.theme.MovaColors
import java.io.FileNotFoundException

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    snackbarHost: SnackbarHostState,
    onOpenHome: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.messages.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current

    LaunchedEffect(Unit) { viewModel.verifyFiles() }
    LaunchedEffect(message) {
        val m = message ?: return@LaunchedEffect
        viewModel.messageShown()
        val text = resources.getString(
            when (m) {
                LibraryMessage.Deleted -> R.string.library_deleted
                LibraryMessage.DeleteFailed -> R.string.library_delete_failed
                LibraryMessage.FileMissing -> R.string.library_file_missing
                LibraryMessage.HistoryCleared -> R.string.library_history_cleared
                LibraryMessage.NoAppToOpen -> R.string.library_no_app
            },
        )
        snackbarHost.showSnackbar(text)
    }

    LibraryContent(
        state = state,
        onSelectTab = viewModel::selectTab,
        onSelectFilter = viewModel::selectFilter,
        onOpen = { item ->
            when (context.openFile(item)) {
                OpenResult.OK -> Unit
                OpenResult.MISSING -> viewModel.onFileMissing(item)
                OpenResult.NO_APP -> { /* sem app compatível */ }
            }
        },
        onShare = { item -> context.shareFile(item) },
        onDelete = viewModel::deleteFile,
        onRemoveRecord = viewModel::removeRecord,
        onClearHistory = viewModel::clearHistory,
        onOpenHome = onOpenHome,
    )
}

@Composable
fun LibraryContent(
    state: LibraryUiState,
    onSelectTab: (LibraryTab) -> Unit,
    onSelectFilter: (LibraryFilter) -> Unit,
    onOpen: (DownloadEntity) -> Unit,
    onShare: (DownloadEntity) -> Unit,
    onDelete: (DownloadEntity) -> Unit,
    onRemoveRecord: (DownloadEntity) -> Unit,
    onClearHistory: () -> Unit,
    onOpenHome: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf<DownloadEntity?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        MovaTopBar(
            title = stringResource(R.string.library_title),
            actions = {
                if (state.tab == LibraryTab.HISTORY && state.history.isNotEmpty()) {
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(
                            Icons.Rounded.DeleteSweep,
                            contentDescription = stringResource(R.string.library_clear_history),
                            tint = MovaColors.TextPrimary,
                        )
                    }
                }
            },
        )
        val tabs = listOf(LibraryTab.FILES to R.string.library_tab_files, LibraryTab.HISTORY to R.string.library_tab_history)
        SecondaryTabRow(
            selectedTabIndex = state.tab.ordinal,
            containerColor = Color.Transparent,
            contentColor = MovaColors.Accent,
            divider = {},
            indicator = {
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(state.tab.ordinal),
                    color = MovaColors.Accent,
                )
            },
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            tabs.forEach { (tab, label) ->
                Tab(
                    selected = state.tab == tab,
                    onClick = { onSelectTab(tab) },
                    text = { Text(stringResource(label)) },
                    selectedContentColor = MovaColors.Accent,
                    unselectedContentColor = MovaColors.TextSecondary,
                )
            }
        }

        when (state.tab) {
            LibraryTab.FILES -> FilesTab(state, onSelectFilter, onOpen, onShare, { confirmDelete = it }, onOpenHome)
            LibraryTab.HISTORY -> HistoryTab(state, onOpen, onRemoveRecord)
        }
    }

    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.library_delete_title)) },
            text = { Text(stringResource(R.string.library_delete_message, item.fileName)) },
            confirmButton = {
                AccentTextButton(stringResource(R.string.action_delete), onClick = {
                    onDelete(item)
                    confirmDelete = null
                })
            },
            dismissButton = { AccentTextButton(stringResource(R.string.action_keep), onClick = { confirmDelete = null }) },
            containerColor = MovaColors.SurfaceHigh,
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.library_clear_history)) },
            text = { Text(stringResource(R.string.library_clear_history_message)) },
            confirmButton = {
                AccentTextButton(stringResource(R.string.action_clear), onClick = {
                    onClearHistory()
                    confirmClear = false
                })
            },
            dismissButton = { AccentTextButton(stringResource(R.string.action_keep), onClick = { confirmClear = false }) },
            containerColor = MovaColors.SurfaceHigh,
        )
    }
}

@Composable
private fun FilesTab(
    state: LibraryUiState,
    onSelectFilter: (LibraryFilter) -> Unit,
    onOpen: (DownloadEntity) -> Unit,
    onShare: (DownloadEntity) -> Unit,
    onDelete: (DownloadEntity) -> Unit,
    onOpenHome: () -> Unit,
) {
    if (state.loaded && state.totalFiles == 0) {
        EmptyState(
            icon = Icons.Rounded.VideoLibrary,
            title = stringResource(R.string.library_empty_title),
            message = stringResource(R.string.library_empty_message),
            action = { PrimaryButton(stringResource(R.string.queue_empty_action), onOpenHome) },
        )
        return
    }
    Column {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibraryFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { onSelectFilter(filter) },
                    label = { Text(stringResource(filter.labelRes())) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MovaColors.Surface,
                        labelColor = MovaColors.TextSecondary,
                        selectedContainerColor = MovaColors.Blue.copy(alpha = 0.22f),
                        selectedLabelColor = MovaColors.Accent,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = state.filter == filter,
                        borderColor = MovaColors.Outline,
                        selectedBorderColor = MovaColors.BlueBright,
                    ),
                )
            }
        }
        if (state.files.isEmpty()) {
            Text(
                stringResource(R.string.library_filter_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MovaColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            if (state.autoDeleteDays > 0) {
                item(key = "auto-delete") {
                    InfoBanner(
                        icon = Icons.Rounded.AutoDelete,
                        text = pluralStringResource(R.plurals.library_auto_delete_notice, state.autoDeleteDays, state.autoDeleteDays),
                        color = MovaColors.Warning,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
            items(state.files, key = { it.id }) { item ->
                FileRow(item, onOpen, onShare, onDelete)
            }
        }
    }
}

@Composable
private fun FileRow(
    item: DownloadEntity,
    onOpen: (DownloadEntity) -> Unit,
    onShare: (DownloadEntity) -> Unit,
    onDelete: (DownloadEntity) -> Unit,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val subtitle = listOfNotNull(
        item.totalBytes?.let(Formatters::bytes),
        item.completedAt?.let { formatDate(context, it) },
    ).joinToString(" · ")
    MovaCard(
        onClick = { onOpen(item) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
    ) {
        Row(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            MediaThumbnail(uri = item.outputUri, kind = item.mediaKind, size = 56.dp)
            Spacer(Modifier.width(12.dp))
            TitleAndSubtitle(title = item.fileName, subtitle = subtitle, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more), tint = MovaColors.TextSecondary)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = MovaColors.SurfaceHigh) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_open)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                        onClick = { menu = false; onOpen(item) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_share)) },
                        leadingIcon = { Icon(Icons.Rounded.Share, null) },
                        onClick = { menu = false; onShare(item) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) },
                        onClick = { menu = false; onDelete(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryTab(
    state: LibraryUiState,
    onOpen: (DownloadEntity) -> Unit,
    onRemoveRecord: (DownloadEntity) -> Unit,
) {
    if (state.loaded && state.history.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.History,
            title = stringResource(R.string.library_history_empty_title),
            message = stringResource(R.string.library_history_empty_message),
        )
        return
    }
    val context = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp)) {
        items(state.history, key = { it.id }) { item ->
            val available = item.status == DownloadStatus.COMPLETED && !item.fileMissing
            val (statusText, color, icon) = when {
                available -> Triple(stringResource(R.string.status_completed), MovaColors.Success, Icons.Rounded.CheckCircle)
                item.status == DownloadStatus.COMPLETED -> Triple(stringResource(R.string.history_file_removed), MovaColors.TextSecondary, Icons.Rounded.RemoveCircleOutline)
                item.status == DownloadStatus.FAILED -> Triple(stringResource(R.string.status_failed), MovaColors.Error, Icons.Rounded.ErrorOutline)
                else -> Triple(stringResource(R.string.status_canceled), MovaColors.TextSecondary, Icons.Rounded.Block)
            }
            val host = Uri.parse(item.sourceUrl).host
            MovaCard(
                onClick = if (available) ({ onOpen(item) }) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
            ) {
                Row(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    MediaThumbnail(
                        uri = if (available) item.outputUri else null,
                        kind = item.mediaKind,
                        size = 48.dp,
                        remoteUrl = item.thumbnailUrl,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        TitleAndSubtitle(
                            title = item.fileName,
                            subtitle = listOfNotNull(formatDate(context, item.updatedAt), host).joinToString(" · "),
                        )
                        Spacer(Modifier.size(6.dp))
                        StatusPill(text = statusText, color = color, icon = icon)
                    }
                    if (!available) {
                        IconButton(onClick = { onRemoveRecord(item) }) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = stringResource(R.string.history_remove),
                                tint = MovaColors.TextSecondary,
                            )
                        }
                    } else {
                        Spacer(Modifier.width(12.dp))
                    }
                }
            }
        }
    }
}

private fun LibraryFilter.labelRes(): Int = when (this) {
    LibraryFilter.ALL -> R.string.filter_all
    LibraryFilter.VIDEO -> R.string.filter_video
    LibraryFilter.AUDIO -> R.string.filter_audio
    LibraryFilter.IMAGE -> R.string.filter_image
    LibraryFilter.OTHER -> R.string.filter_other
}

private fun formatDate(context: Context, millis: Long): String =
    DateUtils.formatDateTime(
        context,
        millis,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
    )

private enum class OpenResult { OK, MISSING, NO_APP }

private fun Context.openFile(item: DownloadEntity): OpenResult {
    val uri = item.outputUri?.let(Uri::parse) ?: return OpenResult.MISSING
    try {
        contentResolver.openFileDescriptor(uri, "r")?.close() ?: return OpenResult.MISSING
    } catch (_: FileNotFoundException) {
        return OpenResult.MISSING
    } catch (_: SecurityException) {
        return OpenResult.MISSING
    }
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, item.mimeType ?: "*/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        startActivity(intent)
        OpenResult.OK
    } catch (_: ActivityNotFoundException) {
        android.widget.Toast.makeText(this, R.string.library_no_app, android.widget.Toast.LENGTH_SHORT).show()
        OpenResult.NO_APP
    }
}

private fun Context.shareFile(item: DownloadEntity) {
    val uri = item.outputUri?.let(Uri::parse) ?: return
    val send = Intent(Intent.ACTION_SEND)
        .setType(item.mimeType ?: "*/*")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(
        Intent.createChooser(send, getString(R.string.action_share))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
