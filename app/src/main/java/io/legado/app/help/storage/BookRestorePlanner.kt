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

    fun cachedLocationStatus(bookUrl: String): LocalBookLocationStatus =
        locationStatusCache.getOrPut(bookUrl) { locationStatus(bookUrl) }

    filterConflictingOnlineBooks(restoredBooks, existingBooks).forEach { restoredBook ->
        if (ignoreLocalBook && restoredBook.isLocal) return@forEach

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

/**
 * 快照无法区分换源和另一设备独立入架。同作品已有在架副本时，只恢复已在架的 URL。
 * 本机只有临时副本时，可以恢复备份里唯一的在架副本，并保留其他 URL 的临时进度。
 */
private fun filterConflictingOnlineBooks(
    restoredBooks: List<Book>,
    existingBooks: List<Book>,
): List<Book> {
    val existingOnlineByUrl = existingBooks.filterNot { it.isLocal }.associateBy { it.bookUrl }
    val existingGroups = existingOnlineByUrl.values
        .mapNotNull { book -> onlineWorkKeyOf(book)?.let { it to book } }
        .groupBy({ it.first }, { it.second })
    val shelvedBackupUrls = restoredBooks
        .filterNot { it.isType(BookType.notShelf) }
        .mapNotNull { book -> onlineWorkKeyOf(book)?.let { it to book.bookUrl } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, urls) -> urls.toSet() }

    return restoredBooks.filter { restoredBook ->
        if (restoredBook.isLocal) return@filter true
        val isTemporary = restoredBook.isType(BookType.notShelf)
        val existingIsTemporary = existingOnlineByUrl[restoredBook.bookUrl]?.isType(BookType.notShelf)
        // URL 一致时沿用恢复，备份临时行不把本机在架书移出书架。
        if (existingIsTemporary == false) return@filter !isTemporary

        val key = onlineWorkKeyOf(restoredBook) ?: return@filter true
        val existingGroup = existingGroups[key] ?: return@filter true
        val backupUrls = shelvedBackupUrls[key].orEmpty()
        if (isTemporary) {
            return@filter existingIsTemporary == true && backupUrls.isEmpty()
        }
        backupUrls.singleOrNull() == restoredBook.bookUrl &&
            existingGroup.all { it.isType(BookType.notShelf) }
    }
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
