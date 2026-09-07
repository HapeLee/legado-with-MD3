package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.legado.app.constant.AppPattern
import io.legado.app.core.platform.cnCompare
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.utils.splitNotBlank
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

@Dao
interface BookSourceDao {

    @Query("select * from book_sources_part order by customOrder asc")
    fun flowAll(): Flow<List<BookSourcePart>>

    @Query(
        """select bp.*
        from book_sources b join book_sources_part bp on b.bookSourceUrl = bp.bookSourceUrl 
        where b.bookSourceName like '%' || :searchKey || '%'
        or b.bookSourceGroup like '%' || :searchKey || '%'
        or b.bookSourceUrl like '%' || :searchKey || '%'
        or b.bookSourceComment like '%' || :searchKey || '%' 
        order by b.customOrder asc"""
    )
    fun flowSearch(searchKey: String): Flow<List<BookSourcePart>>

    @Query(
        """select * from book_sources 
        where bookSourceName like '%' || :searchKey || '%'
        or bookSourceGroup like '%' || :searchKey || '%'
        or bookSourceUrl like '%' || :searchKey || '%'
        or bookSourceComment like '%' || :searchKey || '%' 
        order by customOrder asc"""
    )
    suspend fun search(searchKey: String): List<BookSource>

    @Query(
        """select bp.*
        from book_sources b join book_sources_part bp on b.bookSourceUrl = bp.bookSourceUrl 
        where b.enabled = 1 
        and (b.bookSourceName like '%' || :searchKey || '%' 
        or b.bookSourceGroup like '%' || :searchKey || '%' 
        or b.bookSourceUrl like '%' || :searchKey || '%'  
        or b.bookSourceComment like '%' || :searchKey || '%')
        order by b.customOrder asc"""
    )
    fun flowSearchEnabled(searchKey: String): Flow<List<BookSourcePart>>

    @Query(
        """select * from book_sources_part 
        where bookSourceGroup = :searchKey
        or bookSourceGroup like :searchKey || ',%' 
        or bookSourceGroup like  '%,' || :searchKey
        or bookSourceGroup like  '%,' || :searchKey || ',%' 
        order by customOrder asc"""
    )
    fun flowGroupSearch(searchKey: String): Flow<List<BookSourcePart>>

    @Query(
        """select * from book_sources 
        where bookSourceGroup = :searchKey
        or bookSourceGroup like :searchKey || ',%' 
        or bookSourceGroup like  '%,' || :searchKey
        or bookSourceGroup like  '%,' || :searchKey || ',%' 
        order by customOrder asc"""
    )
    suspend fun groupSearch(searchKey: String): List<BookSource>

    @Query("select * from book_sources_part where enabled = 1 order by customOrder asc")
    fun flowEnabled(): Flow<List<BookSourcePart>>

    @Query("select * from book_sources_part where enabled = 0 order by customOrder asc")
    fun flowDisabled(): Flow<List<BookSourcePart>>

    @Query(
        """select * from book_sources
        where enabled = 1 and enabledExplore = 1 and homepageModules is not null
        order by customOrder asc"""
    )
    fun flowHomepageModules(): Flow<List<BookSource>>

    @Query(
        """select * from book_sources
        where enabled = 1 and enabledExplore = 1
        order by customOrder asc"""
    )
    fun flowExploreSources(): Flow<List<BookSource>>

    @Query(
        """select * from book_sources_part
        where enabled = 1 and enabledExplore = 1
        order by customOrder asc"""
    )
    fun flowExploreSourceParts(): Flow<List<BookSourcePart>>

    @Query(
        """select * from book_sources_part
        where enabledExplore = 1 and hasExploreUrl = 1 order by customOrder asc"""
    )
    fun flowExplore(): Flow<List<BookSourcePart>>

    @Query("select * from book_sources_part where hasLoginUrl = 1 order by customOrder asc")
    fun flowLogin(): Flow<List<BookSourcePart>>

    @Query(
        """select * from book_sources_part 
        where bookSourceGroup is null or bookSourceGroup = '' or bookSourceGroup like '%未分组%'
        order by customOrder asc"""
    )
    fun flowNoGroup(): Flow<List<BookSourcePart>>

    @Query("select * from book_sources_part where enabledExplore = 1 order by customOrder asc")
    fun flowEnabledExplore(): Flow<List<BookSourcePart>>

    @Query("select * from book_sources_part where enabledExplore = 0 order by customOrder asc")
    fun flowDisabledExplore(): Flow<List<BookSourcePart>>

    @Query(
        """select * from book_sources_part 
        where enabledExplore = 1 
        and hasExploreUrl = 1 
        and (bookSourceGroup like '%' || :key || '%' 
            or bookSourceName like '%' || :key || '%') 
        order by customOrder asc"""
    )
    fun flowExplore(key: String): Flow<List<BookSourcePart>>

