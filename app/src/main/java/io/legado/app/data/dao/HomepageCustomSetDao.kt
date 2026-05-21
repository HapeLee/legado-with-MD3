package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.HomepageCustomSet
import kotlinx.coroutines.flow.Flow

@Dao
interface HomepageCustomSetDao {

    @Query("SELECT * FROM homepage_custom_sets ORDER BY sortOrder ASC")
    fun flowAll(): Flow<List<HomepageCustomSet>>

    @Query("SELECT * FROM homepage_custom_sets WHERE id = :id")
    suspend fun getById(id: String): HomepageCustomSet?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(customSet: HomepageCustomSet)

    @Query("UPDATE homepage_custom_sets SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("UPDATE homepage_custom_sets SET sortOrder = :order WHERE id = :id")
    suspend fun setSortOrder(id: String, order: Int)

    @androidx.room.Transaction
    suspend fun batchSetSortOrders(orders: Map<String, Int>) {
        orders.forEach { (id, order) -> setSortOrder(id, order) }
    }

    @Query("DELETE FROM homepage_custom_sets WHERE id = :id")
    suspend fun delete(id: String)
}
