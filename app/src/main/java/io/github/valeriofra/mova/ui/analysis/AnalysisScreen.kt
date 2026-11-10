package io.github.valeriofra.mova.ui.analysis

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Stream
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.valeriofra.mova.R
import io.github.valeriofra.mova.core.analysis.AnalysisReport
import io.github.valeriofra.mova.core.analysis.DownloadOption
import io.github.valeriofra.mova.core.analysis.DownloadOptions
import io.github.valeriofra.mova.core.analysis.LinkAnalysis
import io.github.valeriofra.mova.core.analysis.MediaItem
import io.github.valeriofra.mova.core.format.Formatters
import io.github.valeriofra.mova.core.link.RecognizedLink
import io.github.valeriofra.mova.core.model.ContentKind
import io.github.valeriofra.mova.core.model.Platform
import io.github.valeriofra.mova.ui.components.MediaKindIcon
import io.github.valeriofra.mova.ui.components.MovaCard
import io.github.valeriofra.mova.ui.components.MovaTopBar
import io.github.valeriofra.mova.ui.components.PrimaryButton
import io.github.valeriofra.mova.ui.components.SecondaryButton
import io.github.valeriofra.mova.ui.components.StateMessage
import io.github.valeriofra.mova.ui.components.StatusPill
import io.github.valeriofra.mova.ui.components.TitleAndSubtitle
import io.github.valeriofra.mova.ui.components.icon
import io.github.valeriofra.mova.ui.components.openExternal
import io.github.valeriofra.mova.ui.labelRes
import io.github.valeriofra.mova.ui.theme.MovaColors

@Composable
fun AnalysisScreen(
    viewModel: AnalysisViewModel,
    onBack: () -> Unit,
    onSelectItems: () -> Unit,
    onEnqueued: (Int) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notificationsAsked by viewModel.notificationsAsked.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.enqueued.collect { count -> onEnqueued(count) }
    }

    val requestNotifications = rememberNotificationPermissionRequest(
        alreadyAsked = notificationsAsked,
        onAsked = viewModel::markNotificationsAsked,
    )

    AnalysisContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::analyze,
        onDownload = { items -> requestNotifications { viewModel.enqueue(items) } },
        onSelectItems = onSelectItems,
    )
}

/**
 * Pede a permissão de notificação (Android 13+) uma única vez, no primeiro
 * download, e depois executa a ação — independentemente da resposta.
 */
@Composable
fun rememberNotificationPermissionRequest(
    alreadyAsked: Boolean,
    onAsked: () -> Unit,
): (action: () -> Unit) -> Unit {
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onAsked()
        pending?.invoke()
        pending = null
    }
    return { action ->
        if (Build.VERSION.SDK_INT >= 33 && !alreadyAsked) {
            pending = action
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisContent(
    state: AnalysisUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onDownload: (List<MediaItem>) -> Unit,
    onSelectItems: () -> Unit,
) {
    var showOptions by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        MovaTopBar(title = stringResource(R.string.analysis_title), onBack = onBack)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when (state) {
                AnalysisUiState.Loading -> AnalyzingIndicator(Modifier.align(Alignment.Center))
                is AnalysisUiState.Failed -> ResultLayout(
                    message = {
                        StateMessage(
                            icon = Icons.Rounded.CloudOff,
                            tint = MovaColors.Warning,
                            title = stringResource(R.string.analysis_error_title),
                            message = stringResource(R.string.analysis_error_message),
                        )
                    },
                    primary = { PrimaryButton(stringResource(R.string.action_retry), onRetry, icon = Icons.Rounded.Refresh) },
                    secondary = { SecondaryButton(stringResource(R.string.analysis_other_link), onBack) },
                )
                is AnalysisUiState.Done -> ReportContent(
                    report = state.report,
                    onBack = onBack,
                    onRetry = onRetry,
                    onOpenOptions = { showOptions = true },
                )
            }
        }
    }

    val items = (state as? AnalysisUiState.Done)?.report?.downloadableItems.orEmpty()
    if (showOptions && items.isNotEmpty()) {
        DownloadOptionsSheet(
            items = items,
            onDismiss = { showOptions = false },
            onConfirm = { option ->
                showOptions = false
                when (option) {
                    is DownloadOption.SingleFile -> onDownload(listOf(option.item))
                    is DownloadOption.AllItems -> onDownload(option.items)
                    is DownloadOption.SelectItems -> onSelectItems()
                }
            },
        )
    }
}

