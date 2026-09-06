package io.legado.app.data.dao

import androidx.room.*
import io.legado.app.data.entities.SearchKeyword
import kotlinx.coroutines.flow.Flow


@Dao
interface SearchKeywordDao {

    @Query("SELECT * FROM search_keywords")
    suspend fun all(): List<SearchKeyword>

    @Query("SELECT * FROM search_keywords ORDER BY usage DESC")
    fun flowByUsage(): Flow<List<SearchKeyword>>

    @Query("SELECT * FROM search_keywords ORDER BY lastUseTime DESC")
    fun flowByTime(): Flow<List<SearchKeyword>>

    @Query("SELECT * FROM search_keywords where word like '%'||:key||'%' ORDER BY usage DESC")
    fun flowSearch(key: String): Flow<List<SearchKeyword>>

    @Query("select * from search_keywords where word = :key")
    suspend fun get(key: String): SearchKeyword?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg keywords: SearchKeyword)

    @Update
    suspend fun update(vararg keywords: SearchKeyword)

    @Delete
    suspend fun delete(vararg keywords: SearchKeyword)

    @Query("DELETE FROM search_keywords")
    suspend fun deleteAll()

}