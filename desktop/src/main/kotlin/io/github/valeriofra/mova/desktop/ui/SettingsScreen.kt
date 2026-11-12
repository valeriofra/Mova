package io.github.valeriofra.mova.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.valeriofra.mova.core.format.Formatters
import io.github.valeriofra.mova.desktop.BuildInfo
import io.github.valeriofra.mova.desktop.data.DesktopSettings
import io.github.valeriofra.mova.desktop.data.SaveMode
import io.github.valeriofra.mova.desktop.data.ThemeMode
import io.github.valeriofra.mova.desktop.engine.EngineState
import io.github.valeriofra.mova.desktop.i18n.Localization
import io.github.valeriofra.mova.desktop.resources.*
import io.github.valeriofra.mova.desktop.ui.components.AccentTextButton
import io.github.valeriofra.mova.desktop.ui.components.MovaCard
import io.github.valeriofra.mova.desktop.ui.components.MovaChip
import io.github.valeriofra.mova.desktop.ui.components.MovaLogo
import io.github.valeriofra.mova.desktop.ui.components.MovaSwitch
import io.github.valeriofra.mova.desktop.ui.components.ScreenTitle
import io.github.valeriofra.mova.desktop.ui.components.SectionHeader
import io.github.valeriofra.mova.desktop.ui.components.TitleAndSubtitle
import io.github.valeriofra.mova.desktop.ui.components.LocalScreenPadding
import io.github.valeriofra.mova.desktop.ui.theme.MovaColors
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

data class SettingsActions(
    val saveMode: (SaveMode) -> Unit = {},
    val openFolder: () -> Unit = {},
    val themeMode: (ThemeMode) -> Unit = {},
    val language: (String?) -> Unit = {},
    val chooseFolder: () -> Unit = {},
    val defaultFolder: () -> Unit = {},
    val maxConcurrent: (Int) -> Unit = {},
    val autoDelete: (Int) -> Unit = {},
    val cleanTemporary: () -> Unit = {},
    val updateEngine: () -> Unit = {},
    val openEngineFolder: () -> Unit = {},
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: DesktopSettings,
    destination: String,
    engineState: EngineState,
    temporaryBytes: Long,
    actions: SettingsActions,
) {
    var confirmAutoDelete by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = LocalScreenPadding.current, vertical = 20.dp)) {
        ScreenTitle(stringResource(Res.string.settings_title))
        Column(Modifier.widthIn(max = 820.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader(stringResource(Res.string.settings_section_appearance))
            SettingCard(Icons.Rounded.Palette, stringResource(Res.string.settings_theme), stringResource(Res.string.settings_theme_hint)) {
                Segmented(
                    options = listOf(
                        ThemeMode.DARK to stringResource(Res.string.settings_theme_dark),
                        ThemeMode.LIGHT to stringResource(Res.string.settings_theme_light),
                        ThemeMode.SYSTEM to stringResource(Res.string.settings_theme_system),
                    ),
                    selected = settings.themeMode,
                    onSelect = actions.themeMode,
                )
            }
            val automatic = stringResource(Res.string.settings_language_auto_current, stringResource(Res.string.language_name))
            SettingCard(Icons.Rounded.Language, stringResource(Res.string.settings_language), stringResource(Res.string.settings_language_auto_hint)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MovaChip(if (settings.language == null) automatic else stringResource(Res.string.settings_language_system), settings.language == null, { actions.language(null) })
                    Localization.LANGUAGES.forEach { (code, name) -> MovaChip(name, settings.language == code, { actions.language(code) }) }
                }
            }

            SectionHeader(stringResource(Res.string.settings_section_downloads), Modifier.padding(top = 12.dp))
            SettingCard(
                Icons.Rounded.Folder,
                stringResource(Res.string.settings_save_title),
                stringResource(Res.string.settings_save_hint),
                trailing = { AccentTextButton(stringResource(Res.string.library_open_folder), actions.openFolder) },
            ) {
                Column(Modifier.selectableGroup()) {
                    SaveOption(
                        selected = settings.saveMode == SaveMode.ASK,
                        title = stringResource(Res.string.settings_save_ask),
                        subtitle = stringResource(Res.string.settings_save_ask_hint),
                        onSelect = { actions.saveMode(SaveMode.ASK) },
                    )
                    SaveOption(
                        selected = settings.saveMode == SaveMode.FOLDER,
                        title = stringResource(Res.string.settings_save_always),
                        subtitle = destination,
                        onSelect = { actions.saveMode(SaveMode.FOLDER) },
                    )
                    FlowRow(Modifier.padding(start = 40.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        AccentTextButton(stringResource(Res.string.settings_folder_choose), actions.chooseFolder)
                        if (settings.downloadDir != null) AccentTextButton(stringResource(Res.string.settings_folder_default), actions.defaultFolder)
                    }
                }
            }
            SettingCard(Icons.Rounded.Layers, stringResource(Res.string.settings_concurrent), stringResource(Res.string.settings_concurrent_hint)) {
                Segmented(
                    options = DesktopSettings.CONCURRENT_RANGE.map { it to it.toString() },
                    selected = settings.maxConcurrent,
                    onSelect = actions.maxConcurrent,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 260.dp),
                )
            }
            SettingCard(
                Icons.Rounded.Bolt,
                stringResource(Res.string.settings_engine),
                engineStatus(engineState),
                trailing = {
                    val busy = engineState is EngineState.Preparing || engineState is EngineState.Updating
                    if (!busy) AccentTextButton(stringResource(Res.string.settings_engine_update), actions.updateEngine)
                },
            ) {
                if (engineState is EngineState.Preparing) EnginePreparing(engineState)
                AccentTextButton(stringResource(Res.string.settings_engine_open_folder), actions.openEngineFolder)
            }

            SectionHeader(stringResource(Res.string.settings_section_storage), Modifier.padding(top = 12.dp))
            val days = settings.autoDeleteDays
            SettingCard(
                Icons.Rounded.AutoDelete,
                stringResource(Res.string.settings_auto_delete),
                if (days > 0) pluralStringResource(Res.plurals.settings_auto_delete_on, days, days) else stringResource(Res.string.settings_auto_delete_off_hint),
                toggled = days > 0,
                onToggle = { on -> requestAutoDelete(if (on) DesktopSettings.AUTO_DELETE_OPTIONS.last() else 0, days, actions) { confirmAutoDelete = it } },
                trailing = { MovaSwitch(days > 0, null) },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DesktopSettings.AUTO_DELETE_OPTIONS.forEach { option ->
                        MovaChip(
                            pluralStringResource(Res.plurals.settings_auto_delete_days, option, option),
                            days == option,
                            { requestAutoDelete(option, days, actions) { confirmAutoDelete = it } },
                        )
                    }
                }
            }
            SettingCard(
                Icons.Rounded.CleaningServices,
                stringResource(Res.string.settings_temp),
                stringResource(Res.string.settings_temp_hint, Formatters.bytes(temporaryBytes)),
                trailing = { AccentTextButton(stringResource(Res.string.action_clear), actions.cleanTemporary) },
            )

            SectionHeader(stringResource(Res.string.settings_section_about), Modifier.padding(top = 12.dp))
            MovaCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MovaLogo(44.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(stringResource(Res.string.settings_about_name), style = MaterialTheme.typography.titleMedium, color = MovaColors.TextPrimary)
                            Text(stringResource(Res.string.settings_version, BuildInfo.VERSION, BuildInfo.BUILD), style = MaterialTheme.typography.bodyMedium, color = MovaColors.TextSecondary)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = MovaColors.Accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(Res.string.settings_about_text), style = MaterialTheme.typography.bodyMedium, color = MovaColors.TextSecondary)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    confirmAutoDelete?.let { option ->
        AlertDialog(
            onDismissRequest = { confirmAutoDelete = null },
            title = { Text(stringResource(Res.string.settings_auto_delete_confirm_title)) },
            text = { Text(pluralStringResource(Res.plurals.settings_auto_delete_confirm_message, option, option)) },
            confirmButton = {
                AccentTextButton(stringResource(Res.string.settings_auto_delete_confirm), {
                    confirmAutoDelete = null
                    actions.autoDelete(option)
                })
            },
            dismissButton = { AccentTextButton(stringResource(Res.string.action_cancel), { confirmAutoDelete = null }) },
            containerColor = MovaColors.SurfaceHigh,
        )
    }
}