/** Indicador de análise: anel girando em volta do símbolo de link. */
@Composable
private fun AnalyzingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "analysis")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
            Canvas(
                Modifier
                    .size(120.dp)
                    .rotate(angle),
            ) {
                val stroke = 6.dp.toPx()
                drawArc(
                    color = MovaColors.SurfaceHighest,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(stroke),
                )
                drawArc(
                    brush = Brush.sweepGradient(listOf(MovaColors.Cyan.copy(alpha = 0f), MovaColors.Cyan, MovaColors.BlueBright)),
                    startAngle = 0f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Icon(Icons.Rounded.Link, contentDescription = null, tint = MovaColors.TextPrimary, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(R.string.analysis_loading_title),
            style = MaterialTheme.typography.titleLarge,
            color = MovaColors.TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.analysis_loading_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MovaColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/** Estrutura comum: conteúdo rolável + ações fixas no rodapé. */
@Composable
private fun ResultLayout(
    message: @Composable () -> Unit,
    primary: (@Composable () -> Unit)?,
    secondary: (@Composable () -> Unit)?,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            message()
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            primary?.invoke()
            secondary?.invoke()
        }
    }
}

@Composable
private fun ReportContent(
    report: AnalysisReport,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenOptions: () -> Unit,
) {
    val context = LocalContext.current
    val anotherLink: @Composable () -> Unit = {
        SecondaryButton(stringResource(R.string.analysis_other_link), onBack)
    }
    when {
        report.isEmpty -> ResultLayout(
            message = {
                StateMessage(
                    icon = Icons.Rounded.LinkOff,
                    tint = MovaColors.Warning,
                    title = stringResource(R.string.analysis_no_link_title),
                    message = stringResource(R.string.analysis_no_link_message),
                )
            },
            primary = null,
            secondary = anotherLink,
        )

        report.hasMultipleLinks -> ResultLayout(
            message = { MultipleResults(report) },
            primary = if (report.downloadableItems.isNotEmpty()) {
                { PrimaryButton(stringResource(R.string.action_download), onOpenOptions, icon = Icons.Rounded.Download) }
            } else {
                null
            },
            secondary = anotherLink,
        )

        else -> when (val result = report.results.single()) {
            is LinkAnalysis.Ready -> ResultLayout(
                message = { ReadyResult(result) },
                primary = { PrimaryButton(stringResource(R.string.action_download), onOpenOptions, icon = Icons.Rounded.Download) },
                secondary = anotherLink,
            )

            is LinkAnalysis.RecognizedWithoutDownload -> ResultLayout(
                message = { RecognizedWithoutDownload(result.link) },
                primary = {
                    PrimaryButton(
                        text = stringResource(R.string.analysis_open_in, result.link.platform.displayName),
                        onClick = { context.openExternal(result.link.url) },
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    )
                },
                secondary = anotherLink,
            )

            is LinkAnalysis.UnrecognizedPage -> ResultLayout(
                message = {
                    StateMessage(
                        icon = Icons.Rounded.Public,
                        tint = MovaColors.Accent,
                        title = stringResource(R.string.analysis_page_title),
                        message = stringResource(R.string.analysis_page_message, result.link.host),
                    )
                },
                primary = {
                    PrimaryButton(
                        stringResource(R.string.analysis_open_browser),
                        { context.openExternal(result.link.url) },
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    )
                },
                secondary = anotherLink,
            )

            is LinkAnalysis.UnsupportedFormat -> ResultLayout(
                message = {
                    StateMessage(
                        icon = Icons.Rounded.Stream,
                        tint = MovaColors.Warning,
                        title = stringResource(R.string.analysis_streaming_title),
                        message = stringResource(R.string.analysis_streaming_message),
                    )
                },
                primary = null,
                secondary = anotherLink,
            )

            is LinkAnalysis.Unavailable -> ResultLayout(
                message = {
                    StateMessage(
                        icon = Icons.Rounded.SearchOff,
                        tint = MovaColors.Warning,
                        title = stringResource(R.string.analysis_unavailable_title),
                        message = stringResource(R.string.analysis_unavailable_message, result.httpCode),
                    )
                },
                primary = null,
                secondary = anotherLink,
            )

            is LinkAnalysis.AccessDenied -> ResultLayout(
                message = {
                    StateMessage(
                        icon = Icons.Rounded.Block,
                        tint = MovaColors.Error,
                        title = stringResource(R.string.analysis_denied_title),
                        message = stringResource(R.string.analysis_denied_message, result.httpCode),
                    )
                },
                primary = {
                    PrimaryButton(
                        stringResource(R.string.analysis_open_browser),
                        { context.openExternal(result.link.url) },
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    )
                },
                secondary = anotherLink,
            )

            is LinkAnalysis.TemporaryError -> ResultLayout(
                message = {
                    StateMessage(
                        icon = Icons.Rounded.CloudOff,
                        tint = MovaColors.Warning,
                        title = stringResource(R.string.analysis_error_title),
                        message = stringResource(R.string.analysis_error_message),
                    )
                },
                primary = { PrimaryButton(stringResource(R.string.action_retry), onRetry, icon = Icons.Rounded.Refresh) },
                secondary = anotherLink,
            )
        }
    }
}

@Composable
private fun ReadyResult(result: LinkAnalysis.Ready) {
    val item = result.items.single()
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        StatusPill(
            text = stringResource(R.string.analysis_found),
            color = MovaColors.Success,
            icon = Icons.Rounded.CheckCircle,
        )
        Spacer(Modifier.height(20.dp))
        MediaKindIcon(item.mediaKind, size = 96.dp)
        Spacer(Modifier.height(20.dp))
        Text(
            item.fileName,
            style = MaterialTheme.typography.titleLarge,
            color = MovaColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            itemDetails(item),
            style = MaterialTheme.typography.bodyMedium,
            color = MovaColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.analysis_origin, result.link.host),
            style = MaterialTheme.typography.bodySmall,
            color = MovaColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        if (!item.supportsResume) {
            Spacer(Modifier.height(20.dp))
            NoteRow(stringResource(R.string.analysis_no_resume))
        }
    }
}

