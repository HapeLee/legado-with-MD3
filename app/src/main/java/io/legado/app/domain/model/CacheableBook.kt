package io.legado.app.domain.model

data class CacheableBook(
    val bookUrl: String,
    val isLocal: Boolean,
    val isAudio: Boolean,
    val durChapterIndex: Int,
    val lastChapterIndex: Int
)
