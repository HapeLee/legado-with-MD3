package io.legado.app.data.repository

import androidx.room.withTransaction
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.TagGroupRule
import io.legado.app.domain.gateway.BookGroupMutationGateway
import io.legado.app.domain.model.BookGroupUpdate
import io.legado.app.domain.model.NewBookGroup
import io.legado.app.domain.model.TagGroupRuleUpdate

class BookGroupMutationRepository(
    private val database: AppDatabase,
    private val tagGroupRuleApplier: TagGroupRuleApplier,
) : BookGroupMutationGateway {

    override suspend fun addGroup(group: NewBookGroup) {
        group.pattern?.takeIf(String::isNotBlank)?.let(::Regex)

        database.withTransaction {
            val groupDao = database.bookGroupDao
            val groupId = groupDao.getUnusedId()
            val bookGroup = BookGroup(
                groupId = groupId,
                groupName = group.groupName,
                cover = group.cover,
                bookSort = group.bookSort,
                enableRefresh = group.enableRefresh,
                isPrivate = group.isPrivate,
                order = groupDao.maxOrder.plus(1),
            )

            if (groupDao.getByID(groupId) == null) {
                database.bookDao.removeGroup(groupId)
            }
            groupDao.insert(bookGroup)

            if (!group.pattern.isNullOrBlank()) {
                database.tagGroupRuleDao.insert(
                    TagGroupRule(
                        groupName = group.groupName,
                        pattern = group.pattern,
                        order = database.tagGroupRuleDao.maxOrder,
                    )
                )
                tagGroupRuleApplier.applyInCurrentTransaction()
            }
        }
    }

    override suspend fun saveGroup(
        bookGroup: BookGroupUpdate,
        ruleToSave: TagGroupRuleUpdate?,
        ruleIdToDelete: Long?,
    ) {
        ruleToSave?.pattern?.let(::Regex)

        database.withTransaction {
            database.bookGroupDao.update(bookGroup.toEntity())
            ruleToSave?.let { upsertTagGroupRule(it) }
            ruleIdToDelete?.let { id ->
                database.tagGroupRuleDao.getById(id)?.let { database.tagGroupRuleDao.delete(it) }
            }
            if (ruleToSave != null || ruleIdToDelete != null) {
                tagGroupRuleApplier.applyInCurrentTransaction()
            }
        }
    }

    override suspend fun saveTagGroupRule(rule: TagGroupRuleUpdate) {
        Regex(rule.pattern)
        database.withTransaction {
            upsertTagGroupRule(rule)
            tagGroupRuleApplier.applyInCurrentTransaction()
        }
    }

    override suspend fun deleteTagGroupRule(ruleId: Long) {
        database.withTransaction {
            database.tagGroupRuleDao.getById(ruleId)?.let { database.tagGroupRuleDao.delete(it) }
            tagGroupRuleApplier.applyInCurrentTransaction()
        }
    }

    private suspend fun upsertTagGroupRule(rule: TagGroupRuleUpdate) {
        val ruleDao = database.tagGroupRuleDao
        val existing = ruleDao.getByGroupName(rule.groupName)
        if (existing != null) {
            ruleDao.update(rule.toEntity(id = existing.id))
        } else {
            ruleDao.insert(rule.toEntity())
        }
    }

    private fun BookGroupUpdate.toEntity() = BookGroup(
        groupId = groupId,
        groupName = groupName,
        cover = cover,
        order = order,
        enableRefresh = enableRefresh,
        show = show,
        bookSort = bookSort,
        isPrivate = isPrivate,
    )

    private fun TagGroupRuleUpdate.toEntity(id: Long = this.id) = TagGroupRule(
        id = id,
        pattern = pattern,
        groupName = groupName,
        order = order,
    )
}
