package io.legado.app.domain.gateway

import io.legado.app.domain.model.BookshelfAutoGroupPromptText

interface BookshelfAutoGroupPromptGateway {
    /** Resolves prompt text on demand so it follows the current application locale. */
    fun getPromptText(): BookshelfAutoGroupPromptText
}
