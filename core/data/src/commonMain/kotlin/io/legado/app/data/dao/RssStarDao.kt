package io.legado.app.data.dao

import androidx.room.*
import io.legado.app.data.entities.RssStar
import kotlinx.coroutines.flow.Flow

@Dao
interface RssStarDao {

    @Query("select * from rssStars order by starTime desc")
    suspend fun all(): List<RssStar>

    @Query("select `group` from rssStars group by `group` order by `group`")
    fun flowGroups(): Flow<List<String>>

    @Query("select * from rssStars where `group` = :group order by starTime desc")
    fun flowByGroup(group: String): Flow<List<RssStar>>

    @Query("select * from rssStars where origin = :origin and link = :link")
    suspend fun get(origin: String, link: String): RssStar?

    @Query("select * from rssStars order by starTime desc")
    fun liveAll(): Flow<List<RssStar>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg rssStar: RssStar)

    @Update
    suspend fun update(vararg rssStar: RssStar)

    @Query("update rssStars set origin = :origin where origin = :oldOrigin")
    suspend fun updateOrigin(origin: String, oldOrigin: String)

    @Query("delete from rssStars where origin = :origin")
    suspend fun delete(origin: String)

    @Query("delete from rssStars where origin = :origin and link = :link")
    suspend fun delete(origin: String, link: String)

    @Query("delete from rssStars where `group` = :group")
    suspend fun deleteByGroup(group: String)

    @Query("delete from rssStars")
    suspend fun deleteAll()
}