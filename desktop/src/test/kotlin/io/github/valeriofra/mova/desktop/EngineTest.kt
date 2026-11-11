package io.github.valeriofra.mova.desktop

import io.github.valeriofra.mova.core.analysis.AudioFormat
import io.github.valeriofra.mova.core.analysis.FormatChoice
import io.github.valeriofra.mova.core.extractor.ExtractionFailure
import io.github.valeriofra.mova.core.extractor.ExtractionResult
import io.github.valeriofra.mova.core.extractor.YtDlp
import io.github.valeriofra.mova.desktop.engine.ComponentId
import io.github.valeriofra.mova.desktop.engine.ComponentInstallException
import io.github.valeriofra.mova.desktop.engine.ComponentInstaller
import io.github.valeriofra.mova.desktop.engine.Components
import io.github.valeriofra.mova.desktop.engine.DesktopEngine
import io.github.valeriofra.mova.desktop.engine.EngineDownloadException
import io.github.valeriofra.mova.desktop.engine.EngineState
import io.github.valeriofra.mova.desktop.engine.ProcessRunner
import io.github.valeriofra.mova.desktop.system.OsInfo
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test

class ComponentsTest {

    @Test
    fun windowsUsesExeAndSharedFfmpeg() {
        val specs = Components.specsFor(OsInfo(OsInfo.Family.WINDOWS, OsInfo.Arch.X64))
        assertEquals(listOf(ComponentId.YT_DLP, ComponentId.FFMPEG, ComponentId.JS_RUNTIME), specs.map { it.id })
        assertTrue(specs[0].url.endsWith("/yt-dlp.exe"))
        assertTrue(specs[1].url.endsWith("ffmpeg-master-latest-win64-gpl-shared.zip"))
        assertTrue(specs[1].keep("ffmpeg.exe") && specs[1].keep("avcodec-62.dll") && !specs[1].keep("ffplay.exe"))
        assertTrue(specs[2].url.endsWith("qjs-windows-x86_64.exe"))
        assertNull(specs[2].checksumsUrl)
    }

    @Test
    fun linuxPicksArchitecture() {
        val x64 = Components.specsFor(OsInfo(OsInfo.Family.LINUX, OsInfo.Arch.X64))
        val arm = Components.specsFor(OsInfo(OsInfo.Family.LINUX, OsInfo.Arch.ARM64))
        assertTrue(x64[0].url.endsWith("/yt-dlp_linux"))
        assertTrue(arm[0].url.endsWith("/yt-dlp_linux_aarch64"))
        assertTrue(arm[1].url.endsWith("linuxarm64-gpl.tar.xz"))
        assertTrue(arm[2].url.endsWith("qjs-linux-aarch64"))
    }

    @Test
    fun readsChecksumFiles() {
        val sums = """
            21f87ead735de22248806d9a55202eefbff98da25ed3a054ee03ae55878920ac  yt-dlp
            85C8D440422108BEE7ECD11E584D56CC04FB3BD1A0B3200BDC8A66D5C5A985FC *yt-dlp.exe
        """.trimIndent()
        assertEquals("85c8d440422108bee7ecd11e584d56cc04fb3bd1a0b3200bdc8a66d5c5a985fc", Components.checksumFor(sums, "yt-dlp.exe"))
        assertEquals("21f87ead735de22248806d9a55202eefbff98da25ed3a054ee03ae55878920ac", Components.checksumFor(sums, "yt-dlp"))
        assertNull(Components.checksumFor(sums, "yt-dlp_linux"))
    }

    @Test
    fun detectsOs() {
        assertEquals(OsInfo(OsInfo.Family.WINDOWS, OsInfo.Arch.X64), OsInfo.detect("Windows 11", "amd64"))
        assertEquals(OsInfo(OsInfo.Family.LINUX, OsInfo.Arch.ARM64), OsInfo.detect("Linux", "aarch64"))
    }
}

class ComponentInstallerTest {
    private val server = MockWebServer()
    private lateinit var dir: File

