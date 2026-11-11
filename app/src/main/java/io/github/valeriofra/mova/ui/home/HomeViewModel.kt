package io.github.valeriofra.mova.ui.home

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import io.github.valeriofra.mova.core.link.LinkRecognizer
import io.github.valeriofra.mova.core.link.RecognizedLink
import io.github.valeriofra.mova.core.link.UrlExtractor

/** Pré-visualização imediata do que foi colado, antes da análise. */
sealed interface LinkPreview {
    data object Empty : LinkPreview
    data object NoLink : LinkPreview
    data class Single(val link: RecognizedLink) : LinkPreview
    data class Multiple(val count: Int) : LinkPreview
}

class HomeViewModel : ViewModel() {

    private val recognizer = LinkRecognizer()

    var input by mutableStateOf("")
        private set

    val preview: LinkPreview by derivedStateOf { computePreview(input) }

    fun onInputChange(value: String) {
        input = value
    }

    fun clear() {
        input = ""
    }

    private fun computePreview(text: String): LinkPreview {
        if (text.isBlank()) return LinkPreview.Empty
        val urls = UrlExtractor.extract(text)
        return when {
            urls.isEmpty() -> LinkPreview.NoLink
            urls.size == 1 -> recognizer.recognize(urls.single())?.let { LinkPreview.Single(it) } ?: LinkPreview.NoLink
            else -> LinkPreview.Multiple(urls.size)
        }
    }
}
