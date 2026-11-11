package io.github.valeriofra.mova.core.extractor

import io.github.valeriofra.mova.core.analysis.AudioFormat
import io.github.valeriofra.mova.core.analysis.FormatChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpTest {

    private val video = """
        {
          "_type": "video", "id": "abc123", "title": "Meu vídeo", "uploader": "Canal",
          "duration": 204.5, "webpage_url": "https://www.youtube.com/watch?v=abc123",
          "extractor_key": "Youtube", "thumbnail": "https://i.ytimg.com/vi/abc123/hq.jpg",
          "formats": [
            {"format_id": "140", "vcodec": "none", "acodec": "mp4a.40.2", "filesize": 3000000},
            {"format_id": "251", "vcodec": "none", "acodec": "opus", "filesize_approx": 2800000},
            {"format_id": "137", "vcodec": "avc1", "acodec": "none", "width": 1920, "height": 1080, "filesize": 40000000},
            {"format_id": "136", "vcodec": "avc1", "acodec": "none", "width": 1280, "height": 720, "filesize": 20000000},
            {"format_id": "313", "vcodec": "vp9", "acodec": "none", "width": 3840, "height": 2160, "filesize": 150000000},
            {"format_id": "18", "vcodec": "avc1", "acodec": "mp4a", "width": 640, "height": 360, "filesize": 9000000},
            {"format_id": "sb0", "vcodec": "none", "acodec": "none", "ext": "mhtml"}
          ]
        }
    """.trimIndent()

    @Test
    fun parsesSingleVideoWithResolutionsAndSizes() {
        val result = YtDlp.parse(video) as ExtractionResult.Single
        val media = result.media
        assertEquals("Meu vídeo", media.title)
        assertEquals("Canal", media.uploader)
        assertEquals(204L, media.durationSeconds)
        assertEquals(listOf(2160, 1080, 720, 360), media.resolutions)
        assertTrue(media.hasVideo)
        assertTrue(media.hasAudio)
        assertEquals(3_000_000L, media.audioSizeBytes)
        // 1080p sem áudio próprio: soma o melhor áudio.
        assertEquals(43_000_000L, media.sizeByResolution[1080])
        // 360p já tem áudio embutido.
        assertEquals(9_000_000L, media.sizeByResolution[360])
        assertEquals("https://i.ytimg.com/vi/abc123/hq.jpg", media.thumbnailUrl)
    }

    @Test
    fun verticalVideoUsesShortestSide() {
        val tiktok = """{"title":"Clip","formats":[{"vcodec":"h264","acodec":"aac","width":1080,"height":1920}]}"""
        val media = (YtDlp.parse(tiktok) as ExtractionResult.Single).media
        assertEquals(listOf(1080), media.resolutions)
    }

    @Test
    fun parsesFlatPlaylist() {
        val playlist = """
            {"_type": "playlist", "title": "Minha playlist", "uploader": "Canal",
             "webpage_url": "https://www.youtube.com/playlist?list=PL1",
             "entries": [
               {"_type": "url", "url": "https://www.youtube.com/watch?v=a", "title": "Primeiro", "duration": 60},
               {"_type": "url", "url": "b", "title": "Segundo", "thumbnails": [{"url": "https://x/1.jpg"}, {"url": "https://x/2.jpg"}]}
             ]}
        """.trimIndent()
        val result = YtDlp.parse(playlist) as ExtractionResult.Collection
        assertEquals("Minha playlist", result.title)
        assertEquals(2, result.entries.size)
        assertEquals("https://www.youtube.com/watch?v=a", result.entries[0].url)
        assertEquals(60L, result.entries[0].durationSeconds)
        // URL que não é http: o item será baixado pela posição na playlist.
        assertNull(result.entries[1].url)
        assertEquals(2, result.entries[1].index)
        assertEquals("https://x/2.jpg", result.entries[1].thumbnailUrl)
    }

    @Test
    fun singleFullEntryIsTreatedAsSingleMedia() {
        val carousel = """{"_type":"playlist","entries":[{"title":"Reel","formats":[{"vcodec":"h264","acodec":"aac","height":720,"width":720}]}]}"""
        assertTrue(YtDlp.parse(carousel) is ExtractionResult.Single)
    }

    @Test
    fun invalidOutputIsFailure() {
        assertTrue(YtDlp.parse("not json") is ExtractionResult.Failure)
    }

    @Test
    fun parsesProgressLines() {
        val p = YtDlp.parseProgress("[download]  45.3% of ~  12.50MiB at    1.25MiB/s ETA 00:10")!!
        assertEquals(45.3f, p.percent, 0.01f)
        assertEquals((12.5 * 1024 * 1024).toLong(), p.totalBytes)
        assertEquals((1.25 * 1024 * 1024).toLong(), p.bytesPerSecond)

        val done = YtDlp.parseProgress("[download] 100% of   20.00KiB in 00:00:01 at 15.0KiB/s")!!
        assertEquals(100f, done.percent, 0.01f)
        assertEquals(20L * 1024, done.totalBytes)
        assertNull(YtDlp.parseProgress("[Merger] Merging formats into \"x.mp4\""))
    }

    @Test
    fun classifiesErrors() {
        assertEquals(ExtractionFailure.UNSUPPORTED, YtDlp.classifyError("ERROR: Unsupported URL: https://x"))
        assertEquals(ExtractionFailure.PRIVATE, YtDlp.classifyError("ERROR: [youtube] x: Private video. Sign in if you've been granted access"))
        assertEquals(
            ExtractionFailure.LOGIN_REQUIRED,
            YtDlp.classifyError("ERROR: [Instagram] x: Requested content is not available, rate-limit reached or login required. Use --cookies"),
        )
        assertEquals(ExtractionFailure.DRM, YtDlp.classifyError("ERROR: This video is DRM protected"))
        assertEquals(ExtractionFailure.UNAVAILABLE, YtDlp.classifyError("ERROR: [youtube] x: Video unavailable"))
        assertEquals(ExtractionFailure.NETWORK, YtDlp.classifyError("ERROR: Unable to download webpage: timed out"))
        assertEquals(ExtractionFailure.UNKNOWN, YtDlp.classifyError("algo estranho"))
    }

    @Test
    fun buildsDownloadArguments() {
        val video = YtDlp.downloadArgs(FormatChoice.Video(720), "/tmp/x", playlistIndex = null)
        assertTrue(video.containsAll(listOf("--no-playlist", "--merge-output-format", "mp4")))
        assertEquals("res:720,vcodec:h264,acodec:aac,ext:mp4:m4a", video[video.indexOf("-S") + 1])
        assertEquals("/tmp/x/%(title).90B.%(ext)s", video[video.indexOf("-o") + 1])

        val audio = YtDlp.downloadArgs(FormatChoice.Audio(AudioFormat.MP3), "/tmp/x", playlistIndex = 3)
        assertTrue(audio.contains("-x"))
        assertEquals("mp3", audio[audio.indexOf("--audio-format") + 1])
        assertEquals("3", audio[audio.indexOf("--playlist-items") + 1])
        assertFalse(audio.contains("--no-playlist"))
    }

    @Test
    fun formatChoiceRoundTrip() {
        listOf(
            FormatChoice.Original,
            FormatChoice.Video(1080),
            FormatChoice.Video(480),
            FormatChoice.VideoOnly(720),
            FormatChoice.Audio(AudioFormat.M4A),
        )
            .forEach { assertEquals(it, FormatChoice.decode(it.encode())) }
        assertEquals(FormatChoice.Original, FormatChoice.decode(null))
        // Nunca acima do limite do planejamento.
        assertEquals(FormatChoice.Video(1080), FormatChoice.decode("video:2160"))
    }

    @Test
    fun videoWithoutAudioArguments() {
        val args = YtDlp.downloadArgs(FormatChoice.VideoOnly(720), "/tmp/x", playlistIndex = null)
        assertEquals("bv/b", args[args.indexOf("-f") + 1])
        assertEquals("res:720,vcodec:h264,ext:mp4:m4a", args[args.indexOf("-S") + 1])
        assertEquals("FFmpegCopyStream", args[args.indexOf("--use-postprocessor") + 1])
        assertEquals("CopyStream:-an", args[args.indexOf("--ppa") + 1])
        assertFalse(args.contains("-x"))
    }
}
