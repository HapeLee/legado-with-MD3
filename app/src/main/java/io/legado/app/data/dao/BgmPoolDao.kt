package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.legado.app.data.entities.BgmPoolEntity
import io.legado.app.data.entities.BgmPoolGroupEntity
import io.legado.app.data.entities.BgmPoolMember
import io.legado.app.data.entities.BgmTrackEntity

@Dao
interface BgmPoolDao {

    // ---------- 配乐库（扁平） ----------

    @Query("SELECT * FROM bgm_tracks ORDER BY `order`, name COLLATE NOCASE")
    suspend fun getAll(): List<BgmTrackEntity>

    @Query("SELECT * FROM bgm_tracks WHERE enabled = 1 ORDER BY `order`, name COLLATE NOCASE")
    suspend fun getEnabled(): List<BgmTrackEntity>

    @Query("SELECT COUNT(*) FROM bgm_tracks")
    suspend fun count(): Int

    @Query("SELECT * FROM bgm_tracks WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): BgmTrackEntity?

    @Query("SELECT * FROM bgm_tracks WHERE id = :id LIMIT 1")
    suspend fun getTrack(id: String): BgmTrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(track: BgmTrackEntity)

    @Update
    suspend fun update(track: BgmTrackEntity)

    @Query("UPDATE bgm_tracks SET enabled = :enabled, updatedAt = :now WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE bgm_tracks SET `order` = :order WHERE id = :id")
    suspend fun setOrder(id: String, order: Int)

    @Query("UPDATE bgm_tracks SET volume = :volume, updatedAt = :now WHERE id = :id")
    suspend fun setVolume(id: String, volume: Float, now: Long = System.currentTimeMillis())

    @Query("UPDATE bgm_tracks SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun setName(id: String, name: String, now: Long = System.currentTimeMillis())

    /**
     * 这条配乐所在的全部池名。
     *
     * 场景标记 [io.legado.app.data.entities.BgmSceneMark] 记的是 `poolName` + `trackName`
     * 两个**名字**而不是 id，所以改配乐名必须连带把引用它的场景一起改掉，
     * 而且只能限在这些池里改——别的池可能正好也有一条同名的。
     * 消费方：BgmPoolStore.renameTrack。
     */
    @Query("SELECT p.name FROM bgm_pools p JOIN bgm_pool_members m ON m.poolId = p.id WHERE m.trackId = :trackId")
    suspend fun getPoolNamesOfTrack(trackId: String): List<String>

    @Delete
    suspend fun delete(track: BgmTrackEntity)

    // ---------- 池 ----------

    @Query("SELECT * FROM bgm_pools ORDER BY groupId, `order`, name COLLATE NOCASE")
    suspend fun getPools(): List<BgmPoolEntity>

    @Query("SELECT * FROM bgm_pools WHERE id = :id LIMIT 1")
    suspend fun getPool(id: String): BgmPoolEntity?

    @Query("SELECT * FROM bgm_pools WHERE name = :name LIMIT 1")
    suspend fun getPoolByName(name: String): BgmPoolEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPool(pool: BgmPoolEntity)

    @Update
    suspend fun updatePool(pool: BgmPoolEntity)

    @Update
    suspend fun updatePools(pools: List<BgmPoolEntity>)

    @Query("DELETE FROM bgm_pools WHERE id = :id")
    suspend fun deletePoolById(id: String)

    @Query("UPDATE bgm_pools SET groupId = :groupId, `order` = :order, updatedAt = :now WHERE id = :id")
    suspend fun setPoolSlot(id: String, groupId: String, order: Int, now: Long)

    // ---------- 池内成员 ----------

    @Query("SELECT * FROM bgm_pool_members")
    suspend fun getAllMembers(): List<BgmPoolMember>

    @Query("SELECT * FROM bgm_pool_members WHERE poolId = :poolId")
    suspend fun getMembers(poolId: String): List<BgmPoolMember>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMembers(members: List<BgmPoolMember>)

    @Query("DELETE FROM bgm_pool_members WHERE poolId = :poolId AND trackId IN (:trackIds)")
    suspend fun removeMembers(poolId: String, trackIds: List<String>)

    @Query("DELETE FROM bgm_pool_members WHERE poolId = :poolId")
    suspend fun clearMembers(poolId: String)

    /** 配乐从库里删掉后清掉它的归属行，否则池计数会出现「有数没项」。 */
    @Query("DELETE FROM bgm_pool_members WHERE trackId = :trackId")
    suspend fun clearTrackMembership(trackId: String)

    @Query("UPDATE bgm_pool_members SET enabled = :enabled WHERE poolId = :poolId AND trackId = :trackId")
    suspend fun setMemberEnabled(poolId: String, trackId: String, enabled: Boolean)

    /** 成员全量替换：保留既有成员的启用状态，新成员默认启用。 */
    @Transaction
    suspend fun setMembers(poolId: String, trackIds: Set<String>) {
        val current = getMembers(poolId).map { it.trackId }.toSet()
        val toAdd = trackIds - current
        val toRemove = current - trackIds
        if (toRemove.isNotEmpty()) removeMembers(poolId, toRemove.toList())
        if (toAdd.isNotEmpty()) {
            insertMembers(toAdd.map { BgmPoolMember(poolId, it) })
        }
    }

    // ---------- 分组（可嵌套） ----------

    @Query("SELECT * FROM bgm_pool_groups ORDER BY parentId, `order`, name COLLATE NOCASE")
    suspend fun getGroups(): List<BgmPoolGroupEntity>

    @Query("SELECT * FROM bgm_pool_groups WHERE id = :id LIMIT 1")
    suspend fun getGroup(id: String): BgmPoolGroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: BgmPoolGroupEntity)

    @Update
    suspend fun updateGroups(groups: List<BgmPoolGroupEntity>)

    @Query("DELETE FROM bgm_pool_groups WHERE id = :id")
    suspend fun deleteGroupById(id: String)

    @Query("UPDATE bgm_pool_groups SET parentId = :parentId, updatedAt = :now WHERE id = :id")
    suspend fun setGroupParent(id: String, parentId: String, now: Long)

    /**
     * 一次拖动结束后的整表回写：池的（分组, 顺序）和分组的（父级, 顺序）各自全量重写。
     * 与 [VoicePoolDao.saveSlots] 同规则——只重算被拖那行的父级由调用方负责。
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