@Composable
private fun RecognizedWithoutDownload(link: RecognizedLink) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        PlatformMark(link.platform)
        Spacer(Modifier.height(16.dp))
        StatusPill(
            text = stringResource(R.string.analysis_recognized),
            color = MovaColors.Success,
            icon = Icons.Rounded.CheckCircle,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "${link.platform.displayName} · ${stringResource(link.kind.labelRes())}",
            style = MaterialTheme.typography.titleLarge,
            color = MovaColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.analysis_no_download_message, link.platform.displayName),
            style = MaterialTheme.typography.bodyMedium,
            color = MovaColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        val extraNote = when {
            link.kind == ContentKind.STORY || link.kind == ContentKind.STORY_HIGHLIGHT -> R.string.analysis_note_story
            link.kind == ContentKind.SHORT_LINK -> R.string.analysis_note_short_link
            link.kind.mayContainMultipleItems -> R.string.analysis_note_multiple
            else -> null
        }
        if (extraNote != null) {
            Spacer(Modifier.height(20.dp))
            NoteRow(stringResource(extraNote))
        }
    }
}

/** Marca neutra da plataforma (inicial do nome) — sem usar logotipos de terceiros. */
@Composable
private fun PlatformMark(platform: Platform) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(88.dp)
            .clip(CircleShape)
            .background(MovaColors.BrandGradient),
    ) {
        Text(
            platform.displayName.take(1),
            style = MaterialTheme.typography.headlineMedium,
            color = MovaColors.Background,
        )
    }
}

@Composable
private fun NoteRow(text: String) {
    MovaCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Info, contentDescription = null, tint = MovaColors.Accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MovaColors.TextSecondary)
        }
    }
}

@Composable
private fun MultipleResults(report: AnalysisReport) {
    val downloadable = report.downloadableItems.size
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            pluralStringResource(R.plurals.analysis_links_analyzed, report.results.size, report.results.size),
            style = MaterialTheme.typography.titleLarge,
            color = MovaColors.TextPrimary,
        )
        Text(
            pluralStringResource(R.plurals.analysis_downloadable_count, downloadable, downloadable),
            style = MaterialTheme.typography.bodyMedium,
            color = MovaColors.TextSecondary,
        )
        Spacer(Modifier.height(4.dp))
        report.results.forEach { result -> ResultRow(result) }
    }
}

