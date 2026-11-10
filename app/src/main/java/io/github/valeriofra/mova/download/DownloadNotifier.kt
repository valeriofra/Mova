package io.github.valeriofra.mova.download

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import io.github.valeriofra.mova.MainActivity
import io.github.valeriofra.mova.R
import io.github.valeriofra.mova.core.format.Formatters
import io.github.valeriofra.mova.data.db.DownloadEntity

/** Notificações de progresso e de conclusão dos downloads. */
class DownloadNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        val system = context.getSystemService(NotificationManager::class.java)
        system.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROGRESS,
                context.getString(R.string.notif_channel_progress),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) },
        )
        system.createNotificationChannel(
            NotificationChannel(
                CHANNEL_FINISHED,
                context.getString(R.string.notif_channel_finished),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    fun foregroundInfo(running: List<DownloadEntity>): ForegroundInfo =
        ForegroundInfo(
            PROGRESS_ID,
            progressNotification(running),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

    fun updateProgress(running: List<DownloadEntity>) {
        if (!canNotify()) return
        @Suppress("MissingPermission")
        manager.notify(PROGRESS_ID, progressNotification(running))
    }

    fun cancelProgress() {
        manager.cancel(PROGRESS_ID)
    }

    fun notifyFinished(task: DownloadEntity, success: Boolean) {
        if (!canNotify()) return
        val title = context.getString(
            if (success) R.string.notif_completed_title else R.string.notif_failed_title,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_FINISHED)
            .setSmallIcon(R.drawable.ic_stat_mova)
            .setColor(ContextCompat.getColor(context, R.color.mova_blue))
            .setContentTitle(title)
            .setContentText(task.fileName)
            .setContentIntent(openApp(if (success) MainActivity.DESTINATION_LIBRARY else MainActivity.DESTINATION_QUEUE))
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        manager.notify(FINISHED_ID_BASE + task.id.toInt(), notification)
    }

    private fun progressNotification(running: List<DownloadEntity>) =
        NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_mova)
            .setColor(ContextCompat.getColor(context, R.color.mova_blue))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openApp(MainActivity.DESTINATION_QUEUE))
            .apply {
                when {
                    running.isEmpty() -> {
                        setContentTitle(context.getString(R.string.notif_preparing))
                        setProgress(0, 0, true)
                    }
                    else -> {
                        val title = if (running.size == 1) {
                            context.getString(R.string.notif_downloading_one, running.single().fileName)
                        } else {
                            context.resources.getQuantityString(
                                R.plurals.notif_downloading_many,
                                running.size,
                                running.size,
                            )
                        }
                        setContentTitle(title)
                        val done = running.sumOf { it.downloadedBytes }
                        val totals = running.map { it.totalBytes }
                        if (totals.all { it != null && it > 0 }) {
                            val total = totals.sumOf { it ?: 0L }
                            val percent = Formatters.percent(done, total) ?: 0
                            setProgress(100, percent, false)
                            setContentText(
                                context.getString(
                                    R.string.notif_progress_detail,
                                    Formatters.bytes(done),
                                    Formatters.bytes(total),
                                ),
                            )
                        } else {
                            setProgress(0, 0, true)
                            setContentText(Formatters.bytes(done))
                        }
                    }
                }
            }
            .build()

    private fun openApp(destination: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_DESTINATION, destination)
        return PendingIntent.getActivity(
            context,
            destination.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun canNotify(): Boolean =
        android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        const val CHANNEL_PROGRESS = "downloads_progress"
        const val CHANNEL_FINISHED = "downloads_finished"
        const val PROGRESS_ID = 1001
        private const val FINISHED_ID_BASE = 20_000
    }
}
