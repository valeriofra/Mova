package io.github.valeriofra.mova.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.valeriofra.mova.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Estado do processador da fila, usado para explicar por que nada está baixando. */
enum class QueueRunState { IDLE, RUNNING, WAITING_FOR_NETWORK }

/**
 * Agenda o processamento da fila no WorkManager. Há um único trabalho com nome
 * fixo; novos pedidos são encadeados, de modo que nunca há dois processadores
 * ao mesmo tempo.
 */
class DownloadScheduler(
    private val context: Context,
    private val settings: SettingsRepository,
) {
    private val workManager get() = WorkManager.getInstance(context)

    suspend fun schedule() {
        val wifiOnly = settings.current().wifiOnly
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<DownloadQueueWorker>()
            .setConstraints(constraints)
            .addTag(TAG)
            .build()

        // Se o trabalho atual só está esperando rede, ele pode ser substituído com
        // segurança (ex.: o usuário desligou "Somente Wi-Fi"). Se estiver rodando,
        // o novo pedido é encadeado para depois dele.
        val current = withContext(Dispatchers.IO) { workManager.getWorkInfosForUniqueWork(UNIQUE_NAME).get() }
        val running = current.any { it.state == WorkInfo.State.RUNNING }
        val policy = if (running) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
        workManager.enqueueUniqueWork(UNIQUE_NAME, policy, request)
    }

    fun observeState(): Flow<QueueRunState> =
        workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_NAME).map { infos ->
            when {
                infos.any { it.state == WorkInfo.State.RUNNING } -> QueueRunState.RUNNING
                infos.any { it.state == WorkInfo.State.ENQUEUED } -> QueueRunState.WAITING_FOR_NETWORK
                else -> QueueRunState.IDLE
            }
        }

    companion object {
        const val UNIQUE_NAME = "mova-download-queue"
        const val TAG = "mova-download"
    }
}