@Composable
private fun ResultRow(result: LinkAnalysis) {
    val (title, statusText, color) = when (result) {
        is LinkAnalysis.Ready -> Triple(result.items.first().fileName, stringResource(R.string.result_ready), MovaColors.Success)
        is LinkAnalysis.RecognizedWithoutDownload -> Triple(
            "${result.link.platform.displayName} · ${stringResource(result.link.kind.labelRes())}",
            stringResource(R.string.result_no_download),
            MovaColors.TextSecondary,
        )
        is LinkAnalysis.UnrecognizedPage -> Triple(result.link.host, stringResource(R.string.result_page), MovaColors.TextSecondary)
        is LinkAnalysis.UnsupportedFormat -> Triple(result.link.host, stringResource(R.string.result_streaming), MovaColors.Warning)
        is LinkAnalysis.Unavailable -> Triple(result.link.host, stringResource(R.string.result_unavailable), MovaColors.Warning)
        is LinkAnalysis.AccessDenied -> Triple(result.link.host, stringResource(R.string.result_denied), MovaColors.Error)
        is LinkAnalysis.TemporaryError -> Triple(result.link.host, stringResource(R.string.result_error), MovaColors.Warning)
    }
    MovaCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val kindIcon = (result as? LinkAnalysis.Ready)?.items?.first()?.mediaKind
            if (kindIcon != null) {
                MediaKindIcon(kindIcon, size = 40.dp)
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MovaColors.SurfaceHighest),
                ) {
                    Icon(Icons.Rounded.Link, contentDescription = null, tint = MovaColors.TextSecondary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            TitleAndSubtitle(title = title, subtitle = null, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            StatusPill(text = statusText, color = color)
        }
    }
}

@Composable
private fun itemDetails(item: MediaItem): String {
    val parts = mutableListOf(stringResource(item.mediaKind.labelRes()))
    DownloadOption.SingleFile(item).formatLabel?.let(parts::add)
    item.sizeBytes?.let { parts += Formatters.bytes(it) }
    return parts.joinToString(" · ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadOptionsSheet(
    items: List<MediaItem>,
    onDismiss: () -> Unit,
    onConfirm: (DownloadOption) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MovaColors.Surface,
    ) {
        DownloadOptionsContent(items = items, onConfirm = onConfirm)
    }
}

/** Opções contextuais: somente o que a análise encontrou. */
@Composable
fun DownloadOptionsContent(
    items: List<MediaItem>,
    onConfirm: (DownloadOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = remember(items) { DownloadOptions.forItems(items) }
    var selectedKey by rememberSaveable { mutableStateOf(options.firstOrNull()?.key) }
    val selected = options.firstOrNull { it.key == selectedKey } ?: options.first()

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(
            stringResource(R.string.options_title),
            style = MaterialTheme.typography.titleLarge,
            color = MovaColors.TextPrimary,
        )
        Spacer(Modifier.height(16.dp))
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            options.forEach { option ->
                OptionRow(option, selected = option.key == selected.key, onSelect = { selectedKey = option.key })
            }
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = stringResource(
                if (selected is DownloadOption.SelectItems) R.string.action_continue else R.string.options_download_now,
            ),
            onClick = { onConfirm(selected) },
            icon = Icons.Rounded.Download,
        )
    }
}

@Composable
private fun OptionRow(option: DownloadOption, selected: Boolean, onSelect: () -> Unit) {
    val (icon, title, subtitle) = when (option) {
        is DownloadOption.SingleFile -> {
            val kind = stringResource(option.item.mediaKind.labelRes())
            val title = option.formatLabel?.let { "$kind ($it)" } ?: kind
            val size = option.item.sizeBytes?.let { " · ${Formatters.bytes(it)}" }.orEmpty()
            Triple(option.item.mediaKind.icon(), title, stringResource(R.string.options_original_quality) + size)
        }
        is DownloadOption.AllItems -> Triple(
            Icons.Rounded.Layers,
            stringResource(R.string.options_all_items),
            pluralStringResource(R.plurals.files_count, option.items.size, option.items.size) +
                (option.totalBytes?.let { " · ${Formatters.bytes(it)}" }.orEmpty()),
        )
        is DownloadOption.SelectItems -> Triple(
            Icons.Rounded.Checklist,
            stringResource(R.string.options_select_items),
            stringResource(R.string.options_select_items_hint),
        )
    }
    MovaCard(
        highlighted = selected,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (selected) MovaColors.Accent else MovaColors.TextSecondary)
            Spacer(Modifier.width(14.dp))
            TitleAndSubtitle(title = title, subtitle = subtitle, modifier = Modifier.weight(1f))
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(selectedColor = MovaColors.Accent, unselectedColor = MovaColors.TextSecondary),
            )
        }
    }
}
