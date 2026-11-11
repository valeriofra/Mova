package io.github.valeriofra.mova.desktop.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.valeriofra.mova.core.format.Formatters
import io.github.valeriofra.mova.desktop.data.DownloadTask
import io.github.valeriofra.mova.desktop.data.TaskStatus
import io.github.valeriofra.mova.desktop.resources.*
import io.github.valeriofra.mova.desktop.ui.components.AccentTextButton
import io.github.valeriofra.mova.desktop.ui.components.EmptyState
import io.github.valeriofra.mova.desktop.ui.components.MediaThumbnail
import io.github.valeriofra.mova.desktop.ui.components.MovaCard
import io.github.valeriofra.mova.desktop.ui.components.MovaProgressBar
import io.github.valeriofra.mova.desktop.ui.components.PrimaryButton
import io.github.valeriofra.mova.desktop.ui.components.ScreenTitle
import io.github.valeriofra.mova.desktop.ui.components.SectionHeader
import io.github.valeriofra.mova.desktop.ui.theme.MovaColors
import org.jetbrains.compose.resources.stringResource

/** Ações da fila (implementadas pelo DownloadManager). */
data class QueueActions(
    val pause: (Long) -> Unit = {},
    val resume: (Long) -> Unit = {},
    val cancel: (Long) -> Unit = {},
    val retry: (Long) -> Unit = {},
    val dismiss: (Long) -> Unit = {},
)

@Composable
fun QueueScreen(tasks: List<DownloadTask>, actions: QueueActions, onOpenHome: () -> Unit, onOpenLibrary: () -> Unit) {
    val running = tasks.filter { it.status == TaskStatus.RUNNING }
    val queued = tasks.filter { it.status == TaskStatus.QUEUED }.sortedWith(compareBy({ it.createdAt }, { it.id }))
    val paused = tasks.filter { it.status == TaskStatus.PAUSED }
    val failed = tasks.filter { it.status == TaskStatus.FAILED }.sortedByDescending { it.updatedAt }
    val recent = tasks.filter { it.status == TaskStatus.COMPLETED && !it.fileMissing }.sortedByDescending { it.completedAt }.take(5)
    var confirmCancel by remember { mutableStateOf<DownloadTask?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp)) {
        ScreenTitle(stringResource(Res.string.queue_title))
        if (running.isEmpty() && queued.isEmpty() && paused.isEmpty() && failed.isEmpty() && recent.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Download,
                title = stringResource(Res.string.queue_empty_title),
                message = stringResource(Res.string.queue_empty_message),
                action = { PrimaryButton(stringResource(Res.string.queue_empty_action), onOpenHome) },
            )
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            section(Res.string.queue_section_running, running) { QueueRow(it, actions) { confirmCancel = it } }
            section(Res.string.queue_section_queued, queued) { QueueRow(it, actions) { confirmCancel = it } }
            section(Res.string.queue_section_paused, paused) { QueueRow(it, actions) { confirmCancel = it } }
            section(Res.string.queue_section_failed, failed) { QueueRow(it, actions) { confirmCancel = it } }
            if (recent.isNotEmpty()) {
                item {
                    SectionHeader(stringResource(Res.string.queue_section_completed), Modifier.padding(top = 12.dp)) {
                        AccentTextButton(stringResource(Res.string.home_see_library), onOpenLibrary)
                    }
                }
                items(recent, key = { it.id }) { CompletedRow(it) }
            }
        }
    }

    confirmCancel?.let { task ->
        AlertDialog(
            onDismissRequest = { confirmCancel = null },
            title = { Text(stringResource(Res.string.queue_cancel_title)) },
            text = { Text(stringResource(Res.string.queue_cancel_message, task.title)) },
            confirmButton = {
                AccentTextButton(stringResource(Res.string.queue_cancel_confirm), {
                    actions.cancel(task.id)
                    confirmCancel = null
                })
            },
            dismissButton = { AccentTextButton(stringResource(Res.string.action_keep), { confirmCancel = null }) },
            containerColor = MovaColors.SurfaceHigh,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: org.jetbrains.compose.resources.StringResource,
    tasks: List<DownloadTask>,
    row: @Composable (DownloadTask) -> Unit,
) {
    if (tasks.isEmpty()) return
    item(key = "header-$title") { SectionHeader("${stringResource(title)} (${tasks.size})", Modifier.padding(top = 8.dp)) }
    items(tasks, key = { it.id }) { row(it) }
}

@Composable
private fun QueueRow(task: DownloadTask, actions: QueueActions, onCancel: (DownloadTask) -> Unit) {
    MovaCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MediaThumbnail(task.thumbnailUrl, task.mediaKind)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.titleSmall, color = MovaColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle(task), style = MaterialTheme.typography.bodySmall, color = if (task.status == TaskStatus.FAILED) MovaColors.Error else MovaColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                when (task.status) {
                    TaskStatus.RUNNING -> {
                        task.progress?.let {
                            Text("${(it * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = MovaColors.Accent)
                            Spacer(Modifier.width(4.dp))
                        }
                        ActionIcon(Icons.Rounded.Pause, stringResource(Res.string.action_pause)) { actions.pause(task.id) }
                    }
                    TaskStatus.QUEUED -> ActionIcon(Icons.Rounded.Pause, stringResource(Res.string.action_pause)) { actions.pause(task.id) }
                    TaskStatus.PAUSED -> ActionIcon(Icons.Rounded.PlayArrow, stringResource(Res.string.action_resume)) { actions.resume(task.id) }
                    TaskStatus.FAILED -> ActionIcon(Icons.Rounded.Refresh, stringResource(Res.string.action_retry)) { actions.retry(task.id) }
                    else -> Unit
                }
                if (task.status == TaskStatus.FAILED) {
                    ActionIcon(Icons.Rounded.Close, stringResource(Res.string.action_remove)) { actions.dismiss(task.id) }
                } else {
                    ActionIcon(Icons.Rounded.Close, stringResource(Res.string.action_cancel)) { onCancel(task) }
                }
            }
            if (task.status == TaskStatus.RUNNING || task.status == TaskStatus.PAUSED) {
                Spacer(Modifier.height(12.dp))
                MovaProgressBar(task.progress, color = if (task.status == TaskStatus.PAUSED) MovaColors.TextTertiary else MovaColors.BlueBright)
            }
        }
    }
}

