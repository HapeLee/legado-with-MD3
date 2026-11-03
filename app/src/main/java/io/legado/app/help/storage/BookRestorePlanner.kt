package io.legado.app.help.storage

import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.domain.model.BookMatchKey
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isType
import java.io.File
import java.net.URI

internal data class BookRestorePlan(
    val booksToUpdate: List<Book>,
    val booksToInsert: List<Book>,
    val booksToDelete: List<Book>,
) {
    val booksToUpsert: List<Book>
        get() = booksToUpdate + booksToInsert
}

internal enum class LocalBookLocationStatus {
    Available,
    Missing,
    /** 存储离线、权限失效等原因导致无法确认，不能据此删除书籍。 */
    Unknown,
}

/**
 * 规划书架恢复操作，避免旧备份中的本地文件旧路径生成重复书籍。
 */
internal fun planBookRestore(
    restoredBooks: List<Book>,
    existingBooks: List<Book>,
    ignoreLocalBook: Boolean,
    locationStatus: (String) -> LocalBookLocationStatus,
    normalizeLocation: (String) -> String = ::normalizeLocalBookLocation,
): BookRestorePlan {
    val existingBookUrls = existingBooks.mapTo(hashSetOf()) { it.bookUrl }
    val activeBooks = existingBooks.associateByTo(linkedMapOf()) { it.bookUrl }
    val booksToUpsert = linkedMapOf<String, Book>()
    val booksToDelete = linkedMapOf<String, Book>()
    val locationStatusCache = hashMapOf<String, LocalBookLocationStatus>()

    val onlineGroupPlan = planOnlineBookGroups(restoredBooks, existingBooks)
    onlineGroupPlan.followDeleteBookUrls.forEach { bookUrl ->
        val existingBook = activeBooks.remove(bookUrl) ?: return@forEach
        booksToDelete[bookUrl] = existingBook
    }

    fun cachedLocationStatus(bookUrl: String): LocalBookLocationStatus =
        locationStatusCache.getOrPut(bookUrl) { locationStatus(bookUrl) }

    restoredBooks.forEach { restoredBook ->
        if (ignoreLocalBook && restoredBook.isLocal) return@forEach
        if (!restoredBook.isLocal && restoredBook.bookUrl in onlineGroupPlan.skipRestoredBookUrls) {
            return@forEach
        }

        val resolution = if (restoredBook.isLocal) {
            resolveLocalBookTarget(
                restoredBook = restoredBook,
                activeBooks = activeBooks.values,
                locationStatus = ::cachedLocationStatus,
                normalizeLocation = normalizeLocation,
            )
        } else {
            LocalBookResolution(restoredBook.bookUrl, emptySet())
        }

        resolution.duplicateBookUrls.forEach duplicateLoop@{ duplicateBookUrl ->
            val duplicateBook = activeBooks.remove(duplicateBookUrl) ?: return@duplicateLoop
            booksToUpsert.remove(duplicateBookUrl)
            if (duplicateBookUrl in existingBookUrls) {
                booksToDelete[duplicateBookUrl] = duplicateBook
            }
        }

        val restoredForSave = restoredBook.copy(bookUrl = resolution.targetBookUrl)
        booksToDelete.remove(restoredForSave.bookUrl)
        booksToUpsert[restoredForSave.bookUrl] = restoredForSave
        activeBooks[restoredForSave.bookUrl] = restoredForSave
    }

    val (booksToUpdate, booksToInsert) = booksToUpsert.values.partition {
        it.bookUrl in existingBookUrls
    }
    return BookRestorePlan(
        booksToUpdate = booksToUpdate,
        booksToInsert = booksToInsert,
        booksToDelete = booksToDelete.values.toList(),
    )
}

internal data class OnlineBookGroupPlan(
    val skipRestoredBookUrls: Set<String>,
    val followDeleteBookUrls: Set<String>,
)

/**
 * 在线书「同一部作品」分组的恢复决策。
 *
 * bookshelf.json 是全量快照，换源在源设备上表现为「删旧行、插新行」，因此快照里旧源行
 * 的存留是区分两种同名形状的信号：
 * - 备份组仍含本地在用的 bookUrl（交集）：多出的行是跨设备恢复累积出的重复，跳过不引入；
 * - 备份组与本地组完全不相交且备份组单行：作品整体换了源（换源结果传播），跟随云端——
 *   删本地组、采备份行。备份行自带的进度在源设备换源时已按新目录重定位，原样可用；
 * - 不相交但备份组多行：脏快照，无法仲裁哪行是新源，全部跳过、本地不动。
 *
 * 分组键取 [BookMatchKey] 规范化后的书名与作者（与手动入架查重同一标准），并要求两侧
 * 作者非空——跟随会删除本地行，匹配必须从严，作者缺失不放宽。文本/音频等内容类别位
 * 不同视为不同作品，避免同名跨媒介误删。备份行带 notShelf 标志时同样不跟随：那会把
 * 在架书替换成未上架的临时记录。
 */
