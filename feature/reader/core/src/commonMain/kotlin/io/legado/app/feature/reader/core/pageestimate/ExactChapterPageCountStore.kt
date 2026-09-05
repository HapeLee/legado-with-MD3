package io.legado.app.feature.reader.core.pageestimate

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
