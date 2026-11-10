package io.github.valeriofra.mova.ui

import androidx.annotation.StringRes
import io.github.valeriofra.mova.R
import io.github.valeriofra.mova.core.model.ContentKind
import io.github.valeriofra.mova.core.model.MediaKind
import io.github.valeriofra.mova.data.db.DownloadError
import io.github.valeriofra.mova.data.db.DownloadStatus

@StringRes
fun ContentKind.labelRes(): Int = when (this) {
    ContentKind.VIDEO -> R.string.kind_video
    ContentKind.SHORT_VIDEO -> R.string.kind_short_video
    ContentKind.POST -> R.string.kind_post
    ContentKind.STORY -> R.string.kind_story
    ContentKind.STORY_HIGHLIGHT -> R.string.kind_story_highlight
    ContentKind.PLAYLIST -> R.string.kind_playlist
    ContentKind.COLLECTION -> R.string.kind_collection
    ContentKind.PROFILE -> R.string.kind_profile
    ContentKind.LIVE -> R.string.kind_live
    ContentKind.SHORT_LINK -> R.string.kind_short_link
    ContentKind.FILE -> R.string.kind_file
    ContentKind.PAGE -> R.string.kind_page
    ContentKind.UNKNOWN -> R.string.kind_unknown
}

@StringRes
fun MediaKind.labelRes(): Int = when (this) {
    MediaKind.VIDEO -> R.string.media_video
    MediaKind.AUDIO -> R.string.media_audio
    MediaKind.IMAGE -> R.string.media_image
    MediaKind.DOCUMENT -> R.string.media_document
    MediaKind.OTHER -> R.string.media_other
}

@StringRes
fun DownloadStatus.labelRes(): Int = when (this) {
    DownloadStatus.QUEUED -> R.string.status_queued
    DownloadStatus.RUNNING -> R.string.status_running
    DownloadStatus.PAUSED -> R.string.status_paused
    DownloadStatus.COMPLETED -> R.string.status_completed
    DownloadStatus.FAILED -> R.string.status_failed
    DownloadStatus.CANCELED -> R.string.status_canceled
}

@StringRes
fun DownloadError?.messageRes(): Int = when (this) {
    DownloadError.NETWORK -> R.string.error_network
    DownloadError.SERVER -> R.string.error_server
    DownloadError.NOT_FOUND -> R.string.error_not_found
    DownloadError.ACCESS_DENIED -> R.string.error_access_denied
    DownloadError.INSUFFICIENT_STORAGE -> R.string.error_storage_full
    DownloadError.STORAGE -> R.string.error_storage
    DownloadError.UNKNOWN, null -> R.string.error_unknown
}
