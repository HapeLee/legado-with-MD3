package io.legado.app.data.repository

import io.legado.app.data.AppDatabase
import io.legado.app.domain.gateway.AppStartupGateway

class AppStartupRepository(
    private val appDatabase: AppDatabase
) : AppStartupGateway {

    override suspend fun deleteNotShelfBooks() {
        appDatabase.bookDao.deleteNotShelfBook()
    }
}
