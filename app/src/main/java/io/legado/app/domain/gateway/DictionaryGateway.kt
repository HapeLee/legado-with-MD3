package io.legado.app.domain.gateway

import io.legado.app.data.entities.Book
import io.legado.app.domain.model.BookDictionary
import io.legado.app.domain.model.DictPair

interface DictionaryGateway {
    fun getBookDictionaries(book: Book): BookDictionary
    fun mergeDiscoveredPairs(book: Book, newPairs: List<DictPair>): BookDictionary
    fun clearBookDictionary(book: Book)
}
