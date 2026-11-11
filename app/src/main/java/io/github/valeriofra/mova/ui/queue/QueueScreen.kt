package io.github.valeriofra.mova.ui.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import io.github.valeriofra.mova.ui.components.MovaProgressBar
import io.github.valeriofra.mova.ui.components.MovaTopBar
import io.github.valeriofra.mova.ui.components.PrimaryButton
import io.github.valeriofra.mova.ui.components.SectionHeader
import io.github.valeriofra.mova.ui.components.TitleAndSubtitle
import io.github.valeriofra.mova.ui.messageRes
import io.github.valeriofra.mova.ui.theme.MovaColors

@Composable
fun QueueScreen(
    viewModel: QueueViewModel,
    onOpenHome: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    QueueContent(
        state = state,
        onPause = viewModel::pause,
        onResume = viewModel::resume,
        onRetry = viewModel::retry,
        onCancel = viewModel::cancel,
        onOpenHome = onOpenHome,
        onOpenLibrary = onOpenLibrary,
    )
}

@Composable
fun QueueContent(
    state: QueueUiState,
    onPause: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    onOpenHome: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    var confirmCancel by remember { mutableStateOf<DownloadEntity?>(null) }
    val requestCancel: (DownloadEntity) -> Unit = { item ->
        if (item.downloadedBytes > 0 && item.status != DownloadStatus.FAILED) confirmCancel = item else onCancel(item.id)
    }

    Column(Modifier.fillMaxSize()) {
        MovaTopBar(title = stringResource(R.string.queue_title))
        if (state.loaded && state.isEmpty && state.recentlyCompleted.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
                title = stringResource(R.string.queue_empty_title),
                message = stringResource(R.string.queue_empty_message),
                action = {
                    PrimaryButton(
                        stringResource(R.string.queue_empty_action),
                        onOpenHome,
                        icon = Icons.Rounded.ContentPaste,
                    )
                },
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            if (state.waitingForNetwork) {
                item {
                    InfoBanner(
                        icon = Icons.Rounded.WifiOff,
                        text = stringResource(
                            if (state.wifiOnly) R.string.queue_waiting_wifi else R.string.queue_waiting_network,
                        ),
                        color = MovaColors.Warning,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            if (state.isEmpty) {
                item {
                    Text(
                        stringResource(R.string.queue_nothing_active),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MovaColors.TextSecondary,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
            section(R.string.queue_section_running, state.running) { item ->
                QueueItem(item, onPause, onResume, onRetry, requestCancel)
            }
            section(R.string.queue_section_queued, state.queued) { item ->
                QueueItem(item, onPause, onResume, onRetry, requestCancel, waiting = state.waitingForNetwork)
            }
            section(R.string.queue_section_paused, state.paused) { item ->
                QueueItem(item, onPause, onResume, onRetry, requestCancel)
            }
            section(R.string.queue_section_failed, state.failed) { item ->
                QueueItem(item, onPause, onResume, onRetry, requestCancel)
            }
            if (state.recentlyCompleted.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = stringResource(R.string.queue_section_completed),
                        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
                        action = { AccentTextButton(stringResource(R.string.home_see_library), onOpenLibrary) },
                    )
                }
                items(state.recentlyCompleted, key = { "done-${it.id}" }) { item ->
                    CompletedItem(item, onClick = onOpenLibrary)
                }
            }
        }
    }

    confirmCancel?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmCancel = null },
            title = { Text(stringResource(R.string.queue_cancel_title)) },
            text = { Text(stringResource(R.string.queue_cancel_message, item.fileName)) },
            confirmButton = {
                AccentTextButton(stringResource(R.string.queue_cancel_confirm), onClick = {
                    onCancel(item.id)
                    confirmCancel = null
                })
            },
            dismissButton = {
                AccentTextButton(stringResource(R.string.action_keep), onClick = { confirmCancel = null })
            },
            containerColor = MovaColors.SurfaceHigh,
        )
    }
}

private fun LazyListScope.section(
    title: Int,
    items: List<DownloadEntity>,
    content: @Composable (DownloadEntity) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "header-$title") {
        SectionHeader(
            title = "${stringResource(title)} (${items.size})",
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
    }
    items(items, key = { it.id }) { content(it) }
}

@Composable
private fun QueueItem(
    item: DownloadEntity,
    onPause: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onCancel: (DownloadEntity) -> Unit,
    waiting: Boolean = false,
) {
    val total = item.totalBytes
    val percent = Formatters.percent(item.downloadedBytes, total)
    val progressText = if (total != null) {
        stringResource(R.string.queue_progress_of, Formatters.bytes(item.downloadedBytes), Formatters.bytes(total))
    } else {
        Formatters.bytes(item.downloadedBytes)
    }
    val subtitle = when (item.status) {
        DownloadStatus.RUNNING -> buildList {
            add(progressText)
            if (item.bytesPerSecond > 0) add(Formatters.speed(item.bytesPerSecond))
            Formatters.remaining(item.downloadedBytes, total, item.bytesPerSecond)?.let {
                add(stringResource(R.string.queue_remaining, it))
            }
        }.joinToString(" · ")
        DownloadStatus.QUEUED -> stringResource(if (waiting) R.string.queue_status_waiting else R.string.status_queued)
        DownloadStatus.PAUSED -> if (item.downloadedBytes > 0) progressText else stringResource(R.string.status_paused)
        DownloadStatus.FAILED -> stringResource(item.errorCode.messageRes())
        else -> null
    }

    MovaCard(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
    ) {
        Column(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MediaThumbnail(uri = null, kind = item.mediaKind, size = 48.dp, remoteUrl = item.thumbnailUrl)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    TitleAndSubtitle(title = item.fileName, subtitle = null)
                    if (subtitle != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (item.status == DownloadStatus.FAILED) {
                                Icon(
                                    Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = MovaColors.Error,
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .size(14.dp),
                                )
                            }
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (item.status == DownloadStatus.FAILED) MovaColors.Error else MovaColors.TextSecondary,
                            )
                        }
                    }
                }
                if (item.status == DownloadStatus.RUNNING && percent != null) {
                    Text(
                        "$percent%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MovaColors.Accent,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                ItemActions(item, onPause, onResume, onRetry, onCancel)
            }
            if (item.status == DownloadStatus.RUNNING || item.status == DownloadStatus.PAUSED) {
                Spacer(Modifier.height(10.dp))
                MovaProgressBar(
                    progress = percent?.let { it / 100f },
                    color = if (item.status == DownloadStatus.PAUSED) MovaColors.TextTertiary else MovaColors.BlueBright,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            if (item.status == DownloadStatus.PAUSED && !item.supportsResume && item.downloadedBytes > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.queue_no_resume_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MovaColors.Warning,
                )
            }
        }
    }
}

