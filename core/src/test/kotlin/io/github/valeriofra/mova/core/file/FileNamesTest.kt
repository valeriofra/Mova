package io.github.valeriofra.mova.core.file

import io.github.valeriofra.mova.core.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNamesTest {

    @Test
    fun readsContentDisposition() {
        assertEquals("relatório final.pdf", FileNames.fromContentDisposition("attachment; filename*=UTF-8''relat%C3%B3rio%20final.pdf"))
        assertEquals("video 01.mp4", FileNames.fromContentDisposition("attachment; filename=\"video 01.mp4\""))
        assertEquals("musica.mp3", FileNames.fromContentDisposition("inline; filename=musica.mp3"))
    }

    @Test
    fun sanitizesIllegalCharacters() {
        assertEquals("a_b_c_.mp4", FileNames.sanitize("a/b\\c?.mp4"))
        assertEquals("", FileNames.sanitize("..."))
    }

    @Test
    fun limitsLengthKeepingExtension() {
        val long = "x".repeat(300) + ".mp4"
        val result = FileNames.sanitize(long)
        assertTrue(result.length <= 120)
        assertTrue(result.endsWith(".mp4"))
    }

    @Test
    fun choosesNameAndAddsMissingExtension() {
        assertEquals("download.mp4", FileNames.choose(null, "download", "video/mp4", "mova"))
        assertEquals("clipe.webm", FileNames.choose("attachment; filename=\"clipe.webm\"", "x", "video/webm", "mova"))
        assertEquals("mova-site.mp3", FileNames.choose(null, null, "audio/mpeg", "mova-site"))
    }

    @Test
    fun resolvesMimeAndKind() {
        assertEquals("video/mp4", MimeTypes.resolve("application/octet-stream", "a.mp4"))
        assertEquals("audio/mpeg", MimeTypes.resolve("Audio/MPEG; charset=binary", "a.bin"))
        assertEquals(MediaKind.AUDIO, MimeTypes.mediaKindForMime("audio/mp4"))
        assertEquals(MediaKind.IMAGE, MimeTypes.mediaKindForMime("image/webp"))
        assertTrue(MimeTypes.isStreamingManifest("application/vnd.apple.mpegurl", null))
        assertTrue(MimeTypes.isStreamingManifest(null, "m3u8"))
    }
}
