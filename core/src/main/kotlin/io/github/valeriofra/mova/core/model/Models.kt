package io.github.valeriofra.mova.core.model

/** Origem reconhecida de um link. */
enum class Platform(val displayName: String) {
    YOUTUBE("YouTube"),
    FACEBOOK("Facebook"),
    INSTAGRAM("Instagram"),
    TIKTOK("TikTok"),
    KWAI("Kwai"),
    PINTEREST("Pinterest"),
    VIMEO("Vimeo"),

    /** Link direto para um arquivo (ex.: https://exemplo.com/video.mp4). */
    DIRECT("Link direto"),

    /** Qualquer outro site não reconhecido. */
    WEB("Site");

    /** Uma das redes sociais/plataformas de vídeo listadas no planejamento. */
    val isKnownPlatform: Boolean get() = this != DIRECT && this != WEB

    companion object {
        val knownPlatforms: List<Platform> = entries.filter { it.isKnownPlatform }
    }
}

/** Tipo de conteúdo que um link representa, quando é possível identificar. */
enum class ContentKind {
    VIDEO,
    SHORT_VIDEO,
    POST,
    STORY,
    STORY_HIGHLIGHT,
    PLAYLIST,
    COLLECTION,
    PROFILE,
    LIVE,
    SHORT_LINK,
    FILE,
    PAGE,
    UNKNOWN;

    /** O conteúdo pode reunir vários itens (publicação, playlist, coleção...). */
    val mayContainMultipleItems: Boolean
        get() = this == POST || this == STORY_HIGHLIGHT || this == PLAYLIST ||
            this == COLLECTION || this == PROFILE

    /** Conteúdo temporário (Stories), que pode expirar a qualquer momento. */
    val isTemporary: Boolean get() = this == STORY
}

/** Natureza do arquivo de mídia. */
enum class MediaKind {
    VIDEO,
    AUDIO,
    IMAGE,
    DOCUMENT,
    OTHER,
}

/** De onde o arquivo é obtido. */
enum class MediaSource {
    /** Link direto para o arquivo, baixado por HTTP. */
    DIRECT,

    /** Página de plataforma, processada pelo mecanismo de extração (yt-dlp). */
    EXTRACTOR,
}
