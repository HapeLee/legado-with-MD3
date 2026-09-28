package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.legado.app.data.entities.VoicePoolEntity
import io.legado.app.data.entities.VoicePoolGroupEntity
import io.legado.app.data.entities.VoicePoolMember

@Dao
interface VoicePoolDao {

    @Query("SELECT * FROM voice_pools ORDER BY groupId, `order`, name COLLATE NOCASE")
    suspend fun getAll(): List<VoicePoolEntity>

    @Query("SELECT * FROM voice_pools WHERE enabled = 1 ORDER BY groupId, `order`, name COLLATE NOCASE")
    suspend fun getEnabled(): List<VoicePoolEntity>

    @Query("SELECT COUNT(*) FROM voice_pools")
    suspend fun count(): Int

    @Query("SELECT * FROM voice_pools WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): VoicePoolEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(pool: VoicePoolEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pool: VoicePoolEntity)

    @Update
    suspend fun update(pool: VoicePoolEntity)

    @Query("DELETE FROM voice_pools WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM voice_pool_members")
    suspend fun getAllMembers(): List<VoicePoolMember>

    @Query("SELECT * FROM voice_pool_members WHERE poolId = :poolId")
    suspend fun getMembers(poolId: String): List<VoicePoolMember>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMembers(members: List<VoicePoolMember>)

    @Query("DELETE FROM voice_pool_members WHERE poolId = :poolId AND voiceId IN (:voiceIds)")
    suspend fun removeMembers(poolId: String, voiceIds: List<String>)

    @Query("DELETE FROM voice_pool_members WHERE poolId = :poolId")
    suspend fun clearMembers(poolId: String)

    @Query("UPDATE voice_pool_members SET enabled = :enabled WHERE poolId = :poolId AND voiceId = :voiceId")
    suspend fun setMemberEnabled(poolId: String, voiceId: String, enabled: Boolean)

    /** 成员全量替换：保留既有成员的启用状态，新成员默认启用。 */
    @Transaction
    suspend fun setMembers(poolId: String, voiceIds: Set<String>) {
        val current = getMembers(poolId).map { it.voiceId }.toSet()
        val toAdd = voiceIds - current
        val toRemove = current - voiceIds
        if (toRemove.isNotEmpty()) removeMembers(poolId, toRemove.toList())
        if (toAdd.isNotEmpty()) {
            insertMembers(toAdd.map { VoicePoolMember(poolId, it) })
        }
    }

    // ---------- 分组（可嵌套，见 VoicePoolGroupEntity） ----------

    @Query(
        "SELECT * FROM voice_pool_groups " +
            "ORDER BY parentId, `order`, name COLLATE NOCASE",
    )
    suspend fun getGroups(): List<VoicePoolGroupEntity>

    @Query("SELECT * FROM voice_pool_groups WHERE id = :id LIMIT 1")
    suspend fun getGroup(id: String): VoicePoolGroupEntity?

    @Query("SELECT COUNT(*) FROM voice_pool_groups WHERE parentId = :parentId")
    suspend fun countChildren(parentId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGroupIgnore(group: VoicePoolGroupEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: VoicePoolGroupEntity)

    @Update
    suspend fun updateGroups(groups: List<VoicePoolGroupEntity>)

    @Query("DELETE FROM voice_pool_groups WHERE id = :id")
    suspend fun deleteGroupById(id: String)

    /** 分组的父级改挂（拖动/菜单「移动到」）；子分组随之整体搬家。 */
    @Query("UPDATE voice_pool_groups SET parentId = :parentId, updatedAt = :now WHERE id = :id")
    suspend fun setGroupParent(id: String, parentId: String, now: Long)

    @Query("UPDATE voice_pools SET groupId = :groupId, `order` = :order, updatedAt = :now WHERE id = :id")
    suspend fun setPoolSlot(id: String, groupId: String, order: Int, now: Long)

    @Update
    suspend fun updatePools(pools: List<VoicePoolEntity>)

    /**
     * 一次拖动结束后的整表回写：池的（分组, 顺序）和分组的（父级, 顺序）各自全量重写。
     * 兼容字段 `voice_pools.groupName`（显示路径）由 VoicePoolStore 在事务外刷新。
     */
    @Transaction
    suspend fun saveSlots(
        poolSlots: List<Pair<String, Pair<String, Int>>>,
        groupSlots: List<Pair<String, Pair<String, Int>>>,
        now: Long,
    ) {
        poolSlots.forEach { (id, slot) -> setPoolSlot(id, slot.first, slot.second, now) }
        if (groupSlots.isNotEmpty()) {
            val groups = getGroups().associateBy { it.id }
            updateGroups(
                groupSlots.mapNotNull { (id, slot) ->
                    groups[id]?.copy(parentId = slot.first, order = slot.second, updatedAt = now)
                },
            )
        }
    }
}
