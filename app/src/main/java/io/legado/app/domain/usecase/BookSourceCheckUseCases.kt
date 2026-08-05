package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.BookSourceCheckGateway

class StartBookSourceCheckUseCase(private val gateway: BookSourceCheckGateway) {
    suspend operator fun invoke(sourceIds: Set<String>, keyword: String) =
        gateway.check(sourceIds, keyword)
}