@Composable
private fun subtitle(task: DownloadTask): String {
    val total = task.totalBytes
    return when (task.status) {
        TaskStatus.RUNNING -> listOfNotNull(
            if (total != null && task.downloadedBytes > 0) {
                stringResource(Res.string.queue_progress_of, Formatters.bytes(task.downloadedBytes), Formatters.bytes(total))
            } else {
                stringResource(Res.string.status_running)
            },
            task.bytesPerSecond.takeIf { it > 0 }?.let { Formatters.speed(it) },
            Formatters.remaining(task.downloadedBytes, total, task.bytesPerSecond)?.let { stringResource(Res.string.queue_remaining, it) },
        ).joinToString(" · ")
        TaskStatus.FAILED -> listOfNotNull(stringResource(errorLabel(task.error)), task.errorMessage?.take(160)).joinToString(" — ")
        TaskStatus.PAUSED -> listOfNotNull(
            stringResource(Res.string.status_paused),
            total?.let { stringResource(Res.string.queue_progress_of, Formatters.bytes(task.downloadedBytes), Formatters.bytes(it)) },
        ).joinToString(" · ")
        else -> listOfNotNull(stringResource(statusLabel(task.status)), total?.let { "≈ ${Formatters.bytes(it)}" }, platformName(task.platform)).joinToString(" · ")
    }
}

@Composable
private fun CompletedRow(task: DownloadTask) {
    MovaCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            MediaThumbnail(task.thumbnailUrl, task.mediaKind)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(task.fileName, style = MaterialTheme.typography.titleSmall, color = MovaColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(task.totalBytes?.let(Formatters::bytes), task.outputPath?.let { java.io.File(it).parentFile?.path }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MovaColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Rounded.CheckCircle, contentDescription = stringResource(Res.string.status_completed), tint = MovaColors.Success)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionIcon(icon: ImageVector, label: String, tint: androidx.compose.ui.graphics.Color = MovaColors.TextSecondary, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick) { Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp)) }
    }
}
