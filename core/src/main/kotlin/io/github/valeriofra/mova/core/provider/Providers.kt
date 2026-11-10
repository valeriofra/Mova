package io.github.valeriofra.mova.core.provider

import io.github.valeriofra.mova.core.model.Platform

/** Estado da integração de uma origem. */
enum class IntegrationStatus {
    /** Funciona e foi validado. */
    AVAILABLE,

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
 * Registro central de capacidades. Uma plataforma só passa a oferecer
 * download quando sua integração for implementada por meios oficiais ou
 * expressamente autorizados — reconhecer a URL não basta.
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

    private val capabilities: Map<Platform, ProviderCapabilities> = buildMap {
        Platform.knownPlatforms.forEach { put(it, recognitionOnly(it)) }
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