    @Before
    fun setUp() {
        server.start()
        dir = Files.createTempDirectory("mova-engine").toFile()
    }

    @After
    fun tearDown() {
        server.close()
        dir.deleteRecursively()
    }

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun installsZipFlatAndVerifiesChecksum() = runTest {
        val archive = zip("ffmpeg/bin/ffmpeg.exe" to "ff", "ffmpeg/bin/ffplay.exe" to "no", "ffmpeg/bin/avutil.dll" to "dll", "../evil.dll" to "x")
        server.enqueue(MockResponse.Builder().body("${sha256(archive)}  pack.zip\n").build())
        server.enqueue(MockResponse.Builder().body(Buffer().write(archive)).build())
        val spec = Components.specsFor(OsInfo(OsInfo.Family.WINDOWS, OsInfo.Arch.X64))[1]
            .copy(url = server.url("/pack.zip").toString(), checksumsUrl = server.url("/sums").toString())
        var reported = 0L
        ComponentInstaller(OkHttpClient(), dir).install(spec) { done, _ -> reported = done }

        val installed = File(dir, "ffmpeg").list()!!.sorted()
        assertEquals(listOf("avutil.dll", "evil.dll", "ffmpeg.exe"), installed)
        assertFalse(File(dir.parentFile, "evil.dll").exists())
        assertEquals(archive.size.toLong(), reported)
    }

    @Test
    fun rejectsWrongChecksum() = runTest {
        server.enqueue(MockResponse.Builder().body("${"0".repeat(64)}  yt-dlp\n").build())
        server.enqueue(MockResponse.Builder().body("binary").build())
        val spec = Components.specsFor(OsInfo(OsInfo.Family.LINUX, OsInfo.Arch.X64))[0]
            .copy(url = server.url("/yt-dlp").toString(), checksumsUrl = server.url("/sums").toString())
        val error = runCatching { ComponentInstaller(OkHttpClient(), dir).install(spec) { _, _ -> } }.exceptionOrNull()
        assertTrue(error is ComponentInstallException)
        assertFalse(File(dir, "yt-dlp").exists())
    }
}

/**
 * O mecanismo com um yt-dlp de mentira (script de shell): confere argumentos,
 * leitura do JSON, progresso, arquivo final e erros, sem acessar a internet.
 */
class DesktopEngineTest {
    private lateinit var dir: File
    private val os = OsInfo(OsInfo.Family.LINUX, OsInfo.Arch.X64)

    @Before
    fun setUp() {
        assumeFalse("Usa scripts de shell", OsInfo.current.isWindows)
        dir = Files.createTempDirectory("mova-engine").toFile()
        // Componentes "instalados": o engine não tenta baixar nada.
        File(dir, "ffmpeg").mkdirs()
        File(dir, "ffmpeg/ffmpeg").writeText("")
        File(dir, "qjs").writeText("")
    }

    @After
    fun tearDown() {
        if (::dir.isInitialized) dir.deleteRecursively()
    }

    private fun fakeYtDlp(script: String) {
        val file = File(dir, "yt-dlp")
        file.writeText("#!/bin/sh\n" + script)
        file.setExecutable(true)
    }

    private fun engine() = DesktopEngine(
        engineDir = dir,
        installer = ComponentInstaller(OkHttpClient(), dir),
        os = os,
        lastUpdate = { System.currentTimeMillis() },
        saveLastUpdate = {},
        systemFfmpeg = { null },
    )

