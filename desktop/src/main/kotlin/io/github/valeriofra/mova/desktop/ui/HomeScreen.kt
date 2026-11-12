package io.github.valeriofra.mova.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollbarAdapter
import io.github.valeriofra.mova.desktop.ui.components.SCROLLBAR_GUTTER
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.valeriofra.mova.core.analysis.AnalysisReport
import io.github.valeriofra.mova.core.analysis.DownloadOptions
import io.github.valeriofra.mova.core.analysis.FormatChoice
import io.github.valeriofra.mova.core.analysis.LinkAnalysis
import io.github.valeriofra.mova.core.analysis.LinkAnalyzer
import io.github.valeriofra.mova.core.analysis.MediaItem
import io.github.valeriofra.mova.core.analysis.RestrictionReason
import io.github.valeriofra.mova.core.format.Formatters
import io.github.valeriofra.mova.core.format.duration
import io.github.valeriofra.mova.core.link.LinkRecognizer
import io.github.valeriofra.mova.core.link.UrlExtractor
import io.github.valeriofra.mova.core.model.MediaSource
import io.github.valeriofra.mova.desktop.data.DownloadManager
import io.github.valeriofra.mova.desktop.engine.EngineState
import io.github.valeriofra.mova.desktop.resources.*
import io.github.valeriofra.mova.desktop.system.DesktopActions
import io.github.valeriofra.mova.desktop.ui.components.InfoBanner
import io.github.valeriofra.mova.desktop.ui.components.MediaThumbnail
import io.github.valeriofra.mova.desktop.ui.components.MovaCard
import io.github.valeriofra.mova.desktop.ui.components.MovaChip
import io.github.valeriofra.mova.desktop.ui.components.MovaLogo
import io.github.valeriofra.mova.desktop.ui.components.MovaProgressBar
import io.github.valeriofra.mova.desktop.ui.components.PrimaryButton
import io.github.valeriofra.mova.desktop.ui.components.SecondaryButton
import io.github.valeriofra.mova.desktop.ui.components.TitleAndSubtitle
import io.github.valeriofra.mova.desktop.ui.components.LocalScreenPadding
import io.github.valeriofra.mova.desktop.ui.theme.MovaColors
import java.awt.datatransfer.DataFlavor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Estado da tela Início: o link digitado e o resultado da análise. */
class HomeModel(
    private val analyzer: LinkAnalyzer,
    private val manager: DownloadManager,
    private val scope: CoroutineScope,
) {
    sealed interface Analysis {
        data object Idle : Analysis
        data object Loading : Analysis
        data class Done(val report: AnalysisReport, val selected: Set<String>, val format: FormatChoice) : Analysis {
            val items: List<MediaItem> get() = report.downloadableItems
            val chosen: List<MediaItem> get() = items.filter { it.key in selected }
        }
    }

    var input by mutableStateOf("")
    var analysis by mutableStateOf<Analysis>(Analysis.Idle)
        internal set
    private var job: Job? = null

    fun onInput(value: String) {
        input = value
        if (analysis !is Analysis.Idle) {
            job?.cancel()
            analysis = Analysis.Idle
        }
    }

    fun clear() = onInput("")

    fun pasteAndAnalyze() {
        DesktopActions.clipboardText()?.takeIf { it.isNotBlank() }?.let {
            input = it.trim()
            analyze()
        }
    }

    /** Recebe um link arrastado para a janela. */
    fun drop(text: String) {
        input = text.trim()
        analyze()
    }

    fun analyze() {
        val text = input.trim()
        if (text.isEmpty()) return
        job?.cancel()
        analysis = Analysis.Loading
        job = scope.launch {
            val report = try {
                analyzer.analyze(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AnalysisReport(text, emptyList())
            }
            val items = report.downloadableItems
            analysis = Analysis.Done(report, items.map { it.key }.toSet(), DownloadOptions.defaultFormat(items))
        }
    }

    fun toggle(key: String) = updateDone { it.copy(selected = if (key in it.selected) it.selected - key else it.selected + key) }
    fun selectAll(all: Boolean) = updateDone { it.copy(selected = if (all) it.items.map { item -> item.key }.toSet() else emptySet()) }
    fun chooseFormat(format: FormatChoice) = updateDone { it.copy(format = format) }

    /** Coloca os itens escolhidos na fila; devolve quantos entraram. */
    /** Coloca os itens escolhidos na fila, salvando em [saveDir] (null = pasta padrão). */
    fun enqueue(saveDir: java.io.File? = null): Int {
        val done = analysis as? Analysis.Done ?: return 0
        val count = manager.enqueue(done.chosen, done.format, saveDir)
        if (count > 0) {
            input = ""
            analysis = Analysis.Idle
        }
        return count
    }

    private fun updateDone(transform: (Analysis.Done) -> Analysis.Done) {
        (analysis as? Analysis.Done)?.let { analysis = transform(it) }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(model: HomeModel, engineState: EngineState, onDownload: () -> Unit) {
    val recognizer = remember { LinkRecognizer() }
    val analysis = model.analysis
    val dropTarget = remember(model) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val transferable = event.awtTransferable
                val text = runCatching {
                    if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) transferable.getTransferData(DataFlavor.stringFlavor) as? String else null
                }.getOrNull()
                if (text.isNullOrBlank() || UrlExtractor.extract(text).isEmpty()) return false
                model.drop(text)
                return true
            }
        }
    }
    BoxWithConstraints(
        Modifier.fillMaxSize().dragAndDropTarget(shouldStartDragAndDrop = { true }, target = dropTarget),
        contentAlignment = Alignment.TopCenter,
    ) {
        // Monitores baixos: menos espaço acima do campo.
        val short = maxHeight < 620.dp
        val pageScroll = rememberScrollState()
        Column(
            Modifier.fillMaxSize().verticalScroll(pageScroll).padding(horizontal = LocalScreenPadding.current, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val idle = analysis is HomeModel.Analysis.Idle
            Spacer(Modifier.height(if (idle && !short) 48.dp else 8.dp))
            MovaLogo(if (idle && !short) 96.dp else 56.dp)
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(Res.string.home_title),
                style = if (idle) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                color = MovaColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            if (idle) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(Res.string.home_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MovaColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 560.dp),
                )
            }
            Spacer(Modifier.height(28.dp))
            Column(Modifier.widthIn(max = CONTENT_WIDTH).fillMaxWidth()) {
                LinkField(model)
                val first = remember(model.input) { UrlExtractor.extract(model.input).firstOrNull()?.let(recognizer::recognize) }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 24.dp)) {
                    if (first != null) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MovaColors.Success, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${platformName(first.platform)} · ${stringResource(kindLabel(first.kind))}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MovaColors.TextSecondary,
                        )
                    } else if (idle) {
                        Text(stringResource(Res.string.home_drop_hint), style = MaterialTheme.typography.bodySmall, color = MovaColors.TextTertiary)
                    }
                }
                Spacer(Modifier.height(16.dp))
                when (analysis) {
                    HomeModel.Analysis.Idle -> Unit
                    HomeModel.Analysis.Loading -> AnalyzingCard(engineState)
                    is HomeModel.Analysis.Done -> ResultContent(analysis, model, onDownload)
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(pageScroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

private val CONTENT_WIDTH = 760.dp

@Composable
private fun LinkField(model: HomeModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = model.input,
            onValueChange = model::onInput,
            singleLine = true,
            placeholder = { Text(stringResource(Res.string.home_field_placeholder), color = MovaColors.TextTertiary) },
            leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null, tint = MovaColors.TextSecondary) },
            trailingIcon = {
                if (model.input.isEmpty()) {
                    IconButton(onClick = model::pasteAndAnalyze) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = stringResource(Res.string.home_paste), tint = MovaColors.TextSecondary)
                    }
                } else {
                    IconButton(onClick = model::clear) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(Res.string.home_clear), tint = MovaColors.TextSecondary)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { model.analyze() }),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MovaColors.BlueBright,
                unfocusedBorderColor = MovaColors.OutlineStrong,
                focusedContainerColor = MovaColors.Surface,
                unfocusedContainerColor = MovaColors.Surface,
                cursorColor = MovaColors.Accent,
                focusedTextColor = MovaColors.TextPrimary,
                unfocusedTextColor = MovaColors.TextPrimary,
            ),
            modifier = Modifier
                .weight(1f)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                        model.analyze()
                        true
                    } else {
                        false
                    }
                },
        )
        Spacer(Modifier.width(12.dp))
        PrimaryButton(
            text = stringResource(Res.string.home_download),
            onClick = model::analyze,
            enabled = model.input.isNotBlank() && model.analysis !is HomeModel.Analysis.Loading,
            icon = Icons.Rounded.Download,
            modifier = Modifier.height(56.dp),
        )
    }
}

