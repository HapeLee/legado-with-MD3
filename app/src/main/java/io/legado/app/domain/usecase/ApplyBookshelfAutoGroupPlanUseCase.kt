package io.legado.app.domain.usecase

import androidx.room.withTransaction
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.BookGroup
import io.legado.app.domain.model.BookshelfAutoGroupApplyResult
import io.legado.app.domain.model.BookshelfAutoGroupPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ApplyBookshelfAutoGroupPlanUseCase(
    private val database: AppDatabase,
) {

    suspend fun execute(plan: BookshelfAutoGroupPlan): BookshelfAutoGroupApplyResult =
        withContext(Dispatchers.IO) {
            require(plan.groups.isNotEmpty()) { "没有可执行的分组方案" }

            database.withTransaction {
                val groupDao = database.bookGroupDao
                val bookDao = database.bookDao
                val existingGroups = groupDao.all
                    .filter { it.groupId > 0 }
                    .associateBy { it.groupName }
                    .toMutableMap()
                var createdGroupCount = 0
                var reusedGroupCount = 0

                val targetGroupIds = plan.groups.associate { group ->
                    val existing = existingGroups[group.name]
                    val groupId = if (existing != null) {
                        reusedGroupCount++
                        existing.groupId
                    } else {
                        val newGroupId = groupDao.getUnusedId()
                        val newGroup = BookGroup(
                            groupId = newGroupId,
                            groupName = group.name,
                            order = groupDao.maxOrder.plus(1),
                            enableRefresh = true,
                            show = true,
                            bookSort = -1,
                            isPrivate = false,
                        )
                        bookDao.removeGroup(newGroupId)
                        groupDao.insert(newGroup)
                        existingGroups[group.name] = newGroup
                        createdGroupCount++
                        newGroupId
                    }
                    group.key to groupId
                }

                val updates = plan.groups.flatMap { group ->
                    val targetGroupId = targetGroupIds[group.key] ?: return@flatMap emptyList()
                    group.books.mapNotNull { book ->
                        bookDao.getBook(book.bookUrl)
                            ?.takeIf { it.group != targetGroupId }
                            ?.copy(group = targetGroupId)
                    }
                }

                if (updates.isNotEmpty()) {
                    bookDao.update(*updates.toTypedArray())
                }

                BookshelfAutoGroupApplyResult(
                    createdGroupCount = createdGroupCount,
                    reusedGroupCount = reusedGroupCount,
                    updatedBookCount = updates.size,
                    ignoredBookCount = plan.ignoredBooks.size,
                )
            }
        }
}
