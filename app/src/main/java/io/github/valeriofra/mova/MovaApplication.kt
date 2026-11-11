package io.github.valeriofra.mova

import android.app.Application
import io.github.valeriofra.mova.download.AutoCleanupWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MovaApplication : Application() {

    private companion object {
        const val WARM_UP_DELAY_MS = 3_000L
    }

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        container = AppContainer(this)
        container.notifier.createChannels()
        // Retoma tarefas que ficaram pendentes (ex.: após reiniciar o aparelho).
        container.applicationScope.launch {
            runCatching { container.downloads.ensureProcessing() }
        }
        // Exclusão automática: roda ao abrir o app e fica agendada 1x por dia.
        container.applicationScope.launch {
            runCatching {
                val policy = container.settings.current().autoDelete
                AutoCleanupWorker.schedule(this@MovaApplication, policy.enabled)
                if (policy.enabled) container.downloads.deleteOlderThan(policy.days, container.savedFiles)
            }
        }
        // Prepara o mecanismo de download e mantém o yt-dlp atualizado (1x por dia).
        container.applicationScope.launch {
            delay(WARM_UP_DELAY_MS)
            runCatching { container.ytDlp.warmUp() }
        }
    }
}
