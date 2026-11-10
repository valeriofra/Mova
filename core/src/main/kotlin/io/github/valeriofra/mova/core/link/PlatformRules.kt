package io.github.valeriofra.mova.core.link

import io.github.valeriofra.mova.core.model.ContentKind
import io.github.valeriofra.mova.core.model.ContentKind.COLLECTION
import io.github.valeriofra.mova.core.model.ContentKind.LIVE
import io.github.valeriofra.mova.core.model.ContentKind.PLAYLIST
import io.github.valeriofra.mova.core.model.ContentKind.POST
import io.github.valeriofra.mova.core.model.ContentKind.PROFILE
import io.github.valeriofra.mova.core.model.ContentKind.SHORT_LINK
import io.github.valeriofra.mova.core.model.ContentKind.SHORT_VIDEO
import io.github.valeriofra.mova.core.model.ContentKind.STORY
import io.github.valeriofra.mova.core.model.ContentKind.STORY_HIGHLIGHT
import io.github.valeriofra.mova.core.model.ContentKind.UNKNOWN
import io.github.valeriofra.mova.core.model.ContentKind.VIDEO
import io.github.valeriofra.mova.core.model.Platform

/**
 * Regras de reconhecimento das sete plataformas do planejamento. Apenas
 * classificam o link pela estrutura da URL; nenhuma rede é acessada aqui.
 */
object PlatformRules {
    val all: List<PlatformRule> = listOf(
        YouTubeRule,
        InstagramRule,
        TikTokRule,
        FacebookRule,
        KwaiRule,
        PinterestRule,
        VimeoRule,
    )
}

private fun match(kind: ContentKind, id: String? = null, collection: String? = null) =
    LinkMatch(kind, id, collection)

private val UNKNOWN_MATCH = LinkMatch(UNKNOWN)

private fun String.isNumeric() = isNotEmpty() && all { it.isDigit() }

// ---------------------------------------------------------------------------
// YouTube
// ---------------------------------------------------------------------------
internal object YouTubeRule : PlatformRule {
    override val platform = Platform.YOUTUBE
    private val videoId = Regex("^[A-Za-z0-9_-]{11}$")

    override fun matchesHost(url: ParsedUrl) =
        url.isHost("youtube.com") || url.isHost("youtu.be") || url.isHost("youtube-nocookie.com")

    override fun classify(url: ParsedUrl): LinkMatch {
        val list = url.param("list")
        if (url.isHost("youtu.be")) {
            val id = url.segment(0) ?: return UNKNOWN_MATCH
            return if (videoId.matches(id)) match(VIDEO, id, list) else UNKNOWN_MATCH
        }
        val first = url.segment(0) ?: return UNKNOWN_MATCH
        return when {
            first == "watch" -> {
                val id = url.param("v")
                when {
                    id != null && videoId.matches(id) -> match(VIDEO, id, list)
                    list != null -> match(PLAYLIST, list)
                    else -> UNKNOWN_MATCH
                }
            }
            first == "playlist" -> list?.let { match(PLAYLIST, it) } ?: UNKNOWN_MATCH
            first == "shorts" -> idOrUnknown(url.segment(1), SHORT_VIDEO)
            first == "live" -> idOrUnknown(url.segment(1), LIVE)
            first == "embed" && url.segment(1) == "videoseries" ->
                list?.let { match(PLAYLIST, it) } ?: UNKNOWN_MATCH
            first == "embed" || first == "v" || first == "e" || first == "clip" ->
                idOrUnknown(url.segment(1), VIDEO, list)
            first == "post" -> match(POST, url.segment(1))
            first.startsWith("@") || first == "channel" || first == "c" || first == "user" ->
                match(PROFILE, if (first.startsWith("@")) first else url.segment(1))
            else -> UNKNOWN_MATCH
        }
    }

    private fun idOrUnknown(id: String?, kind: ContentKind, list: String? = null): LinkMatch =
        if (id != null && videoId.matches(id)) match(kind, id, list) else UNKNOWN_MATCH
}

// ---------------------------------------------------------------------------
// Instagram
// ---------------------------------------------------------------------------
internal object InstagramRule : PlatformRule {
    override val platform = Platform.INSTAGRAM
    private val username = Regex("^[A-Za-z0-9._]{1,30}$")
    private val reserved = setOf(
        "explore", "accounts", "direct", "about", "legal", "developer", "web",
        "challenge", "emails", "session", "reels", "api",
    )

    override fun matchesHost(url: ParsedUrl) =
        url.isHost("instagram.com") || url.isHost("instagr.am")

