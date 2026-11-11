package io.github.valeriofra.mova.desktop.ui

import androidx.compose.runtime.Composable
import io.github.valeriofra.mova.core.analysis.AudioFormat
import io.github.valeriofra.mova.core.analysis.FormatChoice
import io.github.valeriofra.mova.core.model.ContentKind
import io.github.valeriofra.mova.core.model.Platform
import io.github.valeriofra.mova.desktop.data.TaskError
import io.github.valeriofra.mova.desktop.data.TaskStatus
import io.github.valeriofra.mova.desktop.engine.ComponentId
import io.github.valeriofra.mova.desktop.resources.Res
import io.github.valeriofra.mova.desktop.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun platformName(platform: Platform): String = when (platform) {
    Platform.DIRECT -> stringResource(Res.string.platform_direct)
    Platform.WEB -> stringResource(Res.string.platform_web)
    else -> platform.displayName
}

fun kindLabel(kind: ContentKind): StringResource = when (kind) {
    ContentKind.VIDEO -> Res.string.kind_video
    ContentKind.SHORT_VIDEO -> Res.string.kind_short_video
    ContentKind.POST -> Res.string.kind_post
    ContentKind.STORY -> Res.string.kind_story
    ContentKind.STORY_HIGHLIGHT -> Res.string.kind_story_highlight
    ContentKind.PLAYLIST -> Res.string.kind_playlist
    ContentKind.COLLECTION -> Res.string.kind_collection
    ContentKind.PROFILE -> Res.string.kind_profile
    ContentKind.LIVE -> Res.string.kind_live
    ContentKind.SHORT_LINK -> Res.string.kind_short_link
    ContentKind.FILE -> Res.string.kind_file
    ContentKind.PAGE -> Res.string.kind_page
    ContentKind.UNKNOWN -> Res.string.kind_unknown
}

fun statusLabel(status: TaskStatus): StringResource = when (status) {
    TaskStatus.QUEUED -> Res.string.status_queued
    TaskStatus.RUNNING -> Res.string.status_running
    TaskStatus.PAUSED -> Res.string.status_paused
    TaskStatus.COMPLETED -> Res.string.status_completed
    TaskStatus.FAILED -> Res.string.status_failed
    TaskStatus.CANCELED -> Res.string.status_canceled
}

fun errorLabel(error: TaskError?): StringResource = when (error) {
    TaskError.NETWORK -> Res.string.error_network
    TaskError.SERVER -> Res.string.error_server
    TaskError.ACCESS_DENIED -> Res.string.error_access_denied
    TaskError.NOT_FOUND -> Res.string.error_not_found
    TaskError.INSUFFICIENT_STORAGE -> Res.string.error_storage_full
    TaskError.STORAGE -> Res.string.error_storage
    TaskError.LOGIN_REQUIRED -> Res.string.error_login_required
    TaskError.PRIVATE -> Res.string.error_private
    TaskError.DRM -> Res.string.error_drm
    TaskError.GEO_BLOCKED -> Res.string.error_geo
    TaskError.RATE_LIMITED -> Res.string.error_rate_limited
    TaskError.UNSUPPORTED -> Res.string.error_unsupported
    TaskError.ENGINE_SETUP -> Res.string.error_engine_setup
    TaskError.EXTRACTOR -> Res.string.error_extractor
    TaskError.UNKNOWN, null -> Res.string.error_unknown
}

fun componentLabel(id: ComponentId): StringResource = when (id) {
    ComponentId.YT_DLP -> Res.string.engine_component_ytdlp
    ComponentId.FFMPEG -> Res.string.engine_component_ffmpeg
    ComponentId.JS_RUNTIME -> Res.string.engine_component_js
}

/** "1080p", "MP3"... */
fun formatChipLabel(choice: FormatChoice): String = when (choice) {
    is FormatChoice.Video -> "${choice.maxResolution}p"
    is FormatChoice.VideoOnly -> "${choice.maxResolution}p"
    is FormatChoice.Audio -> choice.format.label
    FormatChoice.Original -> ""
}

val AUDIO_FORMATS: List<FormatChoice.Audio> = AudioFormat.entries.map { FormatChoice.Audio(it) }
