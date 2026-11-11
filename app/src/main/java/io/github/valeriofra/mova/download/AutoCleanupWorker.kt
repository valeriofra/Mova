package io.github.valeriofra.mova.download

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.valeriofra.mova.MovaApplication
import io.github.valeriofra.mova.data.SavedFiles
import java.util.concurrent.TimeUnit

/** Roda a exclusão automática uma vez por dia, mesmo com o app fechado. */
class AutoCleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as MovaApplication).container
        val policy = container.settings.current().autoDelete
        if (policy.enabled) container.downloads.deleteOlderThan(policy.days, container.savedFiles)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "mova-auto-cleanup"

        /** Liga ou desliga a execução diária. */
        fun schedule(context: Context, enabled: Boolean) {
            val workManager = WorkManager.getInstance(context)
            if (!enabled) {
                workManager.cancelUniqueWork(UNIQUE_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<AutoCleanupWorker>(1, TimeUnit.DAYS).build()
            workManager.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/** [SavedFiles] sobre o [FilePublisher]. */
class PublisherSavedFiles(private val publisher: FilePublisher) : SavedFiles {
    override fun delete(uri: String): Boolean = publisher.delete(Uri.parse(uri))

    override fun exists(uri: String): Boolean = publisher.exists(Uri.parse(uri))
}