    override fun classify(url: ParsedUrl): LinkMatch {
        val first = url.segment(0) ?: return UNKNOWN_MATCH
        val second = url.segment(1)
        return when (first) {
            "p" -> second?.let { match(POST, it) } ?: UNKNOWN_MATCH
            "reel" -> second?.let { match(SHORT_VIDEO, it) } ?: UNKNOWN_MATCH
            "reels" -> second?.let { match(SHORT_VIDEO, it) } ?: UNKNOWN_MATCH
            "tv" -> second?.let { match(VIDEO, it) } ?: UNKNOWN_MATCH
            "stories" -> when {
                second == null -> UNKNOWN_MATCH
                second == "highlights" -> match(STORY_HIGHLIGHT, url.segment(2))
                else -> match(STORY, url.segment(2), second)
            }
            "s" -> match(STORY_HIGHLIGHT, second)
            "share" -> match(SHORT_LINK, url.segments.lastOrNull())
            else -> when {
                first in reserved || !username.matches(first) -> UNKNOWN_MATCH
                // Formato novo: instagram.com/usuario/p/CODIGO e /usuario/reel/CODIGO
                second == "p" -> url.segment(2)?.let { match(POST, it) } ?: UNKNOWN_MATCH
                second == "reel" -> url.segment(2)?.let { match(SHORT_VIDEO, it) } ?: UNKNOWN_MATCH
                second == null || second == "reels" || second == "tagged" -> match(PROFILE, first)
                else -> UNKNOWN_MATCH
            }
        }
    }
}

// ---------------------------------------------------------------------------
// TikTok
// ---------------------------------------------------------------------------
internal object TikTokRule : PlatformRule {
    override val platform = Platform.TIKTOK

    override fun matchesHost(url: ParsedUrl) = url.isHost("tiktok.com")

    override fun classify(url: ParsedUrl): LinkMatch {
        if (url.host.startsWith("vm.") || url.host.startsWith("vt.")) {
            return match(SHORT_LINK, url.segment(0))
        }
        val first = url.segment(0) ?: return UNKNOWN_MATCH
        return when {
            first == "t" -> match(SHORT_LINK, url.segment(1))
            first.startsWith("@") -> when (url.segment(1)) {
                null -> match(PROFILE, first)
                "video" -> url.segment(2)?.let { match(VIDEO, it) } ?: UNKNOWN_MATCH
                "photo" -> url.segment(2)?.let { match(POST, it) } ?: UNKNOWN_MATCH
                "live" -> match(LIVE, first)
                else -> UNKNOWN_MATCH
            }
            first == "embed" -> url.segments.lastOrNull()?.takeIf { it.isNumeric() }
                ?.let { match(VIDEO, it) } ?: UNKNOWN_MATCH
            first == "v" -> url.segment(1)?.removeSuffix(".html")?.takeIf { it.isNumeric() }
                ?.let { match(VIDEO, it) } ?: UNKNOWN_MATCH
            else -> UNKNOWN_MATCH
        }
    }
}

// ---------------------------------------------------------------------------
// Facebook
// ---------------------------------------------------------------------------
internal object FacebookRule : PlatformRule {
    override val platform = Platform.FACEBOOK
    private val reserved = setOf(
        "login", "help", "events", "marketplace", "gaming", "settings", "privacy",
        "policies", "pages", "business", "messages", "notifications", "friends", "search",
    )

    override fun matchesHost(url: ParsedUrl) =
        url.isHost("facebook.com") || url.isHost("fb.com") || url.isHost("fb.watch")

