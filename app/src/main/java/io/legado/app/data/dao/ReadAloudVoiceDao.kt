package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.legado.app.data.entities.BookVoiceBindingEntity
import io.legado.app.data.entities.ReadAloudVoiceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadAloudVoiceDao {

    /** UI 列表：用户排除的不显示。 */
    @Query(
        "select * from read_aloud_voices where excluded = 0 " +
            "order by available desc, enabled desc, displayName collate nocase"
    )
    @Transaction
    fun observeVoices(): Flow<List<ReadAloudVoiceEntity>>

    /**
     * 全量，含被排除的。
     *
     * 目录同步要靠它比对已有行并保留排除标记，所以这里不能过滤。
     */
    @Query("select * from read_aloud_voices order by available desc, enabled desc, displayName collate nocase")
    @Transaction
    suspend fun getVoices(): List<ReadAloudVoiceEntity>

    /** 可选/自动选音候选：排除的不参与。 */
    @Query(
        "select * from read_aloud_voices where enabled = 1 and available = 1 and excluded = 0 " +
            "and (engineType != 'http' or exists (select 1 from httpTTS where cast(httpTTS.id as text) = engineId)) " +
            "order by displayName collate nocase"
    )
    @Transaction
    suspend fun getEnabledVoices(): List<ReadAloudVoiceEntity>

    @Query("select count(*) from read_aloud_voices where excluded = 1")
    suspend fun countExcludedVoices(): Int

    /** 情绪是引擎级目录，默认/手工音色也能使用；排除音色不等于删除源目录。 */
    @Query("select emotionCatalogJson from read_aloud_voices where engineType = 'http' " +
        "and engineId = :engineId and managedBy = 'http_source_catalog' " +
        "and emotionCatalogJson != '[]' order by id limit 1")
    fun getHttpEmotionCatalog(engineId: String): String?

    @Query("delete from read_aloud_voices where engineType = 'http' and engineId = :engineId")
    suspend fun deleteHttpVoices(engineId: String)

    /** 包括排除行；直接以源表为准，不能使用 UI combine 的不同时间快照。 */
    @Query("delete from read_aloud_voices where engineType = 'http' " +
        "and not exists (select 1 from httpTTS where cast(httpTTS.id as text) = engineId)")
    suspend fun deleteOrphanedHttpVoices()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVoices(voices: List<ReadAloudVoiceEntity>)

    @Query("select * from read_aloud_voices where id = :id limit 1")
    suspend fun getVoice(id: String): ReadAloudVoiceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVoice(voice: ReadAloudVoiceEntity)

    @Delete
    suspend fun deleteVoice(voice: ReadAloudVoiceEntity)

    @Query(
        "select * from book_voice_bindings where bookUrl = :bookUrl " +
            "order by subjectType, subjectId"
    )
    fun observeBindings(bookUrl: String): Flow<List<BookVoiceBindingEntity>>

    @Query(
        "select * from book_voice_bindings where bookUrl = :bookUrl " +
            "order by subjectType, subjectId"
    )
    suspend fun getBindings(bookUrl: String): List<BookVoiceBindingEntity>

    @Query(
        "select * from book_voice_bindings where bookUrl = :bookUrl " +
            "and subjectType = :subjectType and subjectId = :subjectId limit 1"
    )
    suspend fun getBinding(
        bookUrl: String,
        subjectType: String,
        subjectId: String,
    ): BookVoiceBindingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBinding(binding: BookVoiceBindingEntity)

    @Delete
    suspend fun deleteBinding(binding: BookVoiceBindingEntity)
}