internal fun planOnlineBookGroups(
    restoredBooks: List<Book>,
    existingBooks: List<Book>,
): OnlineBookGroupPlan {
    val existingGroups = existingBooks
        .mapNotNull { book -> onlineWorkKeyOf(book)?.let { it to book } }
        .groupBy({ it.first }, { it.second })
    val skipRestoredBookUrls = mutableSetOf<String>()
    val followDeleteBookUrls = mutableSetOf<String>()

    restoredBooks
        .mapNotNull { book -> onlineWorkKeyOf(book)?.let { it to book } }
        .groupBy({ it.first }, { it.second })
        .forEach { (key, restoredGroup) ->
            val existingGroup = existingGroups[key] ?: return@forEach
            val existingUrls = existingGroup.mapTo(hashSetOf()) { it.bookUrl }
            val distinctRestored = restoredGroup.distinctBy { it.bookUrl }
            if (distinctRestored.any { it.bookUrl in existingUrls }) {
                distinctRestored.forEach {
                    if (it.bookUrl !in existingUrls) skipRestoredBookUrls.add(it.bookUrl)
                }
                return@forEach
            }
            val winner = distinctRestored.singleOrNull() ?: run {
                distinctRestored.forEach { skipRestoredBookUrls.add(it.bookUrl) }
                return@forEach
            }
            if (winner.isType(BookType.notShelf)) {
                skipRestoredBookUrls.add(winner.bookUrl)
                return@forEach
            }
            existingGroup.forEach { followDeleteBookUrls.add(it.bookUrl) }
        }
    return OnlineBookGroupPlan(skipRestoredBookUrls, followDeleteBookUrls)
}

private data class OnlineWorkKey(
    val nameKey: String,
    val authorKey: String,
    val categoryBits: Int,
)

private fun onlineWorkKeyOf(book: Book): OnlineWorkKey? {
    if (book.isLocal) return null
    val nameKey = BookMatchKey.of(book.name)
    val authorKey = BookMatchKey.of(book.author)
    if (nameKey.isBlank() || authorKey.isBlank()) return null
    return OnlineWorkKey(
        nameKey = nameKey,
        authorKey = authorKey,
        categoryBits = book.type and (BookType.allBookType or BookType.video),
    )
}

private data class LocalBookResolution(
    val targetBookUrl: String,
    val duplicateBookUrls: Set<String>,
)

private fun resolveLocalBookTarget(
    restoredBook: Book,
    activeBooks: Collection<Book>,
    locationStatus: (String) -> LocalBookLocationStatus,
    normalizeLocation: (String) -> String,
): LocalBookResolution {
    val exactBook = activeBooks.firstOrNull { it.bookUrl == restoredBook.bookUrl }
    val restoredLocation = normalizeLocation(restoredBook.bookUrl)
    val sameLocationBooks = activeBooks.filter {
        it.isLocal && normalizeLocation(it.bookUrl) == restoredLocation
    }
    val hasSameLocationAlias = sameLocationBooks.any { it.bookUrl != restoredBook.bookUrl }
    if (sameLocationBooks.isNotEmpty() &&
        (locationStatus(restoredBook.bookUrl) == LocalBookLocationStatus.Available ||
                hasSameLocationAlias)
    ) {
        val targetBook = exactBook
            ?: sameLocationBooks.firstOrNull {
                locationStatus(it.bookUrl) == LocalBookLocationStatus.Available
            }
            ?: sameLocationBooks.first()
        return LocalBookResolution(
            targetBookUrl = targetBook.bookUrl,
            duplicateBookUrls = sameLocationBooks
                .asSequence()
                .map { it.bookUrl }
                .filterNot { it == targetBook.bookUrl }
                .toSet(),
        )
    }

    val compatibleBooks = activeBooks.filter { it.isRelocatedCopyOf(restoredBook) }
    val candidateBookUrls = buildSet {
        add(restoredBook.bookUrl)
        compatibleBooks.forEach { add(it.bookUrl) }
    }
    val candidateStatuses = candidateBookUrls.associateWith(locationStatus)
    val availableLocations = candidateStatuses
        .filterValues { it == LocalBookLocationStatus.Available }
        .keys
    val hasUnknownLocation = candidateStatuses.values.any {
        it == LocalBookLocationStatus.Unknown
    }
    if (!hasUnknownLocation && availableLocations.size == 1) {
        val targetBookUrl = availableLocations.single()
        return LocalBookResolution(
            targetBookUrl = targetBookUrl,
            duplicateBookUrls = compatibleBooks
                .asSequence()
                .map { it.bookUrl }
                .filterNot { it == targetBookUrl }
                .filter {
                    candidateStatuses[it] == LocalBookLocationStatus.Missing
                }
                .toSet(),
        )
    }

    return LocalBookResolution(
        targetBookUrl = exactBook?.bookUrl ?: restoredBook.bookUrl,
        duplicateBookUrls = emptySet(),
    )
}

private fun Book.isRelocatedCopyOf(other: Book): Boolean {
    return isLocal &&
            originName.isNotBlank() &&
            originName == other.originName &&
            name.isNotBlank() &&
            name == other.name &&
            author == other.author
}

internal fun normalizeLocalBookLocation(bookUrl: String): String {
    val value = bookUrl.trim()
    if (value.isEmpty()) return value
    if (value.startsWith("content://", ignoreCase = true)) return value
    val path = if (value.startsWith("file://", ignoreCase = true)) {
        runCatching { File(URI(value)).path }
            .getOrElse { value.substringAfter("file://") }
    } else {
        value
    }
    return runCatching { File(path).canonicalPath }
        .getOrElse { File(path).absolutePath }
}
