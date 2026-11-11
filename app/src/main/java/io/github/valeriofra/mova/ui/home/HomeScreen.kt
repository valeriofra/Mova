package io.github.valeriofra.mova.ui.home

import android.content.ClipboardManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.valeriofra.mova.IncomingEvent
import io.github.valeriofra.mova.R
import io.github.valeriofra.mova.core.format.Formatters
import io.github.valeriofra.mova.core.model.Platform
import io.github.valeriofra.mova.data.db.DownloadEntity
import io.github.valeriofra.mova.data.db.DownloadStatus
import io.github.valeriofra.mova.ui.components.AccentTextButton
import io.github.valeriofra.mova.ui.components.MediaThumbnail
import io.github.valeriofra.mova.ui.components.MovaCard
import io.github.valeriofra.mova.ui.components.MovaLogo
import io.github.valeriofra.mova.ui.components.MovaWordmark
import io.github.valeriofra.mova.ui.components.PrimaryButton
import io.github.valeriofra.mova.ui.components.SectionHeader
import io.github.valeriofra.mova.ui.components.TitleAndSubtitle
import io.github.valeriofra.mova.ui.labelRes
import io.github.valeriofra.mova.ui.theme.MovaColors

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    sharedText: IncomingEvent.SharedText?,
    onSharedTextConsumed: () -> Unit,
    onAnalyze: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val recent by viewModel.recent.collectAsStateWithLifecycle()

    // Link recebido pelo Compartilhar: preenche o campo, sem iniciar o download.
    LaunchedEffect(sharedText) {
        if (sharedText != null) {
            viewModel.onInputChange(sharedText.text)
            onSharedTextConsumed()
        }
    }

    HomeContent(
        input = viewModel.input,
        preview = viewModel.preview,
        recent = recent,
        onInputChange = viewModel::onInputChange,
        onClear = viewModel::clear,
        onAnalyze = { onAnalyze(viewModel.input) },
        onOpenSettings = onOpenSettings,
        onOpenRecent = { item -> if (item.status == DownloadStatus.COMPLETED) onOpenLibrary() else onOpenQueue() },
        onOpenLibrary = onOpenLibrary,
    )
}

@Composable
fun HomeContent(
    input: String,
    preview: LinkPreview,
    recent: List<DownloadEntity>,
    onInputChange: (String) -> Unit,
    onClear: () -> Unit,
    onAnalyze: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRecent: (DownloadEntity) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val canAnalyze = preview is LinkPreview.Single || preview is LinkPreview.Multiple
    val analyze = {
        keyboard?.hide()
        onAnalyze()
    }
    // Com o teclado aberto, o símbolo e o título saem de cena para o campo e o botão caberem.
    @OptIn(ExperimentalLayoutApi::class)
    val imeVisible = WindowInsets.isImeVisible

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .imePadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MovaWordmark(Modifier.weight(1f))
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = stringResource(R.string.nav_settings),
                        tint = MovaColors.TextSecondary,
                    )
                }
            }
        }

        item {
            AnimatedVisibility(visible = !imeVisible, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MovaLogo(size = 96.dp)
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.home_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MovaColors.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MovaColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            }
            if (imeVisible) Spacer(Modifier.height(16.dp))
        }

        item {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.home_field_placeholder)) },
                leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                trailingIcon = {
                    if (input.isEmpty()) {
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)?.coerceToText(context)?.toString()
                            if (!text.isNullOrBlank()) onInputChange(text)
                        }) {
                            Icon(Icons.Rounded.ContentPaste, contentDescription = stringResource(R.string.home_paste))
                        }
                    } else {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.home_clear))
                        }
                    }
                },
                maxLines = 3,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (canAnalyze) analyze() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MovaColors.BlueBright,
                    unfocusedBorderColor = MovaColors.OutlineStrong,
                    focusedContainerColor = MovaColors.Surface,
                    unfocusedContainerColor = MovaColors.Surface,
                    cursorColor = MovaColors.Accent,
                    focusedLeadingIconColor = MovaColors.Accent,
                    unfocusedLeadingIconColor = MovaColors.TextSecondary,
                    focusedTrailingIconColor = MovaColors.TextSecondary,
                    unfocusedTrailingIconColor = MovaColors.TextSecondary,
                    focusedPlaceholderColor = MovaColors.TextSecondary,
                    unfocusedPlaceholderColor = MovaColors.TextSecondary,
                ),
            )
        }

        item {
            AnimatedVisibility(visible = preview != LinkPreview.Empty, enter = fadeIn(), exit = fadeOut()) {
                PreviewLine(preview, Modifier.padding(top = 10.dp, start = 4.dp))
            }
        }

        item {
            PrimaryButton(
                text = stringResource(R.string.home_analyze),
                onClick = analyze,
                enabled = canAnalyze,
                icon = Icons.Rounded.Download,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        item {
            SectionHeader(
                title = stringResource(R.string.home_recent),
                modifier = Modifier.padding(top = 32.dp, bottom = 4.dp),
                action = if (recent.isNotEmpty()) {
                    { AccentTextButton(stringResource(R.string.home_see_library), onClick = onOpenLibrary) }
                } else {
                    null
                },
            )
        }

        if (recent.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.home_recent_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MovaColors.TextSecondary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        } else {
            items(recent, key = { it.id }) { item ->
                RecentRow(item, onClick = { onOpenRecent(item) }, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun PreviewLine(preview: LinkPreview, modifier: Modifier = Modifier) {
    val (icon, color, text) = when (preview) {
        is LinkPreview.Single -> {
            val link = preview.link
            val text = if (link.platform == Platform.WEB) {
                stringResource(R.string.home_preview_site, link.host)
            } else {
                stringResource(
                    R.string.home_preview_platform,
                    link.platform.displayName,
                    stringResource(link.kind.labelRes()),
                )
            }
            Triple(Icons.Rounded.CheckCircle, MovaColors.Success, text)
        }
        is LinkPreview.Multiple -> Triple(
            Icons.Rounded.CheckCircle,
            MovaColors.Success,
            pluralStringResource(R.plurals.home_preview_multiple, preview.count, preview.count),
        )
        LinkPreview.NoLink -> Triple(Icons.Rounded.ErrorOutline, MovaColors.Warning, stringResource(R.string.home_preview_no_link))
        LinkPreview.Empty -> return
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MovaColors.TextPrimary)
    }
}

@Composable
private fun RecentRow(item: DownloadEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val status = stringResource(item.status.labelRes())
    val subtitle = buildList {
        add(status)
        val size = item.totalBytes ?: item.downloadedBytes.takeIf { it > 0 }
        if (size != null) add(Formatters.bytes(size))
    }.joinToString(" · ")
    MovaCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MediaThumbnail(uri = item.outputUri, kind = item.mediaKind, size = 48.dp, remoteUrl = item.thumbnailUrl)
            TitleAndSubtitle(title = item.fileName, subtitle = subtitle, modifier = Modifier.weight(1f))
        }
    }
}
