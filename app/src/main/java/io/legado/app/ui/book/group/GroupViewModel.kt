package io.legado.app.ui.book.group

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.TagGroupRule
import io.legado.app.data.repository.BookGroupRepository
import io.legado.app.data.repository.TagGroupRuleRepository
import io.legado.app.domain.usecase.RemoveBookGroupAssignmentUseCase

class GroupViewModel(
    application: Application,
    private val bookGroupRepository: BookGroupRepository,
    private val removeBookGroupAssignmentUseCase: RemoveBookGroupAssignmentUseCase
) : BaseViewModel(application) {

    private val tagGroupRuleRepository = TagGroupRuleRepository()

    fun upGroup(vararg bookGroup: BookGroup, finally: (() -> Unit)? = null) {
        execute {
            bookGroupRepository.update(*bookGroup)
        }.onFinally {
            finally?.invoke()
        }
    }

    fun addGroup(
        groupName: String,
        bookSort: Int,
        enableRefresh: Boolean,
        isPrivate: Boolean,
        cover: String?,
        pattern: String? = null,
        finally: () -> Unit
    ) {
        execute {
            val groupId = bookGroupRepository.getUnusedId()
            val bookGroup = BookGroup(
                groupId = groupId,
                groupName = groupName,
                cover = cover,
                bookSort = bookSort,
                enableRefresh = enableRefresh,
                isPrivate = isPrivate,
                order = bookGroupRepository.getMaxOrder().plus(1)
            )
            bookGroupRepository.getByID(groupId) ?: removeBookGroupAssignmentUseCase.execute(groupId)
            bookGroupRepository.insert(bookGroup)
            if (!pattern.isNullOrBlank()) {
                val tagRule = TagGroupRule(
                    groupName = groupName,
                    pattern = pattern,
                    order = tagGroupRuleRepository.getMaxOrder()
                )
                tagGroupRuleRepository.insert(tagRule)
            }
        }.onFinally {
            finally()
        }
    }

    fun delGroup(bookGroup: BookGroup, finally: () -> Unit) {
        execute {
            bookGroupRepository.delete(bookGroup)
            removeBookGroupAssignmentUseCase.execute(bookGroup.groupId)
        }.onFinally {
            finally()
        }
    }

    fun clearCover(bookGroup: BookGroup, finally: () -> Unit) {
        execute {
            bookGroupRepository.clearCover(bookGroup.groupId)
        }.onFinally {
            finally()
        }
    }

    suspend fun getTagGroupRule(groupName: String): TagGroupRule? {
        return tagGroupRuleRepository.getByGroupName(groupName)
    }

    fun saveTagGroupRule(rule: TagGroupRule, finally: (() -> Unit)? = null) {
        execute {
            val existing = tagGroupRuleRepository.getByGroupName(rule.groupName)
            if (existing != null) {
                tagGroupRuleRepository.update(rule.copy(id = existing.id))
            } else {
                tagGroupRuleRepository.insert(rule)
            }
        }.onFinally {
            finally?.invoke()
        }
    }

    fun deleteTagGroupRule(rule: TagGroupRule, finally: (() -> Unit)? = null) {
        execute {
            tagGroupRuleRepository.delete(rule)
        }.onFinally {
            finally?.invoke()
        }
    }

}
