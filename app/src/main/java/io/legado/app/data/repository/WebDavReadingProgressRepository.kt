package io.legado.app.data.repository

import io.legado.app.data.entities.BookProgress
import io.legado.app.domain.gateway.ReadingProgressGateway
import io.legado.app.domain.model.ReadingProgress
import io.legado.app.help.AppWebDav

class WebDavReadingProgressRepository : ReadingProgressGateway {

    override val isConfigured: Boolean
        get() = AppWebDav.isOk

    override suspend fun getProgress(name: String, author: String): ReadingProgress? {
        return AppWebDav.getBookProgress(name, author)?.let {
            ReadingProgress(
                name = it.name,
                author = it.author,
                durChapterIndex = it.durChapterIndex,
                durChapterPos = it.durChapterPos,
                durChapterTime = it.durChapterTime,
                durChapterTitle = it.durChapterTitle
            )
        }
    }

    override suspend fun uploadProgress(progress: ReadingProgress): Long? {
        val uploadTime = System.currentTimeMillis()
        val uploaded = AppWebDav.uploadBookProgress(
            BookProgress(
                name = progress.name,
                author = progress.author,
                durChapterIndex = progress.durChapterIndex,
                durChapterPos = progress.durChapterPos,
                durChapterTime = progress.durChapterTime,
                durChapterTitle = progress.durChapterTitle
            )
        )
        return if (uploaded) uploadTime else null
    }
}
