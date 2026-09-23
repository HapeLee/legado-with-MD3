package io.legado.app.domain.usecase

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.ShelfBookSummary
import io.legado.app.data.repository.BookRepository
import io.legado.app.domain.model.BookMatchKey
import io.legado.app.domain.model.ConflictBookSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 查找书架上**确定**为同一部作品的在架书籍。
 *
 * 与 [FindBookshelfConflictUseCase]（入架查重，宽松「疑似」口径）刻意不同：
 * 这里的候选会被用于展示「在架」入口并执行跳转，误判代价是把用户导航到另一本书，
 * 因此要求书名归一化后相等 **且** 两边作者都非空且相等。
 * 任一方作者缺失即视为不确定，一律不命中——搜索结果常缺作者，不能退化成只按书名匹配。
 *
 * 候选集来自 [BookRepository.getShelfBookSummaries]，其 SQL 已排除未上架的书；
 * 本地书参与候选（用户可能导入了同名 TXT，靠 sourceName 区分）。
 */
class FindShelfSameBookUseCase(
    private val bookRepository: BookRepository,
) {

    suspend fun execute(incoming: Book): List<ConflictBookSummary> = withContext(Dispatchers.IO) {
        matchSameBook(
            summaries = bookRepository.getShelfBookSummaries(),
            incomingBookUrl = incoming.bookUrl,
            name = incoming.name,
            author = incoming.author,
        ).map { it.toConflictSummary() }
    }
}

/**
 * 从书架摘要里挑出**确定为同一部作品**的书，按最后阅读时间降序。
 *
 * 抽成纯函数是为了能直接用普通 data 对象测，不必拉起 Room / Robolectric：
 * 判定规则是这个用例唯一有风险的部分，值得单独锁住。
 *
 * 规则：
 * - 书名经 [BookMatchKey] 归一化后相等（NFKC + 空白折叠）；
 * - 两边作者都非空且归一化后相等；任一方缺失即不命中；
 * - 排除自身；
 * - 未上架的书由上游 SQL 排除，这里不再判断。
 */
internal fun matchSameBook(
    summaries: List<ShelfBookSummary>,
    incomingBookUrl: String,
    name: String,
    author: String,
): List<ShelfBookSummary> {
    val normalizedName = BookMatchKey.of(name)
    if (normalizedName.isBlank()) return emptyList()
    val normalizedAuthor = BookMatchKey.of(author)
    // 作者缺失 -> 无法确定是同一部作品，不命中
    if (normalizedAuthor.isBlank()) return emptyList()

    return summaries
        .filter { it.bookUrl != incomingBookUrl }
        .filter { BookMatchKey.of(it.name) == normalizedName }
        // normalizedAuthor 已非空，因此这里等价于「两边都非空且相等」
        .filter { BookMatchKey.of(it.author) == normalizedAuthor }
        .sortedByDescending { it.durChapterTime }
}

internal fun ShelfBookSummary.toConflictSummary() = ConflictBookSummary(
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
