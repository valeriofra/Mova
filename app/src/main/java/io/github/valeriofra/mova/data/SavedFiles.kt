package io.github.valeriofra.mova.data

/** Arquivos já salvos no aparelho (MediaStore ou pasta escolhida), identificados pela URI. */
interface SavedFiles {
    /** Apaga o arquivo; `false` se não conseguiu (ex.: sem permissão). */
    fun delete(uri: String): Boolean

    fun exists(uri: String): Boolean
}

/** Resultado de uma exclusão automática. */
data class CleanupResult(val filesDeleted: Int, val recordsRemoved: Int) {
    companion object {
        val NONE = CleanupResult(0, 0)
    }
}
