package io.legado.app.domain.model

data class DeletableBook(
    val bookUrl: String,
    val origin: String,
    val isLocal: Boolean
)
