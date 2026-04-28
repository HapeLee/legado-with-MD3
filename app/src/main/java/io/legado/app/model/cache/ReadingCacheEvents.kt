package io.legado.app.model.cache

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface ReadingCacheEvent {
    data class ContentReady(
        val book: Book,
        val chapter: BookChapter,
        val content: String,
        val resetPageOffset: Boolean,
        val canceled: Boolean,
    ) : ReadingCacheEvent
}

object ReadingCacheEvents {
    private val _events = MutableSharedFlow<ReadingCacheEvent>(extraBufferCapacity = 32)
    val events = _events.asSharedFlow()

    fun emit(event: ReadingCacheEvent) {
        _events.tryEmit(event)
    }
}
