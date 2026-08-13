package io.legado.app.domain.gateway

fun interface MangaReaderSessionFactory {
    fun create(): MangaReaderSession
}
