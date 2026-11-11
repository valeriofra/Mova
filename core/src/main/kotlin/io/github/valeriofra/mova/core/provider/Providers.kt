package io.github.valeriofra.mova.core.provider

import io.github.valeriofra.mova.core.model.Platform

/** Estado da integração de uma origem. */
enum class IntegrationStatus {
    /** Download disponível. */
    AVAILABLE,

    /** Download disponível, mas parte do conteúdo exige login. */
    PARTIAL,

    /** Download tentado pelo mecanismo, sem garantia de funcionar. */
    EXPERIMENTAL,

    /** Reconhece links, mas ainda não há meio oficial/autorizado implementado. */
    RECOGNITION_ONLY,

    /** Não se aplica (ex.: site desconhecido). */
    NOT_APPLICABLE,
}

/**
 * O que um provider consegue fazer de forma legítima e tecnicamente comprovada.
 * A interface do app só oferece ações que estejam declaradas aqui.
 */
data class ProviderCapabilities(
    val platform: Platform,
    val status: IntegrationStatus,
    val recognizesLinks: Boolean,
    val readsMetadata: Boolean,
    val downloadsFiles: Boolean,
    val extractsAudio: Boolean,
    val listsCollections: Boolean,
    val allowsQualityChoice: Boolean,
)

/**
 * Registro central de capacidades: o que cada origem oferece. A interface só
 * mostra ações declaradas aqui (ver planejamento, Atualização 2).
 */
object ProviderRegistry {

    private val recognitionOnly = { platform: Platform ->
        ProviderCapabilities(
            platform = platform,
            status = IntegrationStatus.RECOGNITION_ONLY,
            recognizesLinks = true,
            readsMetadata = false,
            downloadsFiles = false,
            extractsAudio = false,
            listsCollections = false,
            allowsQualityChoice = false,
        )
    }

    /** Plataformas baixadas pelo mecanismo de extração (yt-dlp), para uso pessoal. */
    private val extractorStatus = mapOf(
        Platform.YOUTUBE to IntegrationStatus.AVAILABLE,
        Platform.VIMEO to IntegrationStatus.AVAILABLE,
        Platform.TIKTOK to IntegrationStatus.AVAILABLE,
        Platform.FACEBOOK to IntegrationStatus.AVAILABLE,
        Platform.PINTEREST to IntegrationStatus.AVAILABLE,
        Platform.INSTAGRAM to IntegrationStatus.PARTIAL,
        Platform.KWAI to IntegrationStatus.EXPERIMENTAL,
    )

    private val capabilities: Map<Platform, ProviderCapabilities> = buildMap {
        Platform.knownPlatforms.forEach { platform ->
            val status = extractorStatus[platform]
            put(
                platform,
                if (status == null) {
                    recognitionOnly(platform)
                } else {
                    ProviderCapabilities(
                        platform = platform,
                        status = status,
                        recognizesLinks = true,
                        readsMetadata = true,
                        downloadsFiles = true,
                        extractsAudio = true,
                        listsCollections = true,
                        allowsQualityChoice = true,
                    )
                },
            )
        }
        put(
            Platform.DIRECT,
            ProviderCapabilities(
                platform = Platform.DIRECT,
                status = IntegrationStatus.AVAILABLE,
                recognizesLinks = true,
                readsMetadata = true,
                downloadsFiles = true,
                extractsAudio = false,
                listsCollections = false,
                allowsQualityChoice = false,
            ),
        )
        put(
            Platform.WEB,
            ProviderCapabilities(
                platform = Platform.WEB,
                status = IntegrationStatus.NOT_APPLICABLE,
                recognizesLinks = false,
                readsMetadata = true,
                // Um link de site pode, na verdade, servir um arquivo; a análise decide.
                downloadsFiles = true,
                extractsAudio = false,
                listsCollections = false,
                allowsQualityChoice = false,
            ),
        )
    }

    fun of(platform: Platform): ProviderCapabilities = capabilities.getValue(platform)

    val knownPlatforms: List<ProviderCapabilities>
        get() = Platform.knownPlatforms.map(::of)
}
