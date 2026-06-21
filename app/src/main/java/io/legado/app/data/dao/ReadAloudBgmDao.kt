package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.ReadAloudBgmAssignmentCache
import io.legado.app.data.entities.ReadAloudBgmGroup
import io.legado.app.data.entities.ReadAloudBgmTrack
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadAloudBgmDao {

    @Query("SELECT * FROM read_aloud_bgm_groups ORDER BY sortOrder ASC, id ASC")
    fun flowGroups(): Flow<List<ReadAloudBgmGroup>>

    @Query("SELECT * FROM read_aloud_bgm_groups ORDER BY sortOrder ASC, id ASC")
    fun groups(): List<ReadAloudBgmGroup>

    @Query("SELECT * FROM read_aloud_bgm_groups WHERE assetType = :assetType ORDER BY sortOrder ASC, id ASC")
    fun groupsByType(assetType: String): List<ReadAloudBgmGroup>

    @Query("SELECT * FROM read_aloud_bgm_tracks ORDER BY groupId ASC, sortOrder ASC, id ASC")
    fun flowTracks(): Flow<List<ReadAloudBgmTrack>>

    @Query("SELECT * FROM read_aloud_bgm_tracks WHERE assetType = :assetType ORDER BY groupId ASC, sortOrder ASC, id ASC")
    fun flowTracksByType(assetType: String): Flow<List<ReadAloudBgmTrack>>

    @Query("SELECT * FROM read_aloud_bgm_tracks WHERE assetType = :assetType ORDER BY groupId ASC, sortOrder ASC, id ASC")
    fun tracksByType(assetType: String): List<ReadAloudBgmTrack>

    @Query("SELECT * FROM read_aloud_bgm_tracks WHERE enabled = 1 ORDER BY groupId ASC, sortOrder ASC, id ASC")
    fun enabledTracks(): List<ReadAloudBgmTrack>

    @Query("SELECT * FROM read_aloud_bgm_tracks WHERE enabled = 1 AND assetType = :assetType ORDER BY groupId ASC, sortOrder ASC, id ASC")
    fun enabledTracksByType(assetType: String): List<ReadAloudBgmTrack>

    @Query("SELECT * FROM read_aloud_bgm_tracks WHERE id = :id LIMIT 1")
    fun track(id: Long): ReadAloudBgmTrack?

    @Query("SELECT MAX(sortOrder) FROM read_aloud_bgm_groups")
    fun maxGroupOrder(): Int?

    @Query("SELECT MAX(sortOrder) FROM read_aloud_bgm_groups WHERE assetType = :assetType")
    fun maxGroupOrderByType(assetType: String): Int?

    @Query("SELECT MAX(sortOrder) FROM read_aloud_bgm_tracks WHERE groupId = :groupId")
    fun maxTrackOrder(groupId: Long): Int?

    @Query("SELECT MAX(sortOrder) FROM read_aloud_bgm_tracks WHERE groupId = :groupId AND assetType = :assetType")
    fun maxTrackOrder(groupId: Long, assetType: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertGroup(group: ReadAloudBgmGroup): Long

    @Update
    fun updateGroup(group: ReadAloudBgmGroup)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTrack(track: ReadAloudBgmTrack): Long

    @Update
    fun updateTrack(track: ReadAloudBgmTrack)

    @Query("DELETE FROM read_aloud_bgm_tracks WHERE id IN (:ids)")
    fun deleteTracks(ids: List<Long>)

    @Query("DELETE FROM read_aloud_bgm_tracks WHERE assetType = :assetType")
    fun deleteTracksByType(assetType: String)

    @Query("UPDATE read_aloud_bgm_tracks SET groupId = :groupId, updatedAt = :updatedAt WHERE id IN (:ids)")
    fun moveTracks(ids: List<Long>, groupId: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM read_aloud_bgm_groups WHERE id = :groupId")
    fun deleteGroup(groupId: Long)

    @Query("DELETE FROM read_aloud_bgm_groups WHERE id = :groupId AND assetType = :assetType")
    fun deleteGroup(groupId: Long, assetType: String)

    @Query("DELETE FROM read_aloud_bgm_groups WHERE assetType = :assetType")
    fun deleteGroupsByType(assetType: String)

    @Query("UPDATE read_aloud_bgm_tracks SET groupId = 0, updatedAt = :updatedAt WHERE groupId = :groupId")
    fun resetTrackGroup(groupId: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE read_aloud_bgm_tracks SET groupId = 0, updatedAt = :updatedAt WHERE groupId = :groupId AND assetType = :assetType")
    fun resetTrackGroup(groupId: Long, assetType: String, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM read_aloud_bgm_assignment_caches WHERE cacheKey = :cacheKey LIMIT 1")
    fun assignmentCache(cacheKey: String): ReadAloudBgmAssignmentCache?

    @Query(
        """
        SELECT * FROM read_aloud_bgm_assignment_caches
        WHERE bookUrl = :bookUrl
          AND chapterIndex = :chapterIndex
          AND contentHash = :contentHash
          AND catalogHash = :catalogHash
          AND status = 'success'
        ORDER BY updatedAt DESC
        LIMIT 1
        """
    )
    fun latestAssignmentCache(
        bookUrl: String,
        chapterIndex: Int,
        contentHash: String,
        catalogHash: String
    ): ReadAloudBgmAssignmentCache?

    @Query(
        """
        SELECT * FROM read_aloud_bgm_assignment_caches
        WHERE bookUrl = :bookUrl
          AND chapterIndex = :chapterIndex
          AND status = 'success'
        ORDER BY updatedAt DESC
        LIMIT 1
        """
    )
    fun latestAssignmentCacheByChapter(
        bookUrl: String,
        chapterIndex: Int
    ): ReadAloudBgmAssignmentCache?

    @Query(
        """
        SELECT * FROM read_aloud_bgm_assignment_caches
        WHERE bookUrl = :bookUrl
          AND chapterIndex = :chapterIndex
        ORDER BY updatedAt DESC
        LIMIT 1
        """
    )
    fun latestAnyAssignmentCacheByChapter(
        bookUrl: String,
        chapterIndex: Int
    ): ReadAloudBgmAssignmentCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAssignmentCache(cache: ReadAloudBgmAssignmentCache)
}
