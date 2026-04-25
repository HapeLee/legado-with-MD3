package io.legado.app.ui.book.group

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.repository.BookGroupRepository
import io.legado.app.domain.usecase.RemoveBookGroupAssignmentUseCase

class GroupViewModel(
    application: Application,
    private val bookGroupRepository: BookGroupRepository,
    private val removeBookGroupAssignmentUseCase: RemoveBookGroupAssignmentUseCase
) : BaseViewModel(application) {

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
        cover: String?,
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
                order = bookGroupRepository.getMaxOrder().plus(1)
            )
            bookGroupRepository.getByID(groupId) ?: removeBookGroupAssignmentUseCase.execute(groupId)
            bookGroupRepository.insert(bookGroup)
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

}
