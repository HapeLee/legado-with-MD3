package io.legado.app.domain.usecase

import io.legado.app.domain.repository.BookDomainRepository

class UpdateBooksGroupUseCase(
    private val bookRepository: BookDomainRepository
) {

    suspend fun replaceGroup(bookUrls: Set<String>, groupId: Long) {
        updateGroups(bookUrls) { groupId }
    }

    suspend fun updateGroups(bookUrls: Set<String>, transform: (Long) -> Long) {
        if (bookUrls.isEmpty()) return
        val updateGroups = bookRepository.getBookGroupAssignments(bookUrls).mapNotNull { book ->
            val targetGroup = transform(book.group)
            if (targetGroup == book.group) {
                null
            } else {
                book.copy(group = targetGroup)
            }
        }
        bookRepository.updateBookGroups(updateGroups)
    }
}
