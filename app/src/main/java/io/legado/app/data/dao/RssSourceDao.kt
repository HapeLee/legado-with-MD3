package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.constant.AppPattern
import io.legado.app.data.entities.RssSource
import io.legado.app.utils.cnCompare
import io.legado.app.utils.splitNotBlank
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

@Dao
interface RssSourceDao {

    @Query("select * from rssSources where sourceUrl = :key")
    suspend fun getByKey(key: String): RssSource?

    @Query("select * from rssSources where sourceUrl in (:sourceUrls)")
    suspend fun getRssSources(vararg sourceUrls: String): List<RssSource>

    @Query("SELECT * FROM rssSources order by customOrder")
    suspend fun all(): List<RssSource>

    @Query("select count(sourceUrl) from rssSources")
    suspend fun size(): Int

    @Query("SELECT * FROM rssSources order by customOrder")
    fun flowAll(): Flow<List<RssSource>>

    @Query(
        """SELECT * FROM rssSources
        where sourceName like '%' || :key || '%' 
        or sourceUrl like '%' || :key || '%' 
        or sourceGroup like '%' || :key || '%'
        or sourceComment like '%' || :key || '%'
        order by customOrder"""
    )
    fun flowSearch(key: String): Flow<List<RssSource>>

    @Query(
        """SELECT * FROM rssSources 
        where (sourceGroup = :key
        or sourceGroup like :key || ',%' 
        or sourceGroup like  '%,' || :key
        or sourceGroup like  '%,' || :key || ',%')
        order by customOrder"""
    )
    fun flowGroupSearch(key: String): Flow<List<RssSource>>

    @Query("SELECT * FROM rssSources where enabled = 1 order by customOrder")
    fun flowEnabled(): Flow<List<RssSource>>

    @Query("SELECT * FROM rssSources where enabled = 0 order by customOrder")
    fun flowDisabled(): Flow<List<RssSource>>

    @Query("select * from rssSources where loginUrl is not null and loginUrl != ''")
    fun flowLogin(): Flow<List<RssSource>>

    @Query("select * from rssSources where sourceGroup is null or sourceGroup = '' or sourceGroup like '%未分组%'")
    fun flowNoGroup(): Flow<List<RssSource>>

    @Query(
        """SELECT * FROM rssSources 
        where enabled = 1 
        and (sourceName like '%' || :searchKey || '%' 
            or sourceGroup like '%' || :searchKey || '%' 
            or sourceUrl like '%' || :searchKey || '%'
            or sourceComment like '%' || :searchKey || '%') 
        order by customOrder"""
    )
    fun flowEnabled(searchKey: String): Flow<List<RssSource>>

    @Query(
        """SELECT * FROM rssSources 
        where enabled = 1 and (sourceGroup = :searchKey
        or sourceGroup like :searchKey || ',%' 
        or sourceGroup like  '%,' || :searchKey
        or sourceGroup like  '%,' || :searchKey || ',%') 
        order by customOrder"""
    )
    fun flowEnabledByGroup(searchKey: String): Flow<List<RssSource>>

    @Query("select distinct sourceGroup from rssSources where trim(sourceGroup) <> ''")
    fun flowGroupsUnProcessed(): Flow<List<String>>

    @Query("select distinct sourceGroup from rssSources where trim(sourceGroup) <> '' and enabled = 1")
    fun flowEnabledGroupsUnProcessed(): Flow<List<String>>

    @Query("select distinct sourceGroup from rssSources where trim(sourceGroup) <> ''")
    suspend fun allGroupsUnProcessed(): List<String>

    @Query("select min(customOrder) from rssSources")
    suspend fun minOrder(): Int

    @Query("select max(customOrder) from rssSources")
    suspend fun maxOrder(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg rssSource: RssSource)

    @Update
    suspend fun update(vararg rssSource: RssSource)

    @Delete
    suspend fun delete(vararg rssSource: RssSource)

    @Query("delete from rssSources where sourceUrl = :sourceUrl")
    suspend fun delete(sourceUrl: String)

    @Query("delete from rssSources where sourceGroup like 'legado'")
    suspend fun deleteDefault()

    @Query("select * from rssSources where sourceGroup is null or sourceGroup = ''")
    suspend fun noGroup(): List<RssSource>

    @Query("select * from rssSources where sourceGroup like '%' || :group || '%'")
    suspend fun getByGroup(group: String): List<RssSource>

    @Query("select exists(select 1 from rssSources where sourceUrl = :key)")
    suspend fun has(key: String): Boolean

    @Query("update rssSources set enabled = :enable where sourceUrl = :sourceUrl")
    suspend fun enable(sourceUrl: String, enable: Boolean)

    @Query("UPDATE rssSources SET redirectPolicy = :redirectPolicy WHERE sourceUrl = :sourceUrl")
    suspend fun updateRedirectPolicy(sourceUrl: String, redirectPolicy: String)

    private fun dealGroups(list: List<String>): List<String> {
        val groups = linkedSetOf<String>()
        list.forEach {
            it.splitNotBlank(AppPattern.splitGroupRegex).forEach { group ->
                groups.add(group)
            }
        }
        return groups.sortedWith { o1, o2 ->
            o1.cnCompare(o2)
        }
    }

    suspend fun allGroups(): List<String> = dealGroups(allGroupsUnProcessed())

    fun flowGroups(): Flow<List<String>> {
        return flowGroupsUnProcessed().map { list ->
            dealGroups(list)
        }.flowOn(IO)
    }

    fun flowEnabledGroups(): Flow<List<String>> {
        return flowEnabledGroupsUnProcessed().map { list ->
            dealGroups(list)
        }.flowOn(IO)
    }

}
