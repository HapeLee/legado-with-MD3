package io.legado.app.domain.usecase

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.ShelfBookSummary
import io.legado.app.data.repository.BookRepository
import io.legado.app.domain.model.BookMatchKey
import io.legado.app.domain.model.BookshelfConflict
import io.legado.app.domain.model.ConflictBookSummary
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 加入书架前的重复检测。
 *
 * 判定规则：
 * - 书名经 [ReadRecordIdentity.bookName] 规范化后相同；
 * - 作者经 [ReadRecordIdentity.author] 规范化后相同；任一方作者为空时视为疑似，仅按书名判定；
 * - 已下架的作品（带 notShelf 标记）不算冲突。
 */
class FindBookshelfConflictUseCase(
    private val bookRepository: BookRepository,
) {

    suspend fun execute(incoming: Book): BookshelfConflict? = withContext(Dispatchers.IO) {
        val name = bookKey(incoming.name)
        val author = bookKey(incoming.author)
        if (name.isBlank()) return@withContext null
        val candidates = bookRepository.getShelfBookSummaries()
            .filter { it.bookUrl != incoming.bookUrl }
            .filter { bookKey(it.name) == name }
            .filter { authorCompatible(author, bookKey(it.author)) }
            .sortedByDescending { it.durChapterTime }
            .map { it.toConflictSummary() }
        if (candidates.isEmpty()) return@withContext null
        BookshelfConflict(
            incoming = incoming.toConflictSummary(),
            candidates = candidates.toImmutableList(),
        )
    }

    private fun bookKey(value: String): String = BookMatchKey.of(value)

    private fun authorCompatible(incoming: String, existing: String): Boolean =
        BookMatchKey.authorCompatible(incoming, existing)

    private fun Book.toConflictSummary() = ConflictBookSummary(
        bookUrl = bookUrl,
        name = name,
        author = author,
        coverUrl = coverUrl,
        customCoverUrl = customCoverUrl,
        origin = origin,
        sourceName = originName.ifBlank { origin },
        totalChapterNum = totalChapterNum,
        latestChapterTitle = latestChapterTitle,
    )

    private fun ShelfBookSummary.toConflictSummary() = ConflictBookSummary(
        bookUrl = bookUrl,
        name = name,
        author = author,
        coverUrl = coverUrl,
        customCoverUrl = customCoverUrl,
        origin = origin,
        sourceName = originName.ifBlank { origin },
        totalChapterNum = totalChapterNum,
        latestChapterTitle = latestChapterTitle,
    )
}