@Composable
private fun ItemActions(
    item: DownloadEntity,
    onPause: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onCancel: (DownloadEntity) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        when (item.status) {
            DownloadStatus.RUNNING, DownloadStatus.QUEUED ->
                ActionIcon(Icons.Rounded.Pause, stringResource(R.string.action_pause)) { onPause(item.id) }
            DownloadStatus.PAUSED ->
                ActionIcon(Icons.Rounded.PlayArrow, stringResource(R.string.action_resume)) { onResume(item.id) }
            DownloadStatus.FAILED ->
                ActionIcon(Icons.Rounded.Refresh, stringResource(R.string.action_retry)) { onRetry(item.id) }
            else -> Unit
        }
        ActionIcon(Icons.Rounded.Close, stringResource(R.string.action_cancel)) { onCancel(item) }
    }
}

@Composable
private fun ActionIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = description, tint = MovaColors.TextPrimary)
    }
}

@Composable
private fun CompletedItem(item: DownloadEntity, onClick: () -> Unit) {
    MovaCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MediaThumbnail(uri = item.outputUri, kind = item.mediaKind, size = 48.dp, remoteUrl = item.thumbnailUrl)
            Spacer(Modifier.width(12.dp))
            TitleAndSubtitle(
                title = item.fileName,
                subtitle = listOfNotNull(item.totalBytes?.let(Formatters::bytes), item.outputLocation).joinToString(" · "),
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = stringResource(R.string.status_completed),
                tint = MovaColors.Success,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}