    @Query(
        """select * from book_sources_part 
        where enabledExplore = 1 
        and hasExploreUrl = 1 
        and (bookSourceGroup = :key
            or bookSourceGroup like :key || ',%' 
            or bookSourceGroup like  '%,' || :key
            or bookSourceGroup like  '%,' || :key || ',%') 
        order by customOrder asc"""
    )
    fun flowGroupExplore(key: String): Flow<List<BookSourcePart>>

    @Query("select distinct bookSourceGroup from book_sources where trim(bookSourceGroup) <> ''")
    fun flowGroupsUnProcessed(): Flow<List<String>>

    @Query(
        """select distinct bookSourceGroup from book_sources 
        where enabled = 1 and trim(bookSourceGroup) <> ''"""
    )
    fun flowEnabledGroupsUnProcessed(): Flow<List<String>>

    @Query(
        """select distinct bookSourceGroup from book_sources 
        where enabledExplore = 1 
        and trim(exploreUrl) <> '' 
        and trim(bookSourceGroup) <> ''
        order by customOrder"""
    )
    fun flowExploreGroupsUnProcessed(): Flow<List<String>>

    @Query(
        """select * from book_sources 
        where bookSourceGroup like '%' || :group || '%' order by customOrder asc"""
    )
    suspend fun getByGroup(group: String): List<BookSource>

    @Query(
        """select * from book_sources 
        where enabled = 1 
        and (bookSourceGroup = :group
            or bookSourceGroup like :group || ',%' 
            or bookSourceGroup like  '%,' || :group
            or bookSourceGroup like  '%,' || :group || ',%')
        order by customOrder asc"""
    )
    suspend fun getEnabledByGroup(group: String): List<BookSource>

    @Query(
        """select * from book_sources_part 
        where enabled = 1 
        and (bookSourceGroup = :group
            or bookSourceGroup like :group || ',%' 
            or bookSourceGroup like  '%,' || :group
            or bookSourceGroup like  '%,' || :group || ',%')
        order by customOrder asc"""
    )
    suspend fun getEnabledPartByGroup(group: String): List<BookSourcePart>

    @Query(
        """select * from book_sources_part 
        where bookSourceGroup = :group
        or bookSourceGroup like :group || ',%' 
        or bookSourceGroup like  '%,' || :group
        or bookSourceGroup like  '%,' || :group || ',%'
        order by customOrder asc"""
    )
    suspend fun getPartByGroup(group: String): List<BookSourcePart>

    @Query(
        """select * from book_sources 
        where bookUrlPattern != 'NONE' and bookSourceType = :type order by customOrder asc"""
    )
    suspend fun getEnabledByType(type: Int): List<BookSource>

    @Query("select * from book_sources where enabled = 1 and bookSourceUrl = :baseUrl")
    suspend fun getBookSourceAddBook(baseUrl: String): BookSource?

    @Query(
        """select bp.* 
        from book_sources b join book_sources_part bp on b.bookSourceUrl = bp.bookSourceUrl
        where b.enabled = 1 
        and trim(b.bookUrlPattern) <> '' 
        and trim(b.bookUrlPattern) <> 'NONE' 
        order by b.customOrder"""
    )
    suspend fun hasBookUrlPattern(): List<BookSourcePart>

    @Query("select * from book_sources where bookSourceGroup is null or bookSourceGroup = ''")
    suspend fun noGroup(): List<BookSource>

    @Query("select * from book_sources order by customOrder asc")
    suspend fun all(): List<BookSource>

    @Query("select * from book_sources_part order by customOrder asc")
    suspend fun allPart(): List<BookSourcePart>

    @Query("select * from book_sources where enabled = 1 order by customOrder")
    suspend fun allEnabled(): List<BookSource>

    @Query("select * from book_sources_part where enabled = 1 order by customOrder asc")
    suspend fun allEnabledPart(): List<BookSourcePart>

    @Query("select * from book_sources where enabled = 0 order by customOrder")
    suspend fun allDisabled(): List<BookSource>

    @Query(
        """select * from book_sources 
        where bookSourceGroup is null or bookSourceGroup = '' or bookSourceGroup like '%未分组%'"""
    )
    suspend fun allNoGroup(): List<BookSource>

    @Query("select * from book_sources where enabledExplore = 1 order by customOrder")
    suspend fun allEnabledExplore(): List<BookSource>

    @Query("select * from book_sources where enabledExplore = 0 order by customOrder")
    suspend fun allDisabledExplore(): List<BookSource>

    @Query("select * from book_sources where loginUrl is not null and loginUrl != ''")
    suspend fun allLogin(): List<BookSource>

    @Query(
        """select bp.*
        from book_sources b join book_sources_part bp on b.bookSourceUrl = bp.bookSourceUrl 
        where b.enabled = 1 and b.bookSourceType = 0 order by b.customOrder"""
    )
    suspend fun allTextEnabledPart(): List<BookSourcePart>

    @Query(
        """select distinct bookSourceGroup from book_sources 
        where trim(bookSourceGroup) <> ''"""
    )
    suspend fun allGroupsUnProcessed(): List<String>

