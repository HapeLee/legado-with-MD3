package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.AiImageGroup

@Dao
interface AiImageGroupDao {

    @Query("select * from ai_image_groups order by sortOrder asc, createdAt asc")
    fun all(): List<AiImageGroup>

    @Query("select * from ai_image_groups where id = :id")
    fun get(id: String): AiImageGroup?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(group: AiImageGroup)

    @Query("update ai_image_groups set name = :name where id = :id")
    fun updateName(id: String, name: String)

    @Query("delete from ai_image_groups where id = :id")
    fun delete(id: String)
}