@Composable
private fun AnalyzingCard(engineState: EngineState) {
    MovaCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = MovaColors.Accent, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(stringResource(Res.string.analysis_loading_title), style = MaterialTheme.typography.titleMedium, color = MovaColors.TextPrimary)
                    Text(stringResource(Res.string.analysis_loading_message), style = MaterialTheme.typography.bodyMedium, color = MovaColors.TextSecondary)
                }
            }
            when (engineState) {
                is EngineState.Preparing -> {
                    Spacer(Modifier.height(16.dp))
                    EnginePreparing(engineState)
                }
                is EngineState.Updating -> {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(Res.string.analysis_loading_updating), style = MaterialTheme.typography.bodySmall, color = MovaColors.TextSecondary)
                }
                else -> Unit
            }
        }
    }
}

/** Progresso do download dos componentes no primeiro uso. */
@Composable
fun EnginePreparing(state: EngineState.Preparing) {
    Column {
        Text(stringResource(Res.string.engine_preparing_first_time), style = MaterialTheme.typography.bodySmall, color = MovaColors.TextSecondary)
        Spacer(Modifier.height(8.dp))
        val total = state.totalBytes
        val sizes = if (total != null) {
            stringResource(Res.string.queue_progress_of, Formatters.bytes(state.downloadedBytes), Formatters.bytes(total))
        } else {
            Formatters.bytes(state.downloadedBytes)
        }
        Text(
            stringResource(Res.string.engine_preparing_step, state.step, state.steps, stringResource(componentLabel(state.component)), sizes),
            style = MaterialTheme.typography.bodyMedium,
            color = MovaColors.TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        MovaProgressBar(total?.let { (state.downloadedBytes.toFloat() / it).coerceIn(0f, 1f) })
    }
}

@Composable
private fun ResultContent(done: HomeModel.Analysis.Done, model: HomeModel, onDownload: () -> Unit) {
    val report = done.report
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (report.isEmpty) {
            ProblemCard(Icons.Rounded.SearchOff, stringResource(Res.string.analysis_no_link_title), stringResource(Res.string.analysis_no_link_message), MovaColors.Warning)
            return@Column
        }
        if (report.hasMultipleLinks) {
            Text(
                pluralStringResource(Res.plurals.analysis_links_analyzed, report.results.size, report.results.size),
                style = MaterialTheme.typography.titleSmall,
                color = MovaColors.TextSecondary,
            )
        }
        report.results.filterNot { it is LinkAnalysis.Ready }.forEach { ResultProblem(it, model) }

        val items = done.items
        if (items.isEmpty()) return@Column
        val collectionTitle = report.results.filterIsInstance<LinkAnalysis.Ready>().singleOrNull()?.collectionTitle
        if (items.size == 1) {
            MediaPreview(items.single())
        } else {
            ItemSelection(items, done.selected, collectionTitle, model)
        }
        FormatSection(items, done.format, model::chooseFormat)
        val count = done.chosen.size
        PrimaryButton(
            text = if (items.size == 1) stringResource(Res.string.options_download_now) else pluralStringResource(Res.plurals.selection_download, count, count),
            onClick = onDownload,
            enabled = count > 0,
            icon = Icons.Rounded.Download,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MediaPreview(item: MediaItem) {
    MovaCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            MediaThumbnail(item.thumbnailUrl, item.mediaKind, width = 160.dp, height = 90.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, color = MovaColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                val details = listOfNotNull(
                    platformName(item.platform),
                    item.uploader,
                    item.durationSeconds?.let(Formatters::duration),
                    item.sizeBytes?.takeIf { item.source == MediaSource.DIRECT }?.let(Formatters::bytes),
                ).joinToString(" · ")
                Text(details, style = MaterialTheme.typography.bodyMedium, color = MovaColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.source == MediaSource.DIRECT && !item.supportsResume) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(Res.string.analysis_no_resume), style = MaterialTheme.typography.bodySmall, color = MovaColors.TextTertiary)
                }
            }
        }
    }
}

