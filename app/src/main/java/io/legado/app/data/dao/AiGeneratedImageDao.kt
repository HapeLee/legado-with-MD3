package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.AiGeneratedImage

@Dao
interface AiGeneratedImageDao {

    @Query("select * from ai_generated_images order by createdAt desc")
    fun all(): List<AiGeneratedImage>

    @Query("select * from ai_generated_images where id = :id")
    fun get(id: String): AiGeneratedImage?

    @Query("select * from ai_generated_images where favorite = 1 order by updatedAt desc")
    fun favorites(): List<AiGeneratedImage>

    @Query("select * from ai_generated_images where favorite = 0 order by createdAt desc")
    fun temporary(): List<AiGeneratedImage>

    @Query("select * from ai_generated_images where groupId = :groupId and favorite = 1 order by updatedAt desc")
    fun byGroup(groupId: String): List<AiGeneratedImage>

    @Query("select * from ai_generated_images where bookKey = :bookKey order by createdAt desc")
    fun byBook(bookKey: String): List<AiGeneratedImage>

    @Query("select * from ai_generated_images where chapterKey = :chapterKey order by createdAt desc")
    fun byChapter(chapterKey: String): List<AiGeneratedImage>

    @Query("select * from ai_generated_images where sourceType = :sourceType order by createdAt desc")
    fun bySourceType(sourceType: String): List<AiGeneratedImage>

    @Query(
        """
        select * from ai_generated_images
        where name like :keyword
           or prompt like :keyword
           or bookName like :keyword
           or bookAuthor like :keyword
           or chapterTitle like :keyword
           or characterName like :keyword
        order by createdAt desc
        """
    )
    fun search(keyword: String): List<AiGeneratedImage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(image: AiGeneratedImage)

    @Query("update ai_generated_images set name = :name, updatedAt = :updatedAt where id = :id")
    fun rename(id: String, name: String, updatedAt: Long)

    @Query("update ai_generated_images set favorite = :favorite, groupId = :groupId, updatedAt = :updatedAt where id = :id")
    fun setFavorite(id: String, favorite: Boolean, groupId: String?, updatedAt: Long)

    @Query("update ai_generated_images set groupId = :targetGroupId, updatedAt = :updatedAt where groupId = :sourceGroupId and favorite = 1")
    fun moveGroup(sourceGroupId: String, targetGroupId: String, updatedAt: Long)

    @Query("delete from ai_generated_images where id = :id")
    fun delete(id: String)

    @Query("select * from ai_generated_images where favorite = 0 and createdAt < :cutoff")
    fun expiredTemporary(cutoff: Long): List<AiGeneratedImage>
}
