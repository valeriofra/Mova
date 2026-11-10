package io.github.valeriofra.mova

import android.app.Application
import kotlinx.coroutines.launch

class MovaApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.createChannels()
        // Retoma tarefas que ficaram pendentes (ex.: após reiniciar o aparelho).
        container.applicationScope.launch {
            runCatching { container.downloads.ensureProcessing() }
        }
    }
}