@Composable
private fun ItemSelection(items: List<MediaItem>, selected: Set<String>, title: String?, model: HomeModel) {
    MovaCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title ?: stringResource(Res.string.selection_title), style = MaterialTheme.typography.titleMedium, color = MovaColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(pluralStringResource(Res.plurals.items_count, items.size, items.size), style = MaterialTheme.typography.bodySmall, color = MovaColors.TextSecondary)
                }
                val all = selected.size == items.size
                SecondaryButton(
                    stringResource(if (all) Res.string.selection_none else Res.string.selection_all),
                    onClick = { model.selectAll(!all) },
                )
            }
            val listScroll = rememberScrollState()
            Box(Modifier.heightIn(max = 360.dp)) {
            Column(Modifier.verticalScroll(listScroll).padding(end = SCROLLBAR_GUTTER)) {
                items.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().clickable { model.toggle(item.key) }.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = item.key in selected,
                            onCheckedChange = { model.toggle(item.key) },
                            colors = CheckboxDefaults.colors(checkedColor = MovaColors.Blue, uncheckedColor = MovaColors.OutlineStrong),
                        )
                        Spacer(Modifier.width(6.dp))
                        MediaThumbnail(item.thumbnailUrl, item.mediaKind, width = 72.dp, height = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        TitleAndSubtitle(
                            item.title,
                            listOfNotNull(item.durationSeconds?.let(Formatters::duration), platformName(item.platform)).joinToString(" · "),
                            Modifier.weight(1f),
                        )
                    }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(listScroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun FormatSection(items: List<MediaItem>, format: FormatChoice, onFormat: (FormatChoice) -> Unit) {
    val formats = DownloadOptions.formatsFor(items)
    if (formats == listOf(FormatChoice.Original)) return
    val single = items.singleOrNull()
    val resolutions = formats.filterIsInstance<FormatChoice.Video>()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(Res.string.options_format), style = MaterialTheme.typography.titleSmall, color = MovaColors.TextSecondary)
        if (resolutions.isNotEmpty()) {
            val chosen = (format as? FormatChoice.Video)?.maxResolution
            FormatCard(
                icon = Icons.Rounded.Movie,
                title = stringResource(Res.string.options_video),
                subtitle = listOfNotNull(
                    stringResource(Res.string.options_video_hint),
                    single?.let { item -> (format as? FormatChoice.Video)?.let { DownloadOptions.estimatedSize(item, it) } }?.let { "≈ ${Formatters.bytes(it)}" },
                ).joinToString(" · "),
                selected = format is FormatChoice.Video,
                onSelect = { onFormat(resolutions.first()) },
                chips = resolutions.map { it to formatChipLabel(it) },
                selectedChip = chosen?.let { FormatChoice.Video(it) },
                onChip = onFormat,
            )
            val videoOnly = formats.filterIsInstance<FormatChoice.VideoOnly>()
            FormatCard(
                icon = Icons.AutoMirrored.Rounded.VolumeOff,
                title = stringResource(Res.string.options_video_only),
                subtitle = stringResource(Res.string.options_video_only_hint),
                selected = format is FormatChoice.VideoOnly,
                onSelect = { onFormat(videoOnly.first()) },
                chips = videoOnly.map { it to formatChipLabel(it) },
                selectedChip = format as? FormatChoice.VideoOnly,
                onChip = onFormat,
            )
        }
        val audio = formats.filterIsInstance<FormatChoice.Audio>()
        if (audio.isNotEmpty()) {
            FormatCard(
                icon = Icons.Rounded.MusicNote,
                title = stringResource(Res.string.options_audio),
                subtitle = stringResource(Res.string.options_audio_hint),
                selected = format is FormatChoice.Audio,
                onSelect = { onFormat(audio.first()) },
                chips = audio.map { it to formatChipLabel(it) },
                selectedChip = format as? FormatChoice.Audio,
                onChip = onFormat,
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
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
    ) {
        val radio: @Composable () -> Unit = {
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(selectedColor = MovaColors.Accent, unselectedColor = MovaColors.TextSecondary),
            )
        }
        val chipRow: @Composable (Modifier) -> Unit = { modifier ->
            Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEach { (choice, label) -> MovaChip(label, choice == selectedChip, onClick = { onChip(choice) }) }
            }
        }
        BoxWithConstraints {
            if (maxWidth >= 640.dp) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = if (selected) MovaColors.Accent else MovaColors.TextSecondary)
                    Spacer(Modifier.width(14.dp))
                    TitleAndSubtitle(title, subtitle, Modifier.weight(0.45f))
                    Spacer(Modifier.width(12.dp))
                    chipRow(Modifier.weight(0.55f))
                    Spacer(Modifier.width(8.dp))
                    radio()
                }
            } else {
                // Janela estreita: os formatos ficam abaixo do título.
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, contentDescription = null, tint = if (selected) MovaColors.Accent else MovaColors.TextSecondary)
                        Spacer(Modifier.width(14.dp))
                        TitleAndSubtitle(title, subtitle, Modifier.weight(1f))
                        radio()
                    }
                    Spacer(Modifier.height(8.dp))
                    chipRow(Modifier.padding(start = 38.dp))
                }
            }
        }
    }
}

