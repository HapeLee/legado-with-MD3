package io.legado.app.ui.book.read.pageestimate

import io.legado.app.data.appDb
import io.legado.app.data.entities.ExactChapterPageCountEntity

data class ExactChapterPageCount(
    val bookId: String,
    val chapterId: String,
    val chapterIndex: Int,
    val contentHash: Long,
    val layoutSignature: Long,
    val engineVersion: Int,
    val pageCount: Int,
    val updatedAt: Long,
)

interface ExactChapterPageCountStore {
    suspend fun load(
        bookId: String,
        layoutSignature: Long,
        engineVersion: Int,
    ): List<ExactChapterPageCount>

    suspend fun save(value: ExactChapterPageCount)

    suspend fun deleteChapter(bookId: String, chapterId: String)

    companion object {
        val None = object : ExactChapterPageCountStore {
            override suspend fun load(
                bookId: String,
                layoutSignature: Long,
                engineVersion: Int,
            ) = emptyList<ExactChapterPageCount>()

            override suspend fun save(value: ExactChapterPageCount) = Unit

            override suspend fun deleteChapter(bookId: String, chapterId: String) = Unit
        }
    }
}

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
