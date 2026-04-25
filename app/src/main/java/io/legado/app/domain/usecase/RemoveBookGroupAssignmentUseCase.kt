package io.legado.app.domain.usecase

import io.legado.app.domain.repository.BookDomainRepository

class RemoveBookGroupAssignmentUseCase(
    private val bookRepository: BookDomainRepository
) {

    suspend fun execute(groupId: Long) {
        bookRepository.removeGroupFromBooks(groupId)
    }
}
