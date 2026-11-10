package io.github.valeriofra.mova.ui.analysis

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.valeriofra.mova.R
import io.github.valeriofra.mova.core.analysis.MediaItem
import io.github.valeriofra.mova.core.format.Formatters
import io.github.valeriofra.mova.ui.components.MediaKindIcon
import io.github.valeriofra.mova.ui.components.MovaCard
import io.github.valeriofra.mova.ui.components.MovaTopBar
import io.github.valeriofra.mova.ui.components.PrimaryButton
import io.github.valeriofra.mova.ui.components.TitleAndSubtitle
import io.github.valeriofra.mova.ui.labelRes
import io.github.valeriofra.mova.ui.theme.MovaColors

@Composable
fun ItemSelectionScreen(
    viewModel: AnalysisViewModel,
    onBack: () -> Unit,
    onEnqueued: (Int) -> Unit,
) {
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val notificationsAsked by viewModel.notificationsAsked.collectAsStateWithLifecycle()
    // Garante que o estado seja observado (itens vêm da análise já concluída).
    viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.enqueued.collect { count -> onEnqueued(count) }
    }
    val requestNotifications = rememberNotificationPermissionRequest(
        alreadyAsked = notificationsAsked,
        onAsked = viewModel::markNotificationsAsked,
    )

    ItemSelectionContent(
        items = viewModel.items,
        selected = selected,
        onBack = onBack,
        onToggle = viewModel::toggle,
        onToggleAll = viewModel::setAllSelected,
        onConfirm = { requestNotifications { viewModel.enqueueSelected() } },
    )
}

@Composable
fun ItemSelectionContent(
    items: List<MediaItem>,
    selected: Set<String>,
    onBack: () -> Unit,
    onToggle: (MediaItem) -> Unit,
    onToggleAll: (Boolean) -> Unit,
    onConfirm: () -> Unit,
) {
    val selectedCount = items.count { it.url in selected }
    val allState = when (selectedCount) {
        0 -> ToggleableState.Off
        items.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    val checkboxColors = CheckboxDefaults.colors(
        checkedColor = MovaColors.Blue,
        uncheckedColor = MovaColors.TextSecondary,
        checkmarkColor = MovaColors.TextPrimary,
    )

    Column(Modifier.fillMaxSize()) {
        MovaTopBar(title = stringResource(R.string.selection_title), onBack = onBack)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.selection_found, items.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = MovaColors.TextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .triStateToggleable(
                            state = allState,
                            onClick = { onToggleAll(allState != ToggleableState.On) },
                            role = Role.Checkbox,
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TriStateCheckbox(state = allState, onClick = null, colors = checkboxColors)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.selection_select_all),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MovaColors.TextPrimary,
                    )
                }
            }
            items(items, key = { it.url }) { item ->
                val checked = item.url in selected
                MovaCard(
                    highlighted = checked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .toggleable(value = checked, onValueChange = { onToggle(item) }, role = Role.Checkbox)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MediaKindIcon(item.mediaKind, size = 52.dp)
                        Spacer(Modifier.width(12.dp))
                        val details = buildList {
                            add(stringResource(item.mediaKind.labelRes()))
                            item.sizeBytes?.let { add(Formatters.bytes(it)) }
                        }.joinToString(" · ")
                        TitleAndSubtitle(item.fileName, details, Modifier.weight(1f))
                        Checkbox(checked = checked, onCheckedChange = null, colors = checkboxColors)
                    }
                }
            }
        }
        PrimaryButton(
            text = pluralStringResource(R.plurals.selection_download, selectedCount, selectedCount),
            onClick = onConfirm,
            enabled = selectedCount > 0,
            icon = Icons.Rounded.Download,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        )
    }
}
