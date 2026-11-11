package io.github.valeriofra.mova.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.valeriofra.mova.MovaApplication
import io.github.valeriofra.mova.download.AutoCleanupWorker
import io.github.valeriofra.mova.ui.analysis.AnalysisViewModel
import io.github.valeriofra.mova.ui.home.HomeViewModel
import io.github.valeriofra.mova.ui.library.LibraryViewModel
import io.github.valeriofra.mova.ui.queue.QueueViewModel
import io.github.valeriofra.mova.ui.settings.SettingsViewModel

/** Cria os ViewModels com as dependências do [io.github.valeriofra.mova.AppContainer]. */
object MovaViewModels {
    val factory: ViewModelProvider.Factory = viewModelFactory {
        initializer { HomeViewModel() }
        initializer {
            val c = movaApplication().container
            AnalysisViewModel(createSavedStateHandle(), c.analyzer, c.downloads, c.settings, c.ytDlp.state)
        }
        initializer {
            val c = movaApplication().container
            QueueViewModel(c.downloads, c.scheduler, c.settings)
        }
        initializer {
            val c = movaApplication().container
            LibraryViewModel(c.downloads, c.publisher, c.settings)
        }
        initializer {
            val c = movaApplication().container
            val app = movaApplication()
            SettingsViewModel(c.settings, c.downloads, c.ytDlp, c.savedFiles) { enabled ->
                AutoCleanupWorker.schedule(app, enabled)
            }
        }
    }

    private fun androidx.lifecycle.viewmodel.CreationExtras.movaApplication(): MovaApplication =
        this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MovaApplication
}
