package io.github.valeriofra.mova.core.link

import io.github.valeriofra.mova.core.model.ContentKind
import io.github.valeriofra.mova.core.model.MediaKind
import io.github.valeriofra.mova.core.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkRecognizerTest {

    private val recognizer = LinkRecognizer()

    private fun assertLink(url: String, platform: Platform, kind: ContentKind, id: String? = null) {
        val link = recognizer.recognize(url)
            ?: throw AssertionError("URL não reconhecida como válida: $url")
        assertEquals("plataforma de $url", platform, link.platform)
        assertEquals("tipo de $url", kind, link.kind)
        if (id != null) assertEquals("id de $url", id, link.contentId)
    }

    @Test
    fun youtube() {
        assertLink("https://www.youtube.com/watch?v=dQw4w9WgXcQ", Platform.YOUTUBE, ContentKind.VIDEO, "dQw4w9WgXcQ")
        assertLink("https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=10s", Platform.YOUTUBE, ContentKind.VIDEO, "dQw4w9WgXcQ")
        assertLink("https://youtu.be/dQw4w9WgXcQ?si=abc", Platform.YOUTUBE, ContentKind.VIDEO, "dQw4w9WgXcQ")
        assertLink("https://youtube.com/shorts/abcdefghijk", Platform.YOUTUBE, ContentKind.SHORT_VIDEO, "abcdefghijk")
        assertLink("https://www.youtube.com/playlist?list=PL123", Platform.YOUTUBE, ContentKind.PLAYLIST, "PL123")
        assertLink("https://www.youtube.com/live/abcdefghijk", Platform.YOUTUBE, ContentKind.LIVE)
        assertLink("https://www.youtube.com/@canal", Platform.YOUTUBE, ContentKind.PROFILE)
        assertLink("https://music.youtube.com/watch?v=dQw4w9WgXcQ", Platform.YOUTUBE, ContentKind.VIDEO)
        assertLink("https://www.youtube.com/embed/dQw4w9WgXcQ", Platform.YOUTUBE, ContentKind.VIDEO)
        assertLink("https://www.youtube.com/", Platform.YOUTUBE, ContentKind.UNKNOWN)

        val withList = recognizer.recognize("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLxyz")!!
        assertEquals("PLxyz", withList.collectionId)
    }

    @Test
    fun instagram() {
        assertLink("https://www.instagram.com/p/C1a2b3c4d5/", Platform.INSTAGRAM, ContentKind.POST, "C1a2b3c4d5")
        assertLink("https://www.instagram.com/reel/C1a2b3c4d5/?igsh=xyz", Platform.INSTAGRAM, ContentKind.SHORT_VIDEO)
        assertLink("https://instagram.com/stories/usuario/3141592653589/", Platform.INSTAGRAM, ContentKind.STORY)
        assertLink("https://www.instagram.com/stories/highlights/1789/", Platform.INSTAGRAM, ContentKind.STORY_HIGHLIGHT)
        assertLink("https://www.instagram.com/usuario.teste/", Platform.INSTAGRAM, ContentKind.PROFILE)
        assertLink("https://www.instagram.com/usuario/p/C1a2b3c4d5/", Platform.INSTAGRAM, ContentKind.POST)
        assertLink("https://www.instagram.com/explore/", Platform.INSTAGRAM, ContentKind.UNKNOWN)
    }

    @Test
    fun tiktok() {
        assertLink("https://www.tiktok.com/@usuario/video/7234567890123456789", Platform.TIKTOK, ContentKind.VIDEO, "7234567890123456789")
        assertLink("https://www.tiktok.com/@usuario/photo/7234567890123456789", Platform.TIKTOK, ContentKind.POST)
        assertLink("https://vm.tiktok.com/ZMabc123/", Platform.TIKTOK, ContentKind.SHORT_LINK)
        assertLink("https://vt.tiktok.com/ZSabc123/", Platform.TIKTOK, ContentKind.SHORT_LINK)
        assertLink("https://www.tiktok.com/t/ZTabc123/", Platform.TIKTOK, ContentKind.SHORT_LINK)
        assertLink("https://www.tiktok.com/@usuario", Platform.TIKTOK, ContentKind.PROFILE)
    }

    @Test
    fun facebook() {
        assertLink("https://www.facebook.com/watch/?v=1234567890", Platform.FACEBOOK, ContentKind.VIDEO, "1234567890")
        assertLink("https://www.facebook.com/pagina/videos/1234567890/", Platform.FACEBOOK, ContentKind.VIDEO, "1234567890")
        assertLink("https://fb.watch/abcDEF/", Platform.FACEBOOK, ContentKind.VIDEO)
        assertLink("https://www.facebook.com/reel/1234567890", Platform.FACEBOOK, ContentKind.SHORT_VIDEO)
        assertLink("https://www.facebook.com/share/v/AbC123/", Platform.FACEBOOK, ContentKind.VIDEO)
        assertLink("https://www.facebook.com/share/r/AbC123/", Platform.FACEBOOK, ContentKind.SHORT_VIDEO)
        assertLink("https://m.facebook.com/stories/123/456", Platform.FACEBOOK, ContentKind.STORY)
        assertLink("https://www.facebook.com/pagina/posts/pfbid0abc", Platform.FACEBOOK, ContentKind.POST)
        assertLink("https://www.facebook.com/pagina", Platform.FACEBOOK, ContentKind.PROFILE)
    }

    @Test
    fun kwai() {
        assertLink("https://www.kwai.com/@usuario/video/5221234567890", Platform.KWAI, ContentKind.VIDEO, "5221234567890")
        assertLink("https://kw.ai/p/AbCdEf", Platform.KWAI, ContentKind.SHORT_LINK)
        assertLink("https://www.kwai.com/@usuario", Platform.KWAI, ContentKind.PROFILE)
    }

    @Test
    fun pinterest() {
        assertLink("https://br.pinterest.com/pin/1234567890/", Platform.PINTEREST, ContentKind.POST, "1234567890")
        assertLink("https://www.pinterest.com.br/pin/1234567890/", Platform.PINTEREST, ContentKind.POST)
        assertLink("https://pin.it/AbCdEf", Platform.PINTEREST, ContentKind.SHORT_LINK)
        assertLink("https://www.pinterest.com/usuario/receitas/", Platform.PINTEREST, ContentKind.COLLECTION)
        assertLink("https://www.pinterest.com/usuario/", Platform.PINTEREST, ContentKind.PROFILE)
    }

    @Test
    fun vimeo() {
        assertLink("https://vimeo.com/123456789", Platform.VIMEO, ContentKind.VIDEO, "123456789")
        assertLink("https://vimeo.com/123456789/abcdef1234", Platform.VIMEO, ContentKind.VIDEO, "123456789")
        assertLink("https://player.vimeo.com/video/123456789", Platform.VIMEO, ContentKind.VIDEO, "123456789")
        assertLink("https://vimeo.com/channels/staffpicks/123456789", Platform.VIMEO, ContentKind.VIDEO)
        assertLink("https://vimeo.com/showcase/987654", Platform.VIMEO, ContentKind.COLLECTION)
        assertLink("https://vimeo.com/usuario", Platform.VIMEO, ContentKind.PROFILE)
    }

    @Test
    fun directFilesAndUnknownSites() {
        val video = recognizer.recognize("https://exemplo.com/midia/Video%2001.mp4?token=1")!!
        assertEquals(Platform.DIRECT, video.platform)
        assertEquals(ContentKind.FILE, video.kind)
        assertEquals(MediaKind.VIDEO, video.mediaKind)

        val audio = recognizer.recognize("http://exemplo.com/audio.MP3")!!
        assertEquals(MediaKind.AUDIO, audio.mediaKind)

        val page = recognizer.recognize("https://exemplo.com/noticia/123")!!
        assertEquals(Platform.WEB, page.platform)
        assertEquals(ContentKind.PAGE, page.kind)
    }

    @Test
    fun lookalikeDomainsAreNotRecognizedAsPlatforms() {
        assertEquals(Platform.WEB, recognizer.recognize("https://notyoutube.com/watch?v=dQw4w9WgXcQ")!!.platform)
        assertEquals(Platform.WEB, recognizer.recognize("https://youtube.com.golpe.net/watch?v=dQw4w9WgXcQ")!!.platform)
        assertEquals(Platform.WEB, recognizer.recognize("https://instagram.fake.com/p/abc")!!.platform)
    }

    @Test
    fun invalidInput() {
        assertNull(recognizer.recognize("isto não é um link"))
        assertNull(recognizer.recognize("ftp://exemplo.com/arquivo.mp4"))
        assertNull(recognizer.recognize(""))
    }
}
