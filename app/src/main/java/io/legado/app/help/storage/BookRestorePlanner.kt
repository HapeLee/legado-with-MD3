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
 * - 备份组与本地组完全不相交且备份组单行：可能是作品整体换了源（换源结果传播）——
 *   跟随云端，删本地组、采备份行；也可能是另一台设备独立入架的同一作品，与换源迁移
 *   快照同形。仅凭快照无法区分，跟随必须过双门槛（见下），不达标时宁可少跟随：
 *   ① 备份行有使用痕迹（durChapterIndex/durChapterPos > 0）——换源迁移会把进度带到
 *      新行，从未打开过的行不该成为删除本机的依据；② 备份行 durChapterTime 不早于
 *      本地组最近阅读时间——本机更近的阅读状态优先，避免删掉本机较新的进度。
 *   （durChapterTime 建行即有默认值，只作新近度比较，不作使用痕迹判定。）
 * - 不相交但备份组多行在架行：脏快照，无法仲裁哪行是新源，全部跳过、本地不动。
 *
 * 在架行优先（notShelf 处理）：备份组里的临时记录不参与交集判定、不能单独成为跟随
 * 对象——否则「本地只有临时记录 X，备份含临时 X 与在架 Y」时 X 的交集会把 Y 挡在
 * 外面，恢复后书架仍然缺这部作品。临时行的归宿只有两种：被在架行覆盖（本地组随之
 * 清理），或作为重复跳过。本地组全是临时记录时，任何备份在架行都直接上位。
 *
 * 分组键取 [BookMatchKey] 规范化后的书名与作者（与手动入架查重同一标准），并要求两侧
 * 作者非空——跟随会删除本地行，匹配必须从严，作者缺失不放宽。文本/音频等内容类别位
 * 不同视为不同作品，避免同名跨媒介误删。
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
            val shelvedRestored = distinctRestored.filterNot { it.isType(BookType.notShelf) }

            fun skipRestOfGroup() {
                distinctRestored.forEach {
                    if (it.bookUrl !in existingUrls) skipRestoredBookUrls.add(it.bookUrl)
                }
            }

            if (shelvedRestored.isEmpty()) {
                // 全临时备份组：本地已有该作品的任何行（在架或临时），备份临时行
                // 都只会引入重复，一律跳过；本地完全没有该作品时不进本分支
                // （existingGroups 缺键早已返回），临时记录按常规导入
                skipRestOfGroup()
                return@forEach
            }
            val shelvedUrls = shelvedRestored.mapTo(hashSetOf()) { it.bookUrl }
            if (shelvedUrls.any { it in existingUrls }) {
                // 在架行命中本地在用行：本组其余行（含临时行）按累积重复跳过
                skipRestOfGroup()
                return@forEach
            }
            val winner = shelvedRestored.singleOrNull() ?: run {
                // 多条在架行且均不在本地：脏快照，全组跳过（含临时行）
                distinctRestored.forEach { skipRestoredBookUrls.add(it.bookUrl) }
                return@forEach
            }
            if (existingGroup.all { it.isType(BookType.notShelf) }) {
                // 本地只有临时记录：在架行整体上位，清掉临时组（进度无损失）。
                // 组内其余备份行（临时行）必须跳过：正常导入路径的 upsert 会把
                // 同 bookUrl 的行写回，等于撤销刚做的跟随删除
                existingGroup.forEach { followDeleteBookUrls.add(it.bookUrl) }
                distinctRestored.forEach {
                    if (it.bookUrl != winner.bookUrl) skipRestoredBookUrls.add(it.bookUrl)
                }
                return@forEach
            }
            val winnerUsed = winner.durChapterIndex > 0 || winner.durChapterPos > 0
            val localLatestReadTime = existingGroup.maxOf { it.durChapterTime }
            if (winnerUsed && winner.durChapterTime >= localLatestReadTime) {
                existingGroup.forEach { followDeleteBookUrls.add(it.bookUrl) }
                // 同上：跟随时组内其余备份行一并跳过，防止 upsert 撤销删除
                distinctRestored.forEach {
                    if (it.bookUrl != winner.bookUrl) skipRestoredBookUrls.add(it.bookUrl)
                }
            } else {
                // 备份行未使用或本地更新：不删本机、也不引入，避免同名重复与进度损失
                distinctRestored.forEach { skipRestoredBookUrls.add(it.bookUrl) }
            }
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
