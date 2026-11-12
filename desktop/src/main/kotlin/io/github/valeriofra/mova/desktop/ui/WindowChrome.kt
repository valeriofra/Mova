package io.github.valeriofra.mova.desktop.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import com.jetbrains.JBR
import com.jetbrains.WindowDecorations
import io.github.valeriofra.mova.desktop.data.DesktopSettings
import java.awt.Frame
import java.awt.GraphicsEnvironment

/**
 * Barra de título integrada ao layout (JetBrains Runtime): some a barra branca
 * do Windows e os botões minimizar/maximizar/fechar seguem o tema do app,
 * mantendo o comportamento nativo (arrastar, encaixar, duplo clique).
 * Em runtimes sem suporte, a barra normal do sistema continua.
 */
class IntegratedTitleBar private constructor(
    private val window: Frame,
    private val bar: WindowDecorations.CustomTitleBar,
) {
    val height: Dp get() = HEIGHT.dp

    fun setDark(dark: Boolean) {
        runCatching {
            bar.putProperty("controls.dark", dark)
            JBR.getWindowDecorations()?.setCustomTitleBar(window, bar)
        }
    }

    companion object {
        private const val HEIGHT = 40

        fun install(window: Frame, dark: Boolean): IntegratedTitleBar? = runCatching {
            if (!JBR.isAvailable() || !JBR.isWindowDecorationsSupported()) return null
            val decorations = JBR.getWindowDecorations() ?: return null
            val bar = decorations.createCustomTitleBar()
            bar.height = HEIGHT.toFloat()
            bar.putProperty("controls.dark", dark)
            decorations.setCustomTitleBar(window, bar)
            IntegratedTitleBar(window, bar)
        }.getOrNull()
    }
}

/** Tamanho inicial da janela, adaptado à área útil do monitor (sem a barra de tarefas). */
object WindowGeometry {
    val MIN = DpSize(720.dp, 520.dp)
    private val PREFERRED = DpSize(1240.dp, 820.dp)

    data class Initial(val size: DpSize, val placement: WindowPlacement)

    fun initial(settings: DesktopSettings): Initial {
        val screen = runCatching { GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds }.getOrNull()
        val maxW = screen?.width?.dp ?: PREFERRED.width
        val maxH = screen?.height?.dp ?: PREFERRED.height
        val width = (settings.windowWidth?.dp ?: minOf(PREFERRED.width, maxW * 0.86f)).coerceIn(MIN.width, maxOf(MIN.width, maxW))
        val height = (settings.windowHeight?.dp ?: minOf(PREFERRED.height, maxH * 0.9f)).coerceIn(MIN.height, maxOf(MIN.height, maxH))
        // Monitores pequenos: já abre maximizada.
        val maximized = settings.windowMaximized ?: (maxW < 1100.dp || maxH < 700.dp)
        return Initial(DpSize(width, height), if (maximized) WindowPlacement.Maximized else WindowPlacement.Floating)
    }
}