@Composable
private fun SaveOption(selected: Boolean, title: String, subtitle: String, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = MovaColors.Accent, unselectedColor = MovaColors.TextSecondary),
        )
        Spacer(Modifier.width(12.dp))
        TitleAndSubtitle(title, subtitle, Modifier.weight(1f))
    }
}

/** Pede confirmação quando a mudança passa a apagar mais arquivos. */
private fun requestAutoDelete(option: Int, current: Int, actions: SettingsActions, confirm: (Int) -> Unit) {
    val deletesMore = option > 0 && (current == 0 || option < current)
    if (deletesMore) confirm(option) else actions.autoDelete(option)
}

@Composable
private fun engineStatus(state: EngineState): String = when (state) {
    EngineState.NotReady -> stringResource(Res.string.settings_engine_not_ready)
    is EngineState.Preparing -> stringResource(Res.string.settings_engine_preparing)
    is EngineState.Updating -> stringResource(Res.string.settings_engine_updating)
    is EngineState.Ready -> listOf(
        stringResource(Res.string.settings_engine_version, state.version ?: "—"),
        stringResource(if (state.systemFfmpeg) Res.string.settings_engine_ffmpeg_system else Res.string.settings_engine_ffmpeg_bundled),
    ).joinToString(" · ")
    is EngineState.Failed -> stringResource(Res.string.settings_engine_failed) + (state.message?.let { ": ${it.take(160)}" } ?: "")
}

@Composable
private fun SettingCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    toggled: Boolean = false,
    onToggle: ((Boolean) -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    MovaCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            val rowModifier = if (onToggle != null) Modifier.toggleable(value = toggled, role = Role.Switch, onValueChange = onToggle) else Modifier
            Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MovaColors.Accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                TitleAndSubtitle(title, subtitle, Modifier.weight(1f))
                if (trailing != null) {
                    Spacer(Modifier.width(12.dp))
                    trailing()
                }
            }
            if (content != null) {
                Spacer(Modifier.height(10.dp))
                Column(Modifier.padding(start = 36.dp)) { content() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp)) {
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MovaColors.Blue.copy(alpha = 0.22f),
                    activeContentColor = MovaColors.Accent,
                    activeBorderColor = MovaColors.BlueBright,
                    inactiveContainerColor = MovaColors.Surface,
                    inactiveContentColor = MovaColors.TextSecondary,
                    inactiveBorderColor = MovaColors.OutlineStrong,
                ),
            ) { Text(label, maxLines = 1) }
        }
    }
}