@Composable
private fun ResultProblem(result: LinkAnalysis, model: HomeModel) {
    val link = when (result) {
        is LinkAnalysis.Ready -> return
        is LinkAnalysis.RecognizedWithoutDownload -> result.link
        is LinkAnalysis.NotSupported -> result.link
        is LinkAnalysis.UnrecognizedPage -> result.link
        is LinkAnalysis.UnsupportedFormat -> result.link
        is LinkAnalysis.Unavailable -> result.link
        is LinkAnalysis.AccessDenied -> result.link
        is LinkAnalysis.Restricted -> result.link
        is LinkAnalysis.TemporaryError -> result.link
    }
    val platform = platformName(link.platform)
    val (icon, title, message) = when (result) {
        is LinkAnalysis.RecognizedWithoutDownload -> Triple(Icons.Rounded.Block, stringResource(Res.string.analysis_recognized), stringResource(Res.string.analysis_no_download_message, platform))
        is LinkAnalysis.NotSupported -> Triple(Icons.Rounded.SearchOff, stringResource(Res.string.analysis_not_supported_title), stringResource(Res.string.analysis_not_supported_message))
        is LinkAnalysis.UnrecognizedPage -> Triple(Icons.Rounded.SearchOff, stringResource(Res.string.analysis_page_title), stringResource(Res.string.analysis_page_message, link.host))
        is LinkAnalysis.UnsupportedFormat -> Triple(Icons.Rounded.Block, stringResource(Res.string.analysis_streaming_title), stringResource(Res.string.analysis_streaming_message))
        is LinkAnalysis.Unavailable -> Triple(
            Icons.Rounded.SearchOff, stringResource(Res.string.analysis_unavailable_title),
            result.httpCode?.let { stringResource(Res.string.analysis_unavailable_message, it) } ?: stringResource(Res.string.analysis_unavailable_message_plain),
        )
        is LinkAnalysis.AccessDenied -> Triple(Icons.Rounded.Lock, stringResource(Res.string.analysis_denied_title), stringResource(Res.string.analysis_denied_message, result.httpCode))
        is LinkAnalysis.Restricted -> when (result.reason) {
            RestrictionReason.PRIVATE -> Triple(Icons.Rounded.Lock, stringResource(Res.string.analysis_private_title), stringResource(Res.string.analysis_private_message))
            RestrictionReason.LOGIN_REQUIRED -> Triple(Icons.Rounded.Lock, stringResource(Res.string.analysis_login_title), stringResource(Res.string.analysis_login_message))
            RestrictionReason.DRM -> Triple(Icons.Rounded.Lock, stringResource(Res.string.analysis_drm_title), stringResource(Res.string.analysis_drm_message))
            RestrictionReason.GEO_BLOCKED -> Triple(Icons.Rounded.Block, stringResource(Res.string.analysis_geo_title), stringResource(Res.string.analysis_geo_message))
        }
        is LinkAnalysis.TemporaryError -> Triple(Icons.Rounded.CloudOff, stringResource(Res.string.analysis_error_title), stringResource(Res.string.analysis_error_message))
        is LinkAnalysis.Ready -> return
    }
    val details = when (result) {
        is LinkAnalysis.NotSupported -> result.message
        is LinkAnalysis.Restricted -> result.message
        is LinkAnalysis.TemporaryError -> result.message
        else -> null
    }
    val retry = result is LinkAnalysis.TemporaryError || result is LinkAnalysis.NotSupported
    ProblemCard(icon, title, message, MovaColors.Warning, details = details, link = link.url, onRetry = if (retry) model::analyze else null)
}

