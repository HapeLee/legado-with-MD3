package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.HomepageModule
import kotlinx.coroutines.flow.Flow

@Dao
interface HomepageModuleDao {

    @Query("SELECT * FROM homepage_modules WHERE isEnabled = 1 ORDER BY sortOrder ASC")
    fun flowEnabled(): Flow<List<HomepageModule>>

    @Query("SELECT * FROM homepage_modules WHERE sourceUrl = :sourceUrl ORDER BY sortOrder ASC")
    fun flowBySource(sourceUrl: String): Flow<List<HomepageModule>>

    @Query("SELECT * FROM homepage_modules WHERE id = :id")
    suspend fun getById(id: String): HomepageModule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(modules: List<HomepageModule>)

    @Query("UPDATE homepage_modules SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("UPDATE homepage_modules SET sortOrder = :order WHERE id = :id")
    suspend fun setSortOrder(id: String, order: Int)

    @androidx.room.Transaction
    suspend fun batchSetSortOrders(orders: Map<String, Int>) {
        orders.forEach { (id, order) -> setSortOrder(id, order) }
    }

    @Query("UPDATE homepage_modules SET customSetTitle = :title WHERE id = :id")
    suspend fun setCustomSetTitle(id: String, title: String?)

    @Query("UPDATE homepage_modules SET customSetId = :setId WHERE id = :id")
    suspend fun setCustomSetId(id: String, setId: String?)

    @Query("DELETE FROM homepage_modules WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM homepage_modules WHERE customSetId = :setId")
    suspend fun deleteByCustomSetId(setId: String)

    @Query("DELETE FROM homepage_modules WHERE sourceUrl = :sourceUrl AND isUserCreated = 0 AND id NOT IN (:currentIds)")
    suspend fun deleteStale(sourceUrl: String, currentIds: List<String>)

    @Query("SELECT * FROM homepage_modules ORDER BY sortOrder ASC")
    fun flowAll(): Flow<List<HomepageModule>>
}
