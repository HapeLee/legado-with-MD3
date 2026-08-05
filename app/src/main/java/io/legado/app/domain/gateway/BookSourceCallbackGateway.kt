package io.legado.app.domain.gateway

interface BookSourceCallbackGateway {
    suspend fun onDeleteFromShelf(bookUrl: String)
}
