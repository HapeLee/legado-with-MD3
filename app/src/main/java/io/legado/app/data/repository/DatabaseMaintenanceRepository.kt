package io.legado.app.data.repository

import io.legado.app.data.AppDatabase
import io.legado.app.domain.gateway.DatabaseMaintenanceGateway

class DatabaseMaintenanceRepository(
    private val appDatabase: AppDatabase
) : DatabaseMaintenanceGateway {
    override fun shrink() {
        appDatabase.openHelper.writableDatabase.execSQL("VACUUM")
    }
}
