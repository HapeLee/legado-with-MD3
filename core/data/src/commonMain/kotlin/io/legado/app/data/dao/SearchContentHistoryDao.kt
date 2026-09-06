package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.SearchContentHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchContentHistoryDao {

    @Query("SELECT * FROM search_content_history ORDER BY time DESC")
    fun getAll(): Flow<List<SearchContentHistory>>

    @Query("SELECT * FROM search_content_history WHERE bookName = :bookName AND bookAuthor = :bookAuthor ORDER BY time DESC")
    fun getByBook(bookName: String, bookAuthor: String): Flow<List<SearchContentHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(searchContentHistory: SearchContentHistory)

    @Query("DELETE FROM search_content_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM search_content_history WHERE bookName = :bookName AND bookAuthor = :bookAuthor")
    suspend fun deleteByBook(bookName: String, bookAuthor: String)

    @Query("DELETE FROM search_content_history")
    suspend fun deleteAll()

    @Query("SELECT * FROM search_content_history WHERE bookName = :bookName AND bookAuthor = :bookAuthor AND `query` = :query")
    suspend fun get(bookName: String, bookAuthor: String, query: String): SearchContentHistory?
}
