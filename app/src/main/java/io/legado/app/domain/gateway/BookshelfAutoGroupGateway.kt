package io.legado.app.domain.gateway

import io.legado.app.domain.model.BookshelfAutoGroupApplyResult
import io.legado.app.domain.model.BookshelfAutoGroupPlan
import io.legado.app.domain.model.BookshelfAutoGroupSource

interface BookshelfAutoGroupGateway {

    suspend fun loadSource(): BookshelfAutoGroupSource

    /** Applies the complete plan atomically. */
    suspend fun applyPlan(plan: BookshelfAutoGroupPlan): BookshelfAutoGroupApplyResult
}
