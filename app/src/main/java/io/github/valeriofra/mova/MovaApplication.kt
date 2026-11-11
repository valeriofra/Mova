package io.github.valeriofra.mova

import android.app.Application
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
        // Prepara o mecanismo de download e mantém o yt-dlp atualizado (1x por dia).
        container.applicationScope.launch {
            delay(WARM_UP_DELAY_MS)
            runCatching { container.ytDlp.warmUp() }
        }
    }
}
