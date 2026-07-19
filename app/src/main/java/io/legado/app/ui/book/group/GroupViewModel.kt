package io.legado.app.ui.book.group

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.TagGroupRule
import io.legado.app.data.repository.BookGroupRepository
import io.legado.app.data.repository.TagGroupRuleRepository
import io.legado.app.domain.gateway.BookGroupMutationGateway
import io.legado.app.domain.model.BookGroupUpdate
import io.legado.app.domain.model.NewBookGroup
import io.legado.app.domain.model.TagGroupRuleUpdate
import io.legado.app.domain.usecase.RemoveBookGroupAssignmentUseCase
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

class GroupViewModel(
    application: Application,
    private val bookGroupRepository: BookGroupRepository,
    private val removeBookGroupAssignmentUseCase: RemoveBookGroupAssignmentUseCase,
    private val bookGroupMutationGateway: BookGroupMutationGateway,
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
        onError: ((Throwable) -> Unit)? = null,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                bookGroupMutationGateway.addGroup(
                    NewBookGroup(
                        groupName = groupName,
                        bookSort = bookSort,
                        enableRefresh = enableRefresh,
                        isPrivate = isPrivate,
                        cover = cover,
                        pattern = pattern,
                    )
                )
                onSuccess()
            } catch (error: Throwable) {
                currentCoroutineContext().ensureActive()
                onError?.invoke(error)
            }
        }
    }

    fun saveGroup(
        bookGroup: BookGroup,
        ruleToSave: TagGroupRule?,
        ruleToDelete: TagGroupRule?,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        viewModelScope.launch {
            try {
                bookGroupMutationGateway.saveGroup(
                    bookGroup = bookGroup.toUpdate(),
                    ruleToSave = ruleToSave?.toUpdate(),
                    ruleIdToDelete = ruleToDelete?.id,
                )
                onSuccess()
            } catch (error: Throwable) {
                currentCoroutineContext().ensureActive()
                onError(error)
            }
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
            bookGroupMutationGateway.saveTagGroupRule(rule.toUpdate())
        }.onFinally {
            finally?.invoke()
        }
    }

    fun deleteTagGroupRule(rule: TagGroupRule, finally: (() -> Unit)? = null) {
        execute {
            bookGroupMutationGateway.deleteTagGroupRule(rule.id)
        }.onFinally {
            finally?.invoke()
        }
    }

    private fun BookGroup.toUpdate() = BookGroupUpdate(
        groupId = groupId,
        groupName = groupName,
        cover = cover,
        order = order,
        enableRefresh = enableRefresh,
        show = show,
        bookSort = bookSort,
        isPrivate = isPrivate,
    )

    private fun TagGroupRule.toUpdate() = TagGroupRuleUpdate(
        id = id,
        pattern = pattern,
        groupName = groupName,
        order = order,
    )

}
