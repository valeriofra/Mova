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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
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
import io.github.valeriofra.mova.data.settings.MovaSettings
import io.github.valeriofra.mova.download.FilePublisher
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
    val context = LocalContext.current
    val resources = LocalResources.current

    var notificationsGranted by remember { mutableStateOf(context.notificationsGranted()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsGranted = context.notificationsGranted()
        viewModel.refreshTemporarySize()
    }
    LaunchedEffect(freed) {
        val bytes = freed ?: return@LaunchedEffect
        viewModel.freedMessageShown()
        snackbarHost.showSnackbar(resources.getString(R.string.settings_temp_cleaned, Formatters.bytes(bytes)))
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
        onEnableNotifications = {
            if (Build.VERSION.SDK_INT >= 33) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.openNotificationSettings()
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    settings: MovaSettings,
    temporaryBytes: Long,
    notificationsGranted: Boolean,
    onWifiOnly: (Boolean) -> Unit,
    onMaxConcurrent: (Int) -> Unit,
    onChooseFolder: () -> Unit,
    onDefaultFolder: () -> Unit,
    onCleanTemporary: () -> Unit,
    onEnableNotifications: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        MovaTopBar(title = stringResource(R.string.settings_title))
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SectionHeader(stringResource(R.string.settings_section_downloads)) }
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
                        Switch(
                            checked = settings.wifiOnly,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MovaColors.Blue,
                                checkedThumbColor = MovaColors.TextPrimary,
                                uncheckedTrackColor = MovaColors.SurfaceHighest,
                                uncheckedThumbColor = MovaColors.TextSecondary,
                                uncheckedBorderColor = MovaColors.OutlineStrong,
                            ),
                        )
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
                                    colors = SegmentedButtonDefaults.colors(
                                        activeContainerColor = MovaColors.Blue.copy(alpha = 0.25f),
                                        activeContentColor = MovaColors.TextPrimary,
                                        activeBorderColor = MovaColors.BlueBright,
                                        inactiveContainerColor = MovaColors.Surface,
                                        inactiveContentColor = MovaColors.TextSecondary,
                                        inactiveBorderColor = MovaColors.OutlineStrong,
                                    ),
                                ) { Text(value.toString()) }
                            }
                        }
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
                                if (caps.status == IntegrationStatus.AVAILABLE) {
                                    StatusPill(stringResource(R.string.platform_available), MovaColors.Success, icon = Icons.Rounded.CheckCircle)
                                } else {
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
