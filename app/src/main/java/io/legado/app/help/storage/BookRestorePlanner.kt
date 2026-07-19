package io.legado.app.help.storage

import io.legado.app.data.entities.Book
import io.legado.app.help.book.isLocal
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

    restoredBooks.forEach { restoredBook ->
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
