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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Stream
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.valeriofra.mova.R
import io.github.valeriofra.mova.core.analysis.AnalysisReport
import io.github.valeriofra.mova.core.analysis.DownloadOption
import io.github.valeriofra.mova.core.analysis.DownloadOptions
import io.github.valeriofra.mova.core.analysis.FormatChoice
import io.github.valeriofra.mova.core.analysis.LinkAnalysis
import io.github.valeriofra.mova.core.analysis.MediaItem
import io.github.valeriofra.mova.core.analysis.RestrictionReason
import io.github.valeriofra.mova.core.format.Formatters
import io.github.valeriofra.mova.core.format.duration
import io.github.valeriofra.mova.core.link.RecognizedLink
import io.github.valeriofra.mova.core.model.ContentKind
import io.github.valeriofra.mova.core.model.MediaSource
import io.github.valeriofra.mova.core.model.Platform
import io.github.valeriofra.mova.ui.components.MediaKindIcon
import io.github.valeriofra.mova.ui.components.MovaCard
import io.github.valeriofra.mova.ui.components.MovaTopBar
import io.github.valeriofra.mova.ui.components.PrimaryButton
import io.github.valeriofra.mova.ui.components.RemoteImage
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
    val engineBusy by viewModel.engineBusy.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.enqueued.collect { count -> onEnqueued(count) }
    }

    val requestNotifications = rememberNotificationPermissionRequest(
        alreadyAsked = notificationsAsked,
        onAsked = viewModel::markNotificationsAsked,
    )

    AnalysisContent(
        state = state,
        engineBusy = engineBusy,
        onBack = onBack,
        onRetry = viewModel::analyze,
        onDownload = { items, format -> requestNotifications { viewModel.enqueue(items, format) } },
        onSelectItems = { format ->
            viewModel.chooseFormat(format)
            onSelectItems()
        },
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

@Composable
fun AnalysisContent(
    state: AnalysisUiState,
    engineBusy: EngineBusy?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onDownload: (List<MediaItem>, FormatChoice) -> Unit,
    onSelectItems: (FormatChoice) -> Unit,
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
                AnalysisUiState.Loading -> AnalyzingIndicator(engineBusy, Modifier.align(Alignment.Center))
                is AnalysisUiState.Failed -> ResultLayout(
                    message = {
                        StateMessage(
                            icon = Icons.Rounded.CloudOff,
                            tint = MovaColors.Warning,
                            title = stringResource(R.string.analysis_error_title),
                            message = stringResource(R.string.analysis_error_message),
                            details = state.message,
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
            onConfirm = { option, format ->
                showOptions = false
                when (option) {
                    is DownloadOption.SingleFile -> onDownload(listOf(option.item), format)
                    is DownloadOption.AllItems -> onDownload(option.items, format)
                    is DownloadOption.SelectItems -> onSelectItems(format)
                }
            },
        )
    }
}

/** Indicador de análise: anel girando em volta do símbolo de link. */
@Composable
private fun AnalyzingIndicator(engineBusy: EngineBusy?, modifier: Modifier = Modifier) {
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
            stringResource(
                when (engineBusy) {
                    EngineBusy.PREPARING -> R.string.analysis_loading_preparing
                    EngineBusy.UPDATING -> R.string.analysis_loading_updating
                    null -> R.string.analysis_loading_message
                },
            ),
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
    val openOriginal: @Composable (RecognizedLink) -> Unit = { link ->
        PrimaryButton(
            text = if (link.platform.isKnownPlatform) {
                stringResource(R.string.analysis_open_in, link.platform.displayName)
            } else {
                stringResource(R.string.analysis_open_browser)
            },
            onClick = { context.openExternal(link.url) },
            icon = Icons.AutoMirrored.Rounded.OpenInNew,
        )
    }
    val downloadButton: @Composable () -> Unit = {
        PrimaryButton(stringResource(R.string.action_download), onOpenOptions, icon = Icons.Rounded.Download)
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
            primary = if (report.downloadableItems.isNotEmpty()) downloadButton else null,
            secondary = anotherLink,
        )

        else -> when (val result = report.results.single()) {
            is LinkAnalysis.Ready -> ResultLayout(
                message = {
                    if (result.items.size == 1) ReadyResult(result) else CollectionResult(result)
                },
                primary = downloadButton,
                secondary = anotherLink,
            )

            is LinkAnalysis.RecognizedWithoutDownload -> ResultLayout(
                message = { RecognizedWithoutDownload(result.link) },
                primary = { openOriginal(result.link) },
                secondary = anotherLink,
            )

            is LinkAnalysis.NotSupported -> ResultLayout(
                message = {
                    StateMessage(
                        icon = Icons.Rounded.SearchOff,
                        tint = MovaColors.Warning,
                        title = stringResource(R.string.analysis_not_supported_title),
                        message = stringResource(R.string.analysis_not_supported_message),
                        badge = result.link.platform.takeIf { it.isKnownPlatform }?.displayName,
                        details = result.message,
                    )
                },
                primary = { openOriginal(result.link) },
                secondary = anotherLink,
            )

            is LinkAnalysis.Restricted -> ResultLayout(
                message = { RestrictedResult(result) },
                primary = { openOriginal(result.link) },
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
                primary = { openOriginal(result.link) },
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
                        message = result.httpCode?.let { code ->
                            stringResource(R.string.analysis_unavailable_message, code)
                        } ?: run {
                            stringResource(R.string.analysis_unavailable_message_plain)
                        },
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
                primary = { openOriginal(result.link) },
                secondary = anotherLink,
            )

            is LinkAnalysis.TemporaryError -> ResultLayout(
                message = {
                    StateMessage(
                        icon = Icons.Rounded.CloudOff,
                        tint = MovaColors.Warning,
                        title = stringResource(R.string.analysis_error_title),
                        message = stringResource(R.string.analysis_error_message),
                        details = result.message,
                    )
                },
                primary = { PrimaryButton(stringResource(R.string.action_retry), onRetry, icon = Icons.Rounded.Refresh) },
                secondary = anotherLink,
            )
        }
    }
}

/** Miniatura grande (16:9) com duração, ou o ícone do tipo de mídia. */
@Composable
private fun MediaPreview(item: MediaItem) {
    if (item.thumbnailUrl != null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(MovaColors.SurfaceHigh),
        ) {
            RemoteImage(
                url = item.thumbnailUrl,
                modifier = Modifier.fillMaxSize(),
                fallback = { MediaKindIcon(item.mediaKind, Modifier.align(Alignment.Center), size = 72.dp) },
            )
            item.durationSeconds?.let { seconds ->
                Text(
                    Formatters.duration(seconds),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xB3000000))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    } else {
        MediaKindIcon(item.mediaKind, size = 96.dp)
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
        MediaPreview(item)
        Spacer(Modifier.height(20.dp))
        Text(
            if (item.source == MediaSource.EXTRACTOR) item.title else item.fileName,
            style = MaterialTheme.typography.titleLarge,
            color = MovaColors.TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            itemDetails(item),
            style = MaterialTheme.typography.bodyMedium,
            color = MovaColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        if (item.source == MediaSource.DIRECT) {
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
}

/** Playlist, coleção ou publicação com vários itens. */
@Composable
private fun CollectionResult(result: LinkAnalysis.Ready) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StatusPill(
            text = stringResource(R.string.analysis_found),
            color = MovaColors.Success,
            icon = Icons.Rounded.CheckCircle,
        )
        Text(
            result.collectionTitle ?: stringResource(R.string.kind_collection),
            style = MaterialTheme.typography.titleLarge,
            color = MovaColors.TextPrimary,
        )
        Text(
            "${result.link.platform.displayName} · " +
                pluralStringResource(R.plurals.items_count, result.items.size, result.items.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MovaColors.TextSecondary,
        )
        Spacer(Modifier.height(4.dp))
        result.items.take(PREVIEW_LIMIT).forEach { item -> ItemRow(item) }
        if (result.items.size > PREVIEW_LIMIT) {
            Text(
                stringResource(R.string.analysis_more_items, result.items.size - PREVIEW_LIMIT),
                style = MaterialTheme.typography.bodySmall,
                color = MovaColors.TextSecondary,
            )
        }
    }
}

private const val PREVIEW_LIMIT = 5

@Composable
private fun ItemRow(item: MediaItem) {
    MovaCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 72.dp, height = 44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MovaColors.SurfaceHigh),
            ) {
                RemoteImage(
                    url = item.thumbnailUrl,
                    modifier = Modifier.fillMaxSize(),
                    fallback = { MediaKindIcon(item.mediaKind, Modifier.align(Alignment.Center), size = 32.dp) },
                )
            }
            Spacer(Modifier.width(12.dp))
            TitleAndSubtitle(
                title = item.title,
                subtitle = item.durationSeconds?.let { Formatters.duration(it) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RestrictedResult(result: LinkAnalysis.Restricted) {
    val (icon, title, message) = when (result.reason) {
        RestrictionReason.PRIVATE -> Triple(
            Icons.Rounded.Lock,
            R.string.analysis_private_title,
            R.string.analysis_private_message,
        )
        RestrictionReason.LOGIN_REQUIRED -> Triple(
            Icons.Rounded.Lock,
            R.string.analysis_login_title,
            R.string.analysis_login_message,
        )
        RestrictionReason.DRM -> Triple(
            Icons.Rounded.Block,
            R.string.analysis_drm_title,
            R.string.analysis_drm_message,
        )
        RestrictionReason.GEO_BLOCKED -> Triple(
            Icons.Rounded.Public,
            R.string.analysis_geo_title,
            R.string.analysis_geo_message,
        )
    }
    StateMessage(
        icon = icon,
        tint = MovaColors.Warning,
        title = stringResource(title),
        message = stringResource(message),
        badge = "${result.link.platform.displayName} · ${stringResource(result.link.kind.labelRes())}",
        details = result.message,
    )
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
        is LinkAnalysis.Ready -> Triple(
            result.collectionTitle ?: result.items.first().let { if (it.source == MediaSource.EXTRACTOR) it.title else it.fileName },
            if (result.items.size > 1) {
                pluralStringResource(R.plurals.items_count, result.items.size, result.items.size)
            } else {
                stringResource(R.string.result_ready)
            },
            MovaColors.Success,
        )
        is LinkAnalysis.RecognizedWithoutDownload -> Triple(
            "${result.link.platform.displayName} · ${stringResource(result.link.kind.labelRes())}",
            stringResource(R.string.result_no_download),
            MovaColors.TextSecondary,
        )
        is LinkAnalysis.NotSupported -> Triple(result.link.host, stringResource(R.string.result_not_supported), MovaColors.Warning)
        is LinkAnalysis.Restricted -> Triple(
            "${result.link.platform.displayName} · ${stringResource(result.link.kind.labelRes())}",
            stringResource(R.string.result_restricted),
            MovaColors.Warning,
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
    val parts = mutableListOf<String>()
    if (item.source == MediaSource.EXTRACTOR) {
        parts += item.platform.displayName
        item.durationSeconds?.let { parts += Formatters.duration(it) }
        item.uploader?.let { parts += it }
    } else {
        parts += stringResource(item.mediaKind.labelRes())
        DownloadOption.SingleFile(item).formatLabel?.let(parts::add)
        item.sizeBytes?.let { parts += Formatters.bytes(it) }
    }
    return parts.joinToString(" · ")
}

private val FormatChoiceSaver = Saver<FormatChoice, String>(save = { it.encode() }, restore = { FormatChoice.decode(it) })

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadOptionsSheet(
    items: List<MediaItem>,
    onDismiss: () -> Unit,
    onConfirm: (DownloadOption, FormatChoice) -> Unit,
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
    onConfirm: (DownloadOption, FormatChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = remember(items) { DownloadOptions.forItems(items) }
    val formats = remember(items) { DownloadOptions.formatsFor(items) }
    var selectedKey by rememberSaveable { mutableStateOf(options.firstOrNull()?.key) }
    var format by rememberSaveable(stateSaver = FormatChoiceSaver) { mutableStateOf(DownloadOptions.defaultFormat(items)) }
    val selected = options.firstOrNull { it.key == selectedKey } ?: options.first()
    val usesFormats = formats.any { it != FormatChoice.Original }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(
            stringResource(R.string.options_title),
            style = MaterialTheme.typography.titleLarge,
            color = MovaColors.TextPrimary,
        )
        Spacer(Modifier.height(16.dp))

        // O que baixar: só aparece para links diretos ou quando há vários itens.
        if (!usesFormats || options.size > 1) {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                options.forEach { option ->
                    OptionRow(option, selected = option.key == selected.key, onSelect = { selectedKey = option.key })
                }
            }
            if (usesFormats) Spacer(Modifier.height(20.dp))
        }

        // Formato: vídeo (até 1080p) ou somente áudio.
        if (usesFormats) {
            Text(
                stringResource(R.string.options_format),
                style = MaterialTheme.typography.titleSmall,
                color = MovaColors.TextPrimary,
            )
            Spacer(Modifier.height(10.dp))
            FormatSection(items, formats, format, onSelect = { format = it })
        }

        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = stringResource(
                if (selected is DownloadOption.SelectItems) R.string.action_continue else R.string.options_download_now,
            ),
            onClick = { onConfirm(selected, format) },
            icon = Icons.Rounded.Download,
        )
    }
}

@Composable
private fun FormatSection(
    items: List<MediaItem>,
    formats: List<FormatChoice>,
    selected: FormatChoice,
    onSelect: (FormatChoice) -> Unit,
) {
    val videos = formats.filterIsInstance<FormatChoice.Video>()
    val silentVideos = formats.filterIsInstance<FormatChoice.VideoOnly>()
    val audios = formats.filterIsInstance<FormatChoice.Audio>()
    val single = items.singleOrNull()
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (videos.isNotEmpty()) {
            val current = selected as? FormatChoice.Video
            val size = single?.let { item -> current?.let { DownloadOptions.estimatedSize(item, it) } }
            FormatCard(
                icon = Icons.Rounded.Movie,
                title = stringResource(R.string.options_video),
                subtitle = listOfNotNull(
                    stringResource(R.string.options_video_hint),
                    size?.let { "≈ ${Formatters.bytes(it)}" },
                ).joinToString(" · "),
                selected = current != null,
                onSelect = { if (current == null) onSelect(videos.first()) },
                chips = videos.map { it to "${it.maxResolution}p" },
                selectedChip = current,
                onChip = onSelect,
            )
        }
        if (silentVideos.isNotEmpty()) {
            val current = selected as? FormatChoice.VideoOnly
            val size = single?.let { item -> current?.let { DownloadOptions.estimatedSize(item, it) } }
            FormatCard(
                icon = Icons.AutoMirrored.Rounded.VolumeOff,
                title = stringResource(R.string.options_video_only),
                subtitle = listOfNotNull(
                    stringResource(R.string.options_video_only_hint),
                    size?.let { "≈ ${Formatters.bytes(it)}" },
                ).joinToString(" · "),
                selected = current != null,
                onSelect = { if (current == null) onSelect(silentVideos.first()) },
                chips = silentVideos.map { it to "${it.maxResolution}p" },
                selectedChip = current,
                onChip = onSelect,
            )
        }
        if (audios.isNotEmpty()) {
            val current = selected as? FormatChoice.Audio
            val size = single?.let { item -> current?.let { DownloadOptions.estimatedSize(item, it) } }
            FormatCard(
                icon = Icons.Rounded.Audiotrack,
                title = stringResource(R.string.options_audio),
                subtitle = listOfNotNull(
                    stringResource(R.string.options_audio_hint),
                    size?.let { "≈ ${Formatters.bytes(it)}" },
                ).joinToString(" · "),
                selected = current != null,
                onSelect = { if (current == null) onSelect(audios.first()) },
                chips = audios.map { it to it.format.label },
                selectedChip = current,
                onChip = onSelect,
            )
        }
    }
}

@Composable
private fun FormatCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit,
    chips: List<Pair<FormatChoice, String>>,
    selectedChip: FormatChoice?,
    onChip: (FormatChoice) -> Unit,
) {
    MovaCard(
        highlighted = selected,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = if (selected) MovaColors.Accent else MovaColors.TextSecondary)
                Spacer(Modifier.width(14.dp))
                TitleAndSubtitle(title = title, subtitle = subtitle, modifier = Modifier.weight(1f))
                RadioButton(
                    selected = selected,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(selectedColor = MovaColors.Accent, unselectedColor = MovaColors.TextSecondary),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .padding(start = 38.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chips.forEach { (choice, label) ->
                    val isSelected = choice == selectedChip
                    FilterChip(
                        selected = isSelected,
                        onClick = { onChip(choice) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MovaColors.Surface,
                            labelColor = MovaColors.TextSecondary,
                            selectedContainerColor = MovaColors.Blue.copy(alpha = 0.25f),
                            selectedLabelColor = MovaColors.TextPrimary,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = MovaColors.OutlineStrong,
                            selectedBorderColor = MovaColors.BlueBright,
                        ),
                    )
                }
            }
        }
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
            pluralStringResource(R.plurals.items_count, option.items.size, option.items.size) +
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
