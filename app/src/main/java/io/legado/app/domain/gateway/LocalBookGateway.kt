package io.legado.app.domain.gateway

interface LocalBookGateway {
    suspend fun deleteBook(bookUrl: String, deleteOriginal: Boolean)
}
