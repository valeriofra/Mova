package io.github.valeriofra.mova.download

import io.github.valeriofra.mova.core.model.MediaKind
import io.github.valeriofra.mova.core.model.Platform
import io.github.valeriofra.mova.data.db.DownloadEntity
import io.github.valeriofra.mova.data.db.DownloadError
import io.github.valeriofra.mova.data.db.DownloadStatus
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class HttpDownloaderTest {

    private lateinit var server: MockWebServer
    private lateinit var dir: File
    private val client = OkHttpClient()
    private val content = ByteArray(10_000) { (it % 251).toByte() }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        dir = Files.createTempDirectory("mova-test").toFile()
    }

    @After
    fun tearDown() {
        server.close()
        dir.deleteRecursively()
    }

    private fun downloader(free: Long = Long.MAX_VALUE) = HttpDownloader(client, minFreeBytes = 0, availableBytes = { free })

    private fun task(
        total: Long? = content.size.toLong(),
        supportsResume: Boolean = true,
        etag: String? = "\"v1\"",
    ) = DownloadEntity(
        id = 1,
        url = server.url("/video.mp4").toString(),
        sourceUrl = server.url("/video.mp4").toString(),
        platform = Platform.DIRECT,
        fileName = "video.mp4",
        title = "video",
        mimeType = "video/mp4",
        mediaKind = MediaKind.VIDEO,
        totalBytes = total,
        status = DownloadStatus.RUNNING,
        supportsResume = supportsResume,
        etag = etag,
        lastModified = null,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun okBuffer(bytes: ByteArray) = okio.Buffer().write(bytes)

    @Test
    fun downloadsWholeFile() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Accept-Ranges", "bytes")
                .addHeader("ETag", "\"v1\"")
                .body(okBuffer(content))
                .build(),
        )
        val file = File(dir, "1.part")
        var info: SourceInfo? = null
        var lastProgress = 0L
        val size = downloader().download(task(), file, { info = it }, { done, _ -> lastProgress = done })

        assertEquals(content.size.toLong(), size)
        assertArrayEquals(content, file.readBytes())
        assertEquals(content.size.toLong(), info!!.totalBytes)
        assertTrue(info!!.supportsResume)
        assertEquals(content.size.toLong(), lastProgress)
        assertNull(server.takeRequest().headers["Range"])
    }

    @Test
    fun resumesWithRangeWhenPartialFileExists() = runBlocking {
        val file = File(dir, "1.part")
        file.writeBytes(content.copyOfRange(0, 4_000))
        server.enqueue(
            MockResponse.Builder()
                .code(206)
                .addHeader("Content-Range", "bytes 4000-9999/10000")
                .addHeader("ETag", "\"v1\"")
                .body(okBuffer(content.copyOfRange(4_000, content.size)))
                .build(),
        )

        downloader().download(task(), file, {}, { _, _ -> })

        val request = server.takeRequest()
        assertEquals("bytes=4000-", request.headers["Range"])
        assertEquals("\"v1\"", request.headers["If-Range"])
        assertArrayEquals(content, file.readBytes())
    }

    @Test
    fun restartsWhenServerIgnoresRange() = runBlocking {
        val file = File(dir, "1.part")
        file.writeBytes(ByteArray(3_000) { 7 })
        server.enqueue(MockResponse.Builder().code(200).body(okBuffer(content)).build())

        downloader().download(task(), file, {}, { _, _ -> })

        assertArrayEquals(content, file.readBytes())
    }

    @Test
    fun discardsPartialFileWhenResumeIsNotSupported() = runBlocking {
        val file = File(dir, "1.part")
        file.writeBytes(ByteArray(3_000) { 7 })
        server.enqueue(MockResponse.Builder().code(200).body(okBuffer(content)).build())

        downloader().download(task(supportsResume = false), file, {}, { _, _ -> })

        assertNull(server.takeRequest().headers["Range"])
        assertArrayEquals(content, file.readBytes())
    }

    @Test
    fun mapsHttpErrors() = runBlocking {
        val cases = listOf(404 to DownloadError.NOT_FOUND, 403 to DownloadError.ACCESS_DENIED, 503 to DownloadError.SERVER)
        for ((code, expected) in cases) {
            server.enqueue(MockResponse.Builder().code(code).build())
            try {
                downloader().download(task(), File(dir, "$code.part"), {}, { _, _ -> })
                fail("esperava falha para HTTP $code")
            } catch (e: DownloadFailure) {
                assertEquals(expected, e.error)
                assertEquals(code, e.httpCode)
            }
        }
    }

    @Test
    fun failsWhenThereIsNotEnoughSpace() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).body(okBuffer(content)).build())
        try {
            downloader(free = 100).download(task(), File(dir, "1.part"), {}, { _, _ -> })
            fail("esperava falta de espaço")
        } catch (e: DownloadFailure) {
            assertEquals(DownloadError.INSUFFICIENT_STORAGE, e.error)
        }
    }

    @Test
    fun cancellationStopsTransferQuickly() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(okBuffer(content))
                .throttleBody(500, 1, TimeUnit.SECONDS)
                .build(),
        )
        val file = File(dir, "1.part")
        val job = launch(Dispatchers.Default) {
            downloader().download(task(), file, {}, { _, _ -> })
        }
        delay(700)
        val started = System.nanoTime()
        job.cancelAndJoin()
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("cancelamento demorou ${elapsedMs}ms", elapsedMs < 2_000)
        assertTrue("arquivo parcial deve ser mantido para retomar", file.length() in 1 until content.size)
    }
}