@Composable
private fun ProblemCard(
    icon: ImageVector,
    title: String,
    message: String,
    tint: androidx.compose.ui.graphics.Color,
    details: String? = null,
    link: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    var showDetails by remember { mutableStateOf(false) }
    MovaCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = MovaColors.TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = MovaColors.TextSecondary)
                    if (link != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(link, style = MaterialTheme.typography.bodySmall, color = MovaColors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (!details.isNullOrBlank()) {
                AnimatedVisibility(showDetails) {
                    Text(
                        details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MovaColors.TextTertiary,
                        modifier = Modifier.padding(start = 38.dp, top = 10.dp)
                            .border(1.dp, MovaColors.Outline, RoundedCornerShape(10.dp)).padding(10.dp),
                    )
                }
            }
            if (link != null || onRetry != null || !details.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.padding(start = 38.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (onRetry != null) SecondaryButton(stringResource(Res.string.action_retry), onRetry, icon = Icons.Rounded.Refresh)
                    if (link != null) SecondaryButton(stringResource(Res.string.analysis_open_browser), { DesktopActions.browse(link) }, icon = Icons.AutoMirrored.Rounded.OpenInNew)
                    if (!details.isNullOrBlank()) {
                        SecondaryButton(
                            stringResource(if (showDetails) Res.string.analysis_hide_details else Res.string.analysis_show_details),
                            { showDetails = !showDetails },
                            icon = Icons.Rounded.ErrorOutline,
                        )
                    }
                }
            }
        }
    }
}
