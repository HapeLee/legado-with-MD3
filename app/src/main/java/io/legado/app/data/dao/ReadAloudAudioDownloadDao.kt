package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.legado.app.data.entities.ReadAloudAudioDownload
import kotlinx.coroutines.flow.Flow

private const val COMPLETE_DOWNLOADS = """
    SELECT chapterIndex FROM read_aloud_audio_downloads
    WHERE bookUrl = :bookUrl
      AND files <> ''
      AND expectedSentenceCount = length(files) - length(replace(files, ',', '')) + 1
"""

/** 听书音频下载记录：一章一条，见 [ReadAloudAudioDownload]。 */
@Dao
interface ReadAloudAudioDownloadDao {

    @Query(
        """
        SELECT * FROM read_aloud_audio_downloads
        WHERE bookUrl = :bookUrl
        ORDER BY chapterIndex
        """
    )
    suspend fun getForBook(bookUrl: String): List<ReadAloudAudioDownload>

    @Query("SELECT * FROM read_aloud_audio_downloads WHERE bookUrl = :bookUrl ORDER BY chapterIndex")
    fun flowForBook(bookUrl: String): Flow<List<ReadAloudAudioDownload>>

    @Query(COMPLETE_DOWNLOADS)
    suspend fun getDownloadedChapters(bookUrl: String): List<Int>

    /** 同上，但订阅式：目录页的听书下载图标下完一章就当场亮起来。 */
    @Query(COMPLETE_DOWNLOADS)
    fun flowDownloadedChapters(bookUrl: String): Flow<List<Int>>

    @Query(
        """
        SELECT * FROM read_aloud_audio_downloads
        WHERE bookUrl = :bookUrl AND chapterIndex = :chapterIndex
        """
    )
    suspend fun getOne(bookUrl: String, chapterIndex: Int): ReadAloudAudioDownload?

    @Upsert
    suspend fun upsert(vararg item: ReadAloudAudioDownload)

    @Query("DELETE FROM read_aloud_audio_downloads WHERE bookUrl = :bookUrl AND chapterIndex = :chapterIndex")
    suspend fun delete(bookUrl: String, chapterIndex: Int)

    @Query("DELETE FROM read_aloud_audio_downloads WHERE bookUrl = :bookUrl")
    suspend fun deleteForBook(bookUrl: String)
}
