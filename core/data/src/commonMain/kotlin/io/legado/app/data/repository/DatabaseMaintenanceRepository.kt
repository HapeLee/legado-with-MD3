package io.legado.app.data.repository

import androidx.room.execSQL
import androidx.room.useWriterConnection
import io.legado.app.data.AppDatabase
import io.legado.app.domain.gateway.DatabaseMaintenanceGateway

class DatabaseMaintenanceRepository(
    private val appDatabase: AppDatabase
) : DatabaseMaintenanceGateway {
    override suspend fun shrink() {
        appDatabase.useWriterConnection { it.execSQL("VACUUM") }
    }
}