    override fun classify(url: ParsedUrl): LinkMatch {
        if (url.isHost("fb.watch")) return match(VIDEO, url.segment(0))
        val first = url.segment(0) ?: return UNKNOWN_MATCH
        val second = url.segment(1)
        return when (first) {
            "watch" -> when {
                second == "live" -> match(LIVE, url.param("v"))
                url.param("v") != null -> match(VIDEO, url.param("v"))
                else -> UNKNOWN_MATCH
            }
            "video.php" -> match(VIDEO, url.param("v"))
            "reel" -> second?.let { match(SHORT_VIDEO, it) } ?: UNKNOWN_MATCH
            "stories" -> match(STORY, url.segments.lastOrNull())
            "share" -> when (second) {
                "v" -> match(VIDEO, url.segment(2))
                "r" -> match(SHORT_VIDEO, url.segment(2))
                "p" -> match(POST, url.segment(2))
                else -> match(SHORT_LINK, url.segments.lastOrNull())
            }
            "story.php", "permalink.php", "photo.php", "photo" -> match(POST, url.param("story_fbid") ?: url.param("fbid"))
            "profile.php" -> match(PROFILE, url.param("id"))
            "groups" -> when {
                url.segments.contains("posts") || url.segments.contains("permalink") ->
                    match(POST, url.segments.last())
                second != null -> match(PROFILE, second)
                else -> UNKNOWN_MATCH
            }
            else -> when {
                first in reserved -> UNKNOWN_MATCH
                second == "videos" -> {
                    val id = url.segments.drop(2).lastOrNull { it.isNumeric() }
                    if (id != null) match(VIDEO, id) else match(PROFILE, first)
                }
                second == "posts" || second == "photos" -> match(POST, url.segments.last())
                second == null -> match(PROFILE, first)
                else -> UNKNOWN_MATCH
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Kwai
// ---------------------------------------------------------------------------
internal object KwaiRule : PlatformRule {
    override val platform = Platform.KWAI

    override fun matchesHost(url: ParsedUrl) =
        url.isHost("kwai.com") || url.isHost("kw.ai") || url.isHost("kwai-video.com")

    override fun classify(url: ParsedUrl): LinkMatch {
        if (url.isHost("kw.ai") || url.isHost("kwai-video.com")) {
            return match(SHORT_LINK, url.segments.lastOrNull())
        }
        val first = url.segment(0) ?: return UNKNOWN_MATCH
        return when {
            first.startsWith("@") -> when (url.segment(1)) {
                null -> match(PROFILE, first)
                "video" -> url.segment(2)?.let { match(VIDEO, it) } ?: UNKNOWN_MATCH
                else -> UNKNOWN_MATCH
            }
            first == "video" || first == "short-video" -> url.segment(1)?.let { match(VIDEO, it) } ?: UNKNOWN_MATCH
            first == "p" || first == "f" -> match(SHORT_LINK, url.segment(1))
            else -> UNKNOWN_MATCH
        }
    }
}

// ---------------------------------------------------------------------------
// Pinterest
// ---------------------------------------------------------------------------
internal object PinterestRule : PlatformRule {
    override val platform = Platform.PINTEREST

    // pinterest.com, pinterest.com.br, br.pinterest.com, pinterest.co.uk, pinterest.de...
    private val pinterestHost = Regex("""(^|\.)pinterest\.(com|[a-z]{2}|com\.[a-z]{2}|co\.[a-z]{2})$""")
    private val reserved = setOf(
        "search", "ideas", "today", "settings", "business", "login", "signup", "_",
        "explore", "about", "resource", "categories", "videos",
    )

    override fun matchesHost(url: ParsedUrl) =
        url.isHost("pin.it") || pinterestHost.containsMatchIn(url.host)

    override fun classify(url: ParsedUrl): LinkMatch {
        if (url.isHost("pin.it")) return match(SHORT_LINK, url.segment(0))
        val first = url.segment(0) ?: return UNKNOWN_MATCH
        val second = url.segment(1)
        return when {
            first == "pin" -> second?.let { match(POST, it) } ?: UNKNOWN_MATCH
            first in reserved -> UNKNOWN_MATCH
            second == null || second in setOf("_saved", "_created", "pins", "boards") ->
                match(PROFILE, first)
            else -> match(COLLECTION, "$first/$second")
        }
    }
}

// ---------------------------------------------------------------------------
// Vimeo
// ---------------------------------------------------------------------------
internal object VimeoRule : PlatformRule {
    override val platform = Platform.VIMEO
    private val reserved = setOf(
        "login", "join", "upgrade", "features", "watch", "search", "categories",
        "ondemand", "blog", "help", "solutions", "enterprise", "create", "settings",
    )

    override fun matchesHost(url: ParsedUrl) = url.isHost("vimeo.com")

    override fun classify(url: ParsedUrl): LinkMatch {
        val first = url.segment(0) ?: return UNKNOWN_MATCH
        if (url.host.startsWith("player.")) {
            return if (first == "video" && url.segment(1)?.isNumeric() == true) {
                match(VIDEO, url.segment(1))
            } else {
                UNKNOWN_MATCH
            }
        }
        return when {
            first.isNumeric() -> match(VIDEO, first)
            first == "channels" -> {
                val id = url.segment(2)
                if (id != null && id.isNumeric()) match(VIDEO, id) else match(COLLECTION, url.segment(1))
            }
            first == "groups" -> {
                val id = url.segment(3)
                if (url.segment(2) == "videos" && id != null && id.isNumeric()) {
                    match(VIDEO, id)
                } else {
                    match(COLLECTION, url.segment(1))
                }
            }
            first == "showcase" || first == "album" -> {
                val id = url.segment(3)
                if (url.segment(2) == "video" && id != null && id.isNumeric()) {
                    match(VIDEO, id, url.segment(1))
                } else {
                    match(COLLECTION, url.segment(1))
                }
            }
            first == "event" -> match(LIVE, url.segment(1))
            first in reserved -> UNKNOWN_MATCH
            else -> match(PROFILE, first)
        }
    }
}
