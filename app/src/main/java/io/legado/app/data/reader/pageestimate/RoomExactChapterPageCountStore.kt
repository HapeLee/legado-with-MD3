package io.legado.app.data.reader.pageestimate

import io.legado.app.data.appDb
import io.legado.app.data.entities.ExactChapterPageCountEntity
import io.legado.app.feature.reader.core.pageestimate.ExactChapterPageCount
import io.legado.app.feature.reader.core.pageestimate.ExactChapterPageCountStore

object RoomExactChapterPageCountStore : ExactChapterPageCountStore {
    override suspend fun load(
        bookId: String,
        layoutSignature: Long,
        engineVersion: Int,
    ): List<ExactChapterPageCount> = appDb.exactChapterPageCountDao
        .getByLayout(bookId, layoutSignature, engineVersion)
        .map(ExactChapterPageCountEntity::toModel)

    override suspend fun save(value: ExactChapterPageCount) {
        appDb.exactChapterPageCountDao.upsert(value.toEntity())
    }

    override suspend fun deleteChapter(bookId: String, chapterId: String) {
        appDb.exactChapterPageCountDao.deleteChapter(bookId, chapterId)
    }
}

private fun ExactChapterPageCountEntity.toModel() = ExactChapterPageCount(
    bookId = bookId,
    chapterId = chapterId,
    chapterIndex = chapterIndex,
    contentHash = contentHash,
    layoutSignature = layoutSignature,
    engineVersion = engineVersion,
    pageCount = pageCount,
    updatedAt = updatedAt,
)

private fun ExactChapterPageCount.toEntity() = ExactChapterPageCountEntity(
    bookId = bookId,
    chapterId = chapterId,
    chapterIndex = chapterIndex,
    contentHash = contentHash,
    layoutSignature = layoutSignature,
    engineVersion = engineVersion,
    pageCount = pageCount,
    updatedAt = updatedAt,
)
