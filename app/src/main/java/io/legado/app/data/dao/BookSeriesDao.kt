package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.BookSeries
import kotlinx.coroutines.flow.Flow

@Dao
interface BookSeriesDao {

    @Query("select * from book_series")
    fun flowAll(): Flow<List<BookSeries>>

    /** 备份用；调用方保证在 IO 线程 */
    @Query("select * from book_series")
    fun all(): List<BookSeries>

    @Query("select * from book_series where name = :name")
    suspend fun get(name: String): BookSeries?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(series: BookSeries)

    @Query("update book_series set cover = :cover where name = :name")
    suspend fun updateCover(name: String, cover: String?)

    @Query("update book_series set name = :newName where name = :oldName")
    suspend fun rename(oldName: String, newName: String)

    @Query("delete from book_series where name = :name")
    suspend fun delete(name: String)
}
