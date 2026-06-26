package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.AiArtifact
import kotlinx.coroutines.flow.Flow

@Dao
interface AiArtifactDao {

    @Query("select * from ai_artifacts where bookUrl = :bookUrl and taskType = :taskType order by chapterIndex, updatedAt desc")
    fun observeBookArtifacts(bookUrl: String, taskType: String): Flow<List<AiArtifact>>

    @Query(
        """
        select * from ai_artifacts
        where bookUrl = :bookUrl
          and (:chapterIndex is null or chapterIndex = :chapterIndex)
          and taskType = :taskType
          and contentHash = :contentHash
          and promptHash = :promptHash
          and modelProfileId = :modelProfileId
          and status = ${AiArtifact.STATUS_SUCCESS}
        order by updatedAt desc
        limit 1
        """
    )
    suspend fun getCachedArtifact(
        bookUrl: String,
        chapterIndex: Int?,
        taskType: String,
        contentHash: String,
        promptHash: String,
        modelProfileId: String
    ): AiArtifact?

    @Query(
        """
        select * from ai_artifacts
        where (:bookUrl is null or bookUrl = :bookUrl)
          and (:taskType is null or taskType = :taskType)
          and (:chapterIndex is null or chapterIndex = :chapterIndex)
        order by updatedAt desc
        limit :limit
        """
    )
    suspend fun queryArtifacts(
        bookUrl: String?,
        taskType: String?,
        chapterIndex: Int?,
        limit: Int
    ): List<AiArtifact>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(artifact: AiArtifact)

    @Query("delete from ai_artifacts where bookUrl = :bookUrl and taskType = :taskType")
    suspend fun deleteBookArtifacts(bookUrl: String, taskType: String)
}
