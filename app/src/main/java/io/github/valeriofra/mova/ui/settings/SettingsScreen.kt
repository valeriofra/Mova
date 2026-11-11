package io.github.valeriofra.mova.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.valeriofra.mova.BuildConfig
import io.github.valeriofra.mova.R
import io.github.valeriofra.mova.core.format.Formatters
import io.github.valeriofra.mova.core.provider.IntegrationStatus
import io.github.valeriofra.mova.core.provider.ProviderRegistry
import io.github.valeriofra.mova.data.settings.AutoDelete
import io.github.valeriofra.mova.data.settings.MovaSettings
import io.github.valeriofra.mova.data.settings.ThemeMode
import io.github.valeriofra.mova.download.FilePublisher
import io.github.valeriofra.mova.download.YtDlpEngine
import io.github.valeriofra.mova.ui.components.AccentTextButton
import io.github.valeriofra.mova.ui.components.MovaCard
import io.github.valeriofra.mova.ui.components.MovaLogo
import io.github.valeriofra.mova.ui.components.MovaTopBar
import io.github.valeriofra.mova.ui.components.SectionHeader
import io.github.valeriofra.mova.ui.components.StatusPill
import io.github.valeriofra.mova.ui.theme.MovaColors

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, snackbarHost: SnackbarHostState) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val temporaryBytes by viewModel.temporaryBytes.collectAsStateWithLifecycle()
    val freed by viewModel.freedBytes.collectAsStateWithLifecycle()
    val engineState by viewModel.engineState.collectAsStateWithLifecycle()
    val engineUpdate by viewModel.engineUpdate.collectAsStateWithLifecycle()
    val cleanup by viewModel.cleanup.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current

    var notificationsGranted by remember { mutableStateOf(context.notificationsGranted()) }
    var language by remember { mutableStateOf(AppLanguage.current(context)) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var confirmAutoDelete by remember { mutableStateOf<AutoDelete?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsGranted = context.notificationsGranted()
        viewModel.refreshTemporarySize()
    }
    LaunchedEffect(Unit) { viewModel.loadEngine() }
    LaunchedEffect(engineUpdate) {
        val result = engineUpdate ?: return@LaunchedEffect
        viewModel.engineUpdateShown()
        val text = when (result) {
            is YtDlpEngine.UpdateResult.Updated -> resources.getString(R.string.settings_engine_updated, result.version.orEmpty())
            is YtDlpEngine.UpdateResult.UpToDate -> resources.getString(R.string.settings_engine_up_to_date)
            is YtDlpEngine.UpdateResult.Failed -> resources.getString(R.string.settings_engine_update_failed)
        }
        snackbarHost.showSnackbar(text)
    }
    LaunchedEffect(freed) {
        val bytes = freed ?: return@LaunchedEffect
        viewModel.freedMessageShown()
        snackbarHost.showSnackbar(resources.getString(R.string.settings_temp_cleaned, Formatters.bytes(bytes)))
    }

    LaunchedEffect(cleanup) {
        val result = cleanup ?: return@LaunchedEffect
        viewModel.cleanupShown()
        val text = if (result.filesDeleted > 0) {
            resources.getQuantityString(R.plurals.settings_auto_delete_done, result.filesDeleted, result.filesDeleted)
        } else {
            resources.getString(R.string.settings_auto_delete_enabled)
        }
        snackbarHost.showSnackbar(text)
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsGranted = granted
        if (!granted) context.openNotificationSettings()
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            val name = DocumentFile.fromTreeUri(context, uri)?.name
            viewModel.setStorageFolder(uri.toString(), name)
        }
    }

    SettingsContent(
        settings = settings ?: MovaSettings(),
        language = language,
        onThemeMode = viewModel::setThemeMode,
        onChooseLanguage = { showLanguageDialog = true },
        engineState = engineState,
        onUpdateEngine = viewModel::updateEngine,
        temporaryBytes = temporaryBytes,
        notificationsGranted = notificationsGranted,
        onWifiOnly = viewModel::setWifiOnly,
        onMaxConcurrent = viewModel::setMaxConcurrent,
        onChooseFolder = { folderLauncher.launch(null) },
        onDefaultFolder = {
            settings?.storageTreeUri?.let { uri ->
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        Uri.parse(uri),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
            }
            viewModel.useDefaultStorage()
        },
        onCleanTemporary = viewModel::cleanTemporaryFiles,
        onAutoDelete = { option ->
            val current = settings?.autoDelete ?: AutoDelete.OFF
            // Pede confirmação quando a mudança passa a apagar mais arquivos.
            val deletesMore = option.enabled && (!current.enabled || option.days < current.days)
            if (deletesMore) confirmAutoDelete = option else viewModel.setAutoDelete(option)
        },
        onEnableNotifications = {
            if (Build.VERSION.SDK_INT >= 33) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.openNotificationSettings()
            }
        },
    )
    if (showLanguageDialog) {
        LanguageDialog(
            current = language,
            onSelect = { option ->
                showLanguageDialog = false
                language = option
                AppLanguage.apply(context, option)
            },
            onDismiss = { showLanguageDialog = false },
        )
    }
    confirmAutoDelete?.let { option ->
        AlertDialog(
            onDismissRequest = { confirmAutoDelete = null },
            title = { Text(stringResource(R.string.settings_auto_delete_confirm_title)) },
            text = { Text(pluralStringResource(R.plurals.settings_auto_delete_confirm_message, option.days, option.days)) },
            confirmButton = {
                AccentTextButton(stringResource(R.string.settings_auto_delete_confirm), onClick = {
                    confirmAutoDelete = null
                    viewModel.setAutoDelete(option)
                })
            },
            dismissButton = { AccentTextButton(stringResource(R.string.action_cancel), onClick = { confirmAutoDelete = null }) },
            containerColor = MovaColors.SurfaceHigh,
        )
    }
}

