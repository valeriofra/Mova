package io.github.valeriofra.mova.download

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OkHttpProbeTest {

    private lateinit var server: MockWebServer
    private val probe = OkHttpProbe(OkHttpClient())

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun usesHeadWhenServerSupportsIt() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "audio/mpeg")
                .addHeader("Content-Disposition", "attachment; filename=\"faixa.mp3\"")
                .addHeader("Accept-Ranges", "bytes")
                .build(),
        )
        val result = probe.probe(server.url("/download").toString())

        assertEquals("HEAD", server.takeRequest().method)
        assertEquals(200, result.httpCode)
        assertEquals("audio/mpeg", result.contentType)
        assertTrue(result.acceptsRanges)
        assertEquals("attachment; filename=\"faixa.mp3\"", result.contentDisposition)
    }

    @Test
    fun fallsBackToRangedGetWhenHeadIsRejected() = runBlocking {
        server.enqueue(MockResponse.Builder().code(405).build())
        server.enqueue(
            MockResponse.Builder()
                .code(206)
                .addHeader("Content-Type", "video/mp4")
                .addHeader("Content-Range", "bytes 0-0/123456")
                .body("x")
                .build(),
        )
        val result = probe.probe(server.url("/video.mp4").toString())

        assertEquals("HEAD", server.takeRequest().method)
        val get = server.takeRequest()
        assertEquals("GET", get.method)
        assertEquals("bytes=0-0", get.headers["Range"])
        assertEquals(206, result.httpCode)
        assertEquals(123456L, result.contentLength)
        assertTrue(result.acceptsRanges)
    }

    @Test
    fun followsRedirectsAndReportsFinalUrl() = runBlocking {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/final.mp4").build())
        server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "video/mp4").build())
        val result = probe.probe(server.url("/curto").toString())
        assertTrue(result.finalUrl.endsWith("/final.mp4"))
    }
}
