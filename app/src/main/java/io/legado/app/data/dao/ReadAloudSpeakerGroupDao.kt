package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.ReadAloudSpeakerGroup
import io.legado.app.data.entities.ReadAloudSpeakerGroupItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadAloudSpeakerGroupDao {

    @Query("SELECT * FROM read_aloud_speaker_groups ORDER BY sortOrder ASC, id ASC")
    fun flowGroups(): Flow<List<ReadAloudSpeakerGroup>>

    @Query("SELECT * FROM read_aloud_speaker_groups ORDER BY sortOrder ASC, id ASC")
    fun groups(): List<ReadAloudSpeakerGroup>

    @Query("SELECT * FROM read_aloud_speaker_groups WHERE enabled = 1 ORDER BY sortOrder ASC, id ASC")
    fun enabledGroups(): List<ReadAloudSpeakerGroup>

    @Query("SELECT * FROM read_aloud_speaker_group_items ORDER BY groupId ASC, sortOrder ASC, id ASC")
    fun flowItems(): Flow<List<ReadAloudSpeakerGroupItem>>

    @Query("SELECT * FROM read_aloud_speaker_group_items ORDER BY groupId ASC, sortOrder ASC, id ASC")
    fun items(): List<ReadAloudSpeakerGroupItem>

    @Query("SELECT * FROM read_aloud_speaker_group_items WHERE groupId = :groupId ORDER BY sortOrder ASC, id ASC")
    fun itemsByGroup(groupId: Long): List<ReadAloudSpeakerGroupItem>

    @Query("SELECT MAX(sortOrder) FROM read_aloud_speaker_groups")
    fun maxGroupOrder(): Int?

    @Query("SELECT MAX(sortOrder) FROM read_aloud_speaker_group_items WHERE groupId = :groupId")
    fun maxItemOrder(groupId: Long): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertGroup(group: ReadAloudSpeakerGroup): Long

    @Update
    fun updateGroup(group: ReadAloudSpeakerGroup)

    @Query("DELETE FROM read_aloud_speaker_groups WHERE id = :groupId")
    fun deleteGroup(groupId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertItems(items: List<ReadAloudSpeakerGroupItem>)

    @Update
    fun updateItem(item: ReadAloudSpeakerGroupItem)

    @Query("DELETE FROM read_aloud_speaker_group_items WHERE id IN (:ids)")
    fun deleteItems(ids: List<Long>)

    @Query("DELETE FROM read_aloud_speaker_group_items WHERE groupId = :groupId")
    fun deleteItemsByGroup(groupId: Long)
}