    @Query(
        """select distinct bookSourceGroup from book_sources 
        where enabled = 1 and trim(bookSourceGroup) <> ''"""
    )
    suspend fun allEnabledGroupsUnProcessed(): List<String>

    @Query("select * from book_sources where bookSourceUrl = :key")
    suspend fun getBookSource(key: String): BookSource?

    @Query("select * from book_sources_part where bookSourceUrl = :key")
    suspend fun getBookSourcePart(key: String): BookSourcePart?

    @Query("select count(*) from book_sources")
    suspend fun allCount(): Int

    @Query("SELECT EXISTS(select 1 from book_sources where bookSourceUrl = :key)")
    suspend fun has(key: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg bookSource: BookSource)

    @Update
    suspend fun update(vararg bookSource: BookSource)

    @Delete
    suspend fun delete(vararg bookSource: BookSource)

    @Query("delete from book_sources where bookSourceUrl = :key")
    suspend fun delete(key: String)

    @Transaction
    suspend fun delete(bookSources: List<BookSourcePart>) {
        for (bs in bookSources) {
            delete(bs.bookSourceUrl)
        }
    }

    @Query("select min(customOrder) from book_sources")
    suspend fun minOrder(): Int

    @Query("select max(customOrder) from book_sources")
    suspend fun maxOrder(): Int

    @Transaction
    suspend fun moveToTop(sourceUrl: String): Int? {
        val source = getBookSource(sourceUrl) ?: return null
        source.customOrder = minOrder() - 1
        update(source)
        return source.customOrder
    }

    @Transaction
    suspend fun moveToBottom(sourceUrl: String): Int? {
        val source = getBookSource(sourceUrl) ?: return null
        source.customOrder = maxOrder() + 1
        update(source)
        return source.customOrder
    }

    @Query(
        """select exists (select 1 
        from book_sources group by customOrder having count(customOrder) > 1)"""
    )
    suspend fun hasDuplicateOrder(): Boolean

    @Query("update book_sources set enabled = :enable where bookSourceUrl = :bookSourceUrl")
    suspend fun enable(bookSourceUrl: String, enable: Boolean)

    @Transaction
    suspend fun enable(enable: Boolean, bookSources: List<BookSourcePart>) {
        for (bs in bookSources) {
            enable(bs.bookSourceUrl, enable)
        }
    }

    @Query("update book_sources set enabledExplore = :enable where bookSourceUrl = :bookSourceUrl")
    suspend fun enableExplore(bookSourceUrl: String, enable: Boolean)

    @Transaction
    suspend fun enableExplore(enable: Boolean, bookSources: List<BookSourcePart>) {
        for (bs in bookSources) {
            enableExplore(bs.bookSourceUrl, enable)
        }
    }

    @Query(
        """update book_sources 
        set customOrder = :customOrder where bookSourceUrl = :bookSourceUrl"""
    )
    suspend fun upOrder(bookSourceUrl: String, customOrder: Int)

    @Transaction
    suspend fun upOrder(bookSources: List<BookSourcePart>) {
        for (bs in bookSources) {
            upOrder(bs.bookSourceUrl, bs.customOrder)
        }
    }

    suspend fun upOrder(bookSource: BookSourcePart) {
        upOrder(bookSource.bookSourceUrl, bookSource.customOrder)
    }

    @Query(
        """update book_sources 
        set bookSourceGroup = :bookSourceGroup where bookSourceUrl = :bookSourceUrl"""
    )
    suspend fun upGroup(bookSourceUrl: String, bookSourceGroup: String)

    @Transaction
    suspend fun upGroup(bookSources: List<BookSourcePart>) {
        for (bs in bookSources) {
            bs.bookSourceGroup?.let { upGroup(bs.bookSourceUrl, it) }
        }
    }

    private fun dealGroups(list: List<String>): List<String> {
        val groups = linkedSetOf<String>()
        list.forEach {
            it.splitNotBlank(AppPattern.splitGroupRegex).forEach { group ->
                groups.add(group)
            }
        }
        return groups.sortedWith { o1, o2 ->
            cnCompare(o1, o2)
        }
    }

    suspend fun allGroups(): List<String> = dealGroups(allGroupsUnProcessed())

    suspend fun allEnabledGroups(): List<String> = dealGroups(allEnabledGroupsUnProcessed())

    fun flowGroups(): Flow<List<String>> {
        return flowGroupsUnProcessed().map { list ->
            dealGroups(list)
        }.flowOn(IO)
    }

    fun flowExploreGroups(): Flow<List<String>> {
        return flowExploreGroupsUnProcessed().map { list ->
            dealGroups(list)
        }.flowOn(IO)
    }

    fun flowEnabledGroups(): Flow<List<String>> {
        return flowEnabledGroupsUnProcessed().map { list ->
            dealGroups(list)
        }.flowOn(IO)
    }
}
