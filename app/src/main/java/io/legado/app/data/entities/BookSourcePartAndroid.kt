package io.legado.app.data.entities

import io.legado.app.data.appDb
import kotlinx.coroutines.runBlocking

/**
 * [BookSourcePart.getBookSource] 依赖 `appDb`（Android 侧 Room 单例），无法进 commonMain，
 * 故下沉时外置为本文件扩展。
 */
fun BookSourcePart.getBookSource(): BookSource? {
    return runBlocking { appDb.bookSourceDao.getBookSource(bookSourceUrl) }
}

fun List<BookSourcePart>.toBookSource(): List<BookSource> {
    return mapNotNull { it.getBookSource() }
}