    @Test
    fun analyzesWithJsonAndPassesEngineOptions() = runTest {
        val args = File(dir, "args.txt")
        fakeYtDlp(
            """
            if [ "${'$'}1" = "--version" ]; then echo 2026.11.10.000000; exit 0; fi
            echo "${'$'}@" > ${args.absolutePath}
            echo '{"id":"abc","title":"Pôr do sol","webpage_url":"https://youtu.be/abc","extractor":"youtube","duration":61,"formats":[{"format_id":"137","height":1080,"width":1920,"vcodec":"avc1","acodec":"none","filesize":9000000},{"format_id":"140","vcodec":"none","acodec":"mp4a","filesize":500000}]}'
            """.trimIndent(),
        )
        val engine = engine()
        val result = engine.extract("https://youtu.be/abc", allowPlaylist = false)
        assertTrue(result.toString(), result is ExtractionResult.Single)
        assertEquals("Pôr do sol", (result as ExtractionResult.Single).media.title)
        val passed = args.readText()
        assertTrue(passed, passed.contains("--ffmpeg-location ${File(dir, "ffmpeg").absolutePath}"))
        assertTrue(passed, passed.contains("--js-runtimes quickjs:${File(dir, "qjs").absolutePath}"))
        assertTrue(passed, passed.contains("--dump-single-json"))
        assertEquals(EngineState.Ready("2026.11.10.000000", systemFfmpeg = false), engine.state.value)
    }

    @Test
    fun downloadReportsProgressAndReturnsFinalFile() = runTest {
        fakeYtDlp(
            """
            if [ "${'$'}1" = "--version" ]; then echo 2026.11.10; exit 0; fi
            out=""
            while [ "${'$'}#" -gt 0 ]; do if [ "${'$'}1" = "-o" ]; then out="${'$'}2"; fi; shift; done
            folder=${'$'}(dirname "${'$'}out")
            echo "[download]  10.0% of ~  20.00MiB at  2.00MiB/s ETA 00:09"
            echo "[download]  55.5% of ~  20.00MiB at  4.00MiB/s ETA 00:03"
            echo "[download] 100% of   20.00MiB in 00:00:05"
            printf 'v' > "${'$'}folder/Video.f137.mp4"
            printf 'video final' > "${'$'}folder/Video.mp4"
            """.trimIndent(),
        )
        val work = File(dir, "work/1")
        val progress = mutableListOf<YtDlp.Progress>()
        val file = engine().download("https://youtu.be/abc", FormatChoice.Audio(AudioFormat.MP3), null, "Video", null, work) { progress += it }
        assertEquals("Video.mp4", file.name)
        assertEquals(listOf(10f, 55.5f, 100f), progress.map { it.percent })
        assertEquals(20L * 1024 * 1024, progress.first().totalBytes)
    }

    @Test
    fun downloadFailureIsClassified() = runTest {
        fakeYtDlp(
            """
            if [ "${'$'}1" = "--version" ]; then echo 2026.11.10; exit 0; fi
            echo "ERROR: [youtube] abc: Private video. Sign in if you've been granted access" >&2
            exit 1
            """.trimIndent(),
        )
        val error = runCatching {
            engine().download("https://youtu.be/abc", FormatChoice.Video(720), null, "Video", null, File(dir, "work/2")) {}
        }.exceptionOrNull()
        assertTrue(error.toString(), error is EngineDownloadException)
        assertEquals(ExtractionFailure.PRIVATE, (error as EngineDownloadException).reason)
        assertTrue(error.message!!.startsWith("[youtube] abc: Private video"))
    }

    @Test
    fun cancellingStopsTheProcess() = kotlinx.coroutines.runBlocking {
        fakeYtDlp("sleep 30\n")
        val start = System.nanoTime()
        withTimeout(10_000) {
            val job = async { ProcessRunner().run(listOf(File(dir, "yt-dlp").absolutePath)) }
            delay(300)
            job.cancel()
            runCatching { job.await() }
        }
        assertTrue((System.nanoTime() - start) / 1_000_000 < 9_000)
    }

    @Test
    fun picksResultFileIgnoringIntermediates() {
        val work = File(dir, "pick").also { it.mkdirs() }
        File(work, "a.f137.mp4").writeText("x".repeat(100))
        File(work, "a.mp4.part").writeText("x".repeat(200))
        File(work, "a.mp4").writeText("x".repeat(50))
        assertEquals("a.mp4", DesktopEngine.resultFile(work)?.name)
    }
}
