package io.github.valeriofra.mova

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.valeriofra.mova.ui.MovaApp
import io.github.valeriofra.mova.ui.theme.MovaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Evento vindo de fora da interface (compartilhamento ou notificação). */
sealed interface IncomingEvent {
    val id: Long

    /** Texto recebido pelo menu Compartilhar. Preenche o campo, sem iniciar o download. */
    data class SharedText(val text: String, override val id: Long = System.nanoTime()) : IncomingEvent

    /** Abrir uma área específica (ex.: ao tocar numa notificação). */
    data class OpenDestination(val destination: String, override val id: Long = System.nanoTime()) : IncomingEvent
}

class MainActivity : ComponentActivity() {

    private val incoming = MutableStateFlow<IncomingEvent?>(null)
    private val incomingEvents = incoming.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            MovaTheme {
                MovaApp(
                    incomingEvents = incomingEvents,
                    onEventConsumed = { consumed ->
                        if (incoming.value?.id == consumed.id) incoming.value = null
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        if (intent.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
            if (!text.isNullOrBlank()) incoming.value = IncomingEvent.SharedText(text)
            return
        }
        intent.getStringExtra(EXTRA_DESTINATION)?.let { incoming.value = IncomingEvent.OpenDestination(it) }
    }

    companion object {
        const val EXTRA_DESTINATION = "io.github.valeriofra.mova.DESTINATION"
        const val DESTINATION_QUEUE = "queue"
        const val DESTINATION_LIBRARY = "library"
    }
}