@Composable
private fun LanguageDialog(
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            if (!AppLanguage.supported) {
                Text(stringResource(R.string.settings_language_unsupported) + "\n\n" + stringResource(R.string.settings_language_auto_hint))
            } else {
                Column(Modifier.selectableGroup()) {
                    Text(
                        stringResource(R.string.settings_language_auto_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MovaColors.TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    AppLanguage.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(selected = option == current, onClick = { onSelect(option) }, role = Role.RadioButton),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = option == current,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = MovaColors.Accent),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                option.nativeName ?: stringResource(R.string.settings_language_system),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MovaColors.TextPrimary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { AccentTextButton(stringResource(R.string.action_close), onDismiss) },
        containerColor = MovaColors.SurfaceHigh,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    settings: MovaSettings,
    language: AppLanguage,
    onThemeMode: (ThemeMode) -> Unit,
    onChooseLanguage: () -> Unit,
    engineState: YtDlpEngine.EngineState,
    onUpdateEngine: () -> Unit,
    temporaryBytes: Long,
    notificationsGranted: Boolean,
    onWifiOnly: (Boolean) -> Unit,
    onMaxConcurrent: (Int) -> Unit,
    onChooseFolder: () -> Unit,
    onDefaultFolder: () -> Unit,
    onCleanTemporary: () -> Unit,
    onAutoDelete: (AutoDelete) -> Unit,
    onEnableNotifications: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        MovaTopBar(title = stringResource(R.string.settings_title))
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SectionHeader(stringResource(R.string.settings_section_appearance)) }
            item {
                MovaCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RowIcon(Icons.Rounded.Palette)
                            TitleAndSubtitleWrap(
                                stringResource(R.string.settings_theme),
                                stringResource(R.string.settings_theme_hint),
                                Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        val modes = listOf(
                            ThemeMode.DARK to R.string.settings_theme_dark,
                            ThemeMode.LIGHT to R.string.settings_theme_light,
                            ThemeMode.SYSTEM to R.string.settings_theme_system,
                        )
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            modes.forEachIndexed { index, (mode, label) ->
                                SegmentedButton(
                                    selected = settings.themeMode == mode,
                                    onClick = { onThemeMode(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                                    colors = segmentedColors(),
                                ) { Text(stringResource(label), maxLines = 1) }
                            }
                        }
                    }
                }
            }
            item {
                MovaCard(Modifier.fillMaxWidth(), onClick = onChooseLanguage) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        RowIcon(Icons.Rounded.Language)
                        TitleAndSubtitleWrap(
                            stringResource(R.string.settings_language),
                            language.nativeName
                                ?: stringResource(R.string.settings_language_auto_current, stringResource(R.string.language_name)),
                            Modifier.weight(1f),
                        )
                        AccentTextButton(stringResource(R.string.settings_language_change), onChooseLanguage)
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.settings_section_downloads), Modifier.padding(top = 12.dp)) }
            item {
                MovaCard(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .toggleable(value = settings.wifiOnly, onValueChange = onWifiOnly, role = Role.Switch)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RowIcon(Icons.Rounded.Wifi)
                        TitleAndSubtitleWrap(
                            stringResource(R.string.settings_wifi_only),
                            stringResource(R.string.settings_wifi_only_hint),
                            Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = settings.wifiOnly, onCheckedChange = null, colors = switchColors())
                    }
                }
            }
            item {
                MovaCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RowIcon(Icons.Rounded.Layers)
                            TitleAndSubtitleWrap(
                                stringResource(R.string.settings_concurrent),
                                stringResource(R.string.settings_concurrent_hint),
                                Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        val options = MovaSettings.CONCURRENT_RANGE.toList()
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            options.forEachIndexed { index, value ->
                                SegmentedButton(
                                    selected = settings.maxConcurrent == value,
                                    onClick = { onMaxConcurrent(value) },
                                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                                    colors = segmentedColors(),
                                ) { Text(value.toString()) }
                            }
                        }
                    }
                }
            }

            item {
                MovaCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        RowIcon(Icons.Rounded.Bolt)
                        val status = when (engineState) {
                            YtDlpEngine.EngineState.NotReady -> stringResource(R.string.settings_engine_not_ready)
                            YtDlpEngine.EngineState.Preparing -> stringResource(R.string.settings_engine_preparing)
                            is YtDlpEngine.EngineState.Updating -> stringResource(R.string.settings_engine_updating)
                            is YtDlpEngine.EngineState.Ready ->
                                stringResource(R.string.settings_engine_version, engineState.version ?: "—")
                            is YtDlpEngine.EngineState.Failed -> stringResource(R.string.settings_engine_failed)
                        }
                        TitleAndSubtitleWrap(
                            stringResource(R.string.settings_engine),
                            status,
                            Modifier.weight(1f),
                        )
                        val busy = engineState is YtDlpEngine.EngineState.Preparing ||
                            engineState is YtDlpEngine.EngineState.Updating
                        if (!busy) AccentTextButton(stringResource(R.string.settings_engine_update), onUpdateEngine)
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.settings_section_storage), Modifier.padding(top = 12.dp)) }
            item {
                MovaCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RowIcon(Icons.Rounded.Folder)
                            val location = settings.storageTreeUri?.let {
                                settings.storageTreeName ?: stringResource(R.string.settings_folder_custom)
                            } ?: "Download/${FilePublisher.FOLDER}"
                            TitleAndSubtitleWrap(
                                stringResource(R.string.settings_folder),
                                location,
                                Modifier.weight(1f),
                            )
                        }
                        Row(Modifier.padding(start = 36.dp, top = 4.dp)) {
                            AccentTextButton(stringResource(R.string.settings_folder_choose), onChooseFolder)
                            if (settings.storageTreeUri != null) {
                                AccentTextButton(stringResource(R.string.settings_folder_default), onDefaultFolder)
                            }
                        }
                    }
                }
            }
            item {
                MovaCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        RowIcon(Icons.Rounded.CleaningServices)
                        TitleAndSubtitleWrap(
                            stringResource(R.string.settings_temp),
                            stringResource(R.string.settings_temp_hint, Formatters.bytes(temporaryBytes)),
                            Modifier.weight(1f),
                        )
                        AccentTextButton(stringResource(R.string.action_clear), onCleanTemporary)
                    }
                }
            }
            item {
                val policy = settings.autoDelete
                MovaCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.toggleable(
                                value = policy.enabled,
                                // Ao ligar, começa pelo período mais longo (o que apaga menos).
                                onValueChange = { on -> onAutoDelete(if (on) AutoDelete.DAYS_30 else AutoDelete.OFF) },
                                role = Role.Switch,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RowIcon(Icons.Rounded.AutoDelete)
                            TitleAndSubtitleWrap(
                                stringResource(R.string.settings_auto_delete),
                                if (policy.enabled) {
                                    pluralStringResource(R.plurals.settings_auto_delete_on, policy.days, policy.days)
                                } else {
                                    stringResource(R.string.settings_auto_delete_off_hint)
                                },
                                Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(12.dp))
                            Switch(checked = policy.enabled, onCheckedChange = null, colors = switchColors())
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.padding(start = 36.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AutoDelete.entries.filter { it.enabled }.forEach { option ->
                                val selected = policy == option
                                FilterChip(
                                    selected = selected,
                                    onClick = { onAutoDelete(option) },
                                    label = {
                                        Text(pluralStringResource(R.plurals.settings_auto_delete_days, option.days, option.days))
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MovaColors.Surface,
                                        labelColor = MovaColors.TextSecondary,
                                        selectedContainerColor = MovaColors.Blue.copy(alpha = 0.22f),
                                        selectedLabelColor = MovaColors.Accent,
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selected,
                                        borderColor = MovaColors.Outline,
                                        selectedBorderColor = MovaColors.BlueBright,
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.settings_section_notifications), Modifier.padding(top = 12.dp)) }
            item {
                MovaCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        RowIcon(Icons.Rounded.Notifications)
                        TitleAndSubtitleWrap(
                            stringResource(R.string.settings_notifications),
                            stringResource(
                                if (notificationsGranted) R.string.settings_notifications_on else R.string.settings_notifications_off,
                            ),
                            Modifier.weight(1f),
                        )
                        if (!notificationsGranted) {
                            AccentTextButton(stringResource(R.string.settings_notifications_enable), onEnableNotifications)
                        }
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.settings_section_platforms), Modifier.padding(top = 12.dp)) }
            item {
                Text(
                    stringResource(R.string.settings_platforms_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MovaColors.TextSecondary,
                )
            }
            item {
                MovaCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        ProviderRegistry.knownPlatforms.forEach { caps ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    caps.platform.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MovaColors.TextPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                when (caps.status) {
                                    IntegrationStatus.AVAILABLE ->
                                        StatusPill(stringResource(R.string.platform_available), MovaColors.Success, icon = Icons.Rounded.CheckCircle)
                                    IntegrationStatus.PARTIAL ->
                                        StatusPill(stringResource(R.string.platform_partial), MovaColors.Accent, icon = Icons.Rounded.CheckCircle)
                                    IntegrationStatus.EXPERIMENTAL ->
                                        StatusPill(stringResource(R.string.platform_experimental), MovaColors.Warning, icon = Icons.Rounded.Schedule)
                                    else ->
                                        StatusPill(stringResource(R.string.platform_recognition_only), MovaColors.Accent, icon = Icons.Rounded.Schedule)
                                }
                            }
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.platform_direct_links),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MovaColors.TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            StatusPill(stringResource(R.string.platform_available), MovaColors.Success, icon = Icons.Rounded.CheckCircle)
                        }
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.settings_section_about), Modifier.padding(top = 12.dp)) }
            item {
                MovaCard(Modifier.fillMaxWidth()) {
                    // Column: as duas linhas ficam uma abaixo da outra (antes se sobrepunham).
                    Column {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        MovaLogo(size = 40.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("MOVA", style = MaterialTheme.typography.titleMedium, color = MovaColors.TextPrimary)
                            Text(
                                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                                style = MaterialTheme.typography.bodySmall,
                                color = MovaColors.TextSecondary,
                            )
                        }
                    }
                    Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Info, null, tint = MovaColors.Accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.settings_about_text),
                            style = MaterialTheme.typography.bodySmall,
                            color = MovaColors.TextSecondary,
                        )
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun segmentedColors() = SegmentedButtonDefaults.colors(
    activeContainerColor = MovaColors.Blue.copy(alpha = 0.25f),
    activeContentColor = MovaColors.TextPrimary,
    activeBorderColor = MovaColors.BlueBright,
    inactiveContainerColor = MovaColors.Surface,
    inactiveContentColor = MovaColors.TextSecondary,
    inactiveBorderColor = MovaColors.OutlineStrong,
)

@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedTrackColor = MovaColors.Blue,
    checkedThumbColor = Color.White,
    uncheckedTrackColor = MovaColors.SurfaceHighest,
    uncheckedThumbColor = MovaColors.TextSecondary,
    uncheckedBorderColor = MovaColors.OutlineStrong,
)

@Composable
private fun RowIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = MovaColors.Accent, modifier = Modifier.size(22.dp))
    Spacer(Modifier.width(14.dp))
}

@Composable
private fun TitleAndSubtitleWrap(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MovaColors.TextPrimary)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MovaColors.TextSecondary)
    }
}

private fun Context.notificationsGranted(): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun Context.openNotificationSettings() {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}
