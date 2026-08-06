package io.legado.app.data.repository

import androidx.room.withTransaction
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.domain.gateway.BookshelfAutoGroupGateway
import io.legado.app.domain.model.BookshelfAutoGroupApplyResult
import io.legado.app.domain.model.BookshelfAutoGroupBook
import io.legado.app.domain.model.BookshelfAutoGroupErrorReason
import io.legado.app.domain.model.BookshelfAutoGroupException
import io.legado.app.domain.model.BookshelfAutoGroupPlan
import io.legado.app.domain.model.BookshelfAutoGroupSource
import io.legado.app.help.book.isNotShelf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BookshelfAutoGroupRepository(
    private val database: AppDatabase,
) : BookshelfAutoGroupGateway {

    override suspend fun loadSource(): BookshelfAutoGroupSource = withContext(Dispatchers.IO) {
        val groupDao = database.bookGroupDao
        val allGroups = groupDao.all
        val publicGroups = allGroups.filter { it.isUserGroup() && !it.isPrivate }
        val privateGroupMask = allGroups
            .asSequence()
            .filter { it.isUserGroup() && it.isPrivate }
            .fold(0L) { mask, group -> mask or group.groupId }
        val books = database.bookDao.getAll()
            .filterNot { it.isNotShelf }
            // Private shelf metadata must not be sent to an external AI provider.
            .filter { book -> book.group and privateGroupMask == 0L }
            .sortedWith(compareBy<Book> { it.name }.thenBy { it.author })
        BookshelfAutoGroupSource(
            books = books.map { book ->
                BookshelfAutoGroupBook(
                    bookUrl = book.bookUrl,
                    name = book.name,
                    author = book.author,
                    intro = book.groupingIntro(),
                    kind = book.customTag ?: book.kind.orEmpty(),
                    currentGroupNames = groupDao.getGroupNames(book.group),
                )
            },
            existingGroupNames = publicGroups
                .map(BookGroup::groupName)
                .filter(String::isNotBlank),
        )
    }

    override suspend fun applyPlan(
        plan: BookshelfAutoGroupPlan,
    ): BookshelfAutoGroupApplyResult = withContext(Dispatchers.IO) {
        database.withTransaction {
            val groupDao = database.bookGroupDao
            val bookDao = database.bookDao
            val allGroups = groupDao.all
            val privateGroupMask = allGroups
                .asSequence()
                .filter { it.isUserGroup() && it.isPrivate }
                .fold(0L) { mask, group -> mask or group.groupId }
            val existingGroups = allGroups
                .filter { it.isUserGroup() && !it.isPrivate }
                .associateBy(BookGroup::groupName)
                .toMutableMap()
            var createdGroupCount = 0
            var reusedGroupCount = 0
            var missingBookCount = 0

            // Resolve current rows before creating groups so stale plans cannot create empty groups.
            val applicableGroups = plan.groups.mapNotNull { group ->
                val books = group.books.mapNotNull { plannedBook ->
                    bookDao.getBook(plannedBook.bookUrl).also { entity ->
                        if (entity == null) missingBookCount++
                    }
                }
                group.takeIf { books.isNotEmpty() }?.let { it to books }
            }

            val newGroupCount = applicableGroups
                .asSequence()
                .map { (group, _) -> group.name }
                .distinct()
                .count { it !in existingGroups }
            val occupiedGroupBits = allGroups.count { it.isUserGroup() }
            val availableGroupBits = (MAX_USER_GROUPS - occupiedGroupBits).coerceAtLeast(0)
            if (newGroupCount > availableGroupBits) {
                throw BookshelfAutoGroupException(
                    BookshelfAutoGroupErrorReason.GroupCapacityExceeded
                )
            }

            val targetGroupIds = applicableGroups.associate { (group, _) ->
                val existing = existingGroups[group.name]
                val groupId = if (existing != null) {
                    reusedGroupCount++
                    existing.groupId
                } else {
                    val newGroupId = groupDao.getUnusedId()
                    // A zero id means all 64 group bits are already occupied; Long.MIN_VALUE
                    // is the reserved private-group bit and must not back a public group.
                    if (newGroupId == 0L || newGroupId == Long.MIN_VALUE) {
                        throw BookshelfAutoGroupException(
                            BookshelfAutoGroupErrorReason.GroupCapacityExceeded
                        )
                    }
                    val newGroup = BookGroup(
                        groupId = newGroupId,
                        groupName = group.name,
                        order = groupDao.maxOrder.plus(1),
                        enableRefresh = true,
                        show = true,
                        bookSort = -1,
                        isPrivate = false,
                    )
                    // Clear any stale bit before reusing a historical group id.
                    bookDao.removeGroup(newGroupId)
                    groupDao.insert(newGroup)
                    existingGroups[group.name] = newGroup
                    createdGroupCount++
                    newGroupId
                }
                group.key to groupId
            }

            val updates = applicableGroups.flatMap { (group, books) ->
                val targetGroupId = targetGroupIds[group.key] ?: return@flatMap emptyList()
                books.mapNotNull { entity ->
                    // Auto-grouping replaces public memberships but never removes private ones.
                    val updatedGroup = (entity.group and privateGroupMask) or targetGroupId
                    entity.takeIf { it.group != updatedGroup }?.copy(group = updatedGroup)
                }
            }
            if (updates.isNotEmpty()) {
                bookDao.update(*updates.toTypedArray())
            }

            BookshelfAutoGroupApplyResult(
                createdGroupCount = createdGroupCount,
                reusedGroupCount = reusedGroupCount,
                updatedBookCount = updates.size,
                ignoredBookCount = plan.ignoredBooks.size + missingBookCount,
            )
        }
    }

    private fun Book.groupingIntro(): String {
        return (customIntro ?: intro ?: listIntro).orEmpty()
            .replace(Regex("\\s+"), " ")
            .take(MAX_INTRO_CHARS)
    }

    private fun BookGroup.isUserGroup(): Boolean {
        return groupId > 0 || groupId == Long.MIN_VALUE
    }

    private companion object {
        const val MAX_INTRO_CHARS = 320
        const val MAX_USER_GROUPS = Long.SIZE_BITS
    }
}
