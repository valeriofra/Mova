package io.github.valeriofra.mova.desktop

import io.github.valeriofra.mova.desktop.engine.ComponentInstaller
import io.github.valeriofra.mova.desktop.engine.Components
import io.github.valeriofra.mova.desktop.engine.DesktopEngine
import io.github.valeriofra.mova.desktop.engine.EngineState
import io.github.valeriofra.mova.desktop.engine.ProcessRunner
import io.github.valeriofra.mova.desktop.net.Http
import io.github.valeriofra.mova.desktop.system.OsInfo
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Baixa os componentes REAIS (yt-dlp, FFmpeg, QuickJS) e executa cada um.
 * Roda só no CI (MOVA_INTEGRATION=1), no Windows e no Linux, para garantir que
 * os endereços, as somas SHA-256 e a extração continuam funcionando.
 */
class EngineIntegrationTest {

    @Test
    fun installsAndRunsRealComponents() = runBlocking {
        assumeTrue("Só no CI", System.getenv("MOVA_INTEGRATION") == "1")
        val dir = Files.createTempDirectory("mova-engine-real").toFile()
        try {
            val os = OsInfo.current
            val engine = DesktopEngine(
                engineDir = dir,
                installer = ComponentInstaller(Http.client(), dir),
                os = os,
                lastUpdate = { System.currentTimeMillis() },
                saveLastUpdate = {},
                systemFfmpeg = { null },
            )
            engine.ensureReady()
            val state = engine.state.value
            assertTrue("Estado: $state", state is EngineState.Ready && state.version != null)

            val layout = Components.Layout(dir, os)
            val runner = ProcessRunner()
            // Guarda a saída inteira: "ffmpeg version ..." é a primeira linha.
            val ffmpeg = runner.run(listOf(layout.ffmpeg.absolutePath, "-version"), keepLines = null)
            assertTrue("FFmpeg: ${ffmpeg.combined}", ffmpeg.ok && ffmpeg.stdout.contains("ffmpeg version"))
            val probe = File(layout.ffmpegDir, "ffprobe${os.executableSuffix}")
            assertTrue("ffprobe ausente", probe.isFile)

            val script = File(dir, "teste.js").apply { writeText("console.log('quickjs ' + (40 + 2));") }
            val js = runner.run(listOf(layout.jsRuntime.absolutePath, script.absolutePath), keepLines = 5)
            assertTrue("QuickJS: ${js.combined}", js.ok && js.stdout.contains("quickjs 42"))

            println("Componentes OK: yt-dlp ${(state as EngineState.Ready).version}; ${ffmpeg.stdout.lineSequence().first()}")
        } finally {
            dir.deleteRecursively()
        }
    }
}
