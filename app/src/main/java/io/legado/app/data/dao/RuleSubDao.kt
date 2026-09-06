package io.legado.app.data.dao

import androidx.room.*
import io.legado.app.data.entities.RuleSub
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleSubDao {

    @Query("select * from ruleSubs order by customOrder")
    suspend fun all(): List<RuleSub>

    @Query("select * from ruleSubs order by customOrder")
    fun flowAll(): Flow<List<RuleSub>>

    @Query("select customOrder from ruleSubs order by customOrder limit 0,1")
    suspend fun maxOrder(): Int

    @Query("select * from ruleSubs where url = :url")
    suspend fun findByUrl(url: String): RuleSub?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg ruleSub: RuleSub)

    @Delete
    suspend fun delete(vararg ruleSub: RuleSub)

    @Update
    suspend fun update(vararg ruleSub: RuleSub)
}