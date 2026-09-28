package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.CastCharacter

@Dao
interface CastCharacterDao {

    @Query(
        """
        SELECT * FROM cast_characters
        WHERE bookUrl = :bookUrl
        ORDER BY name COLLATE NOCASE, updatedAt
        """
    )
    suspend fun getByBook(bookUrl: String): List<CastCharacter>

    @Query("SELECT * FROM cast_characters WHERE id = :id")
    suspend fun getById(id: String): CastCharacter?

    @Query("SELECT * FROM cast_characters WHERE bookUrl = :bookUrl AND name = :name")
    suspend fun getByName(bookUrl: String, name: String): List<CastCharacter>

    /** 分配表 tab：全书所有配音角色。 */
    @Query("SELECT * FROM cast_characters ORDER BY name COLLATE NOCASE")
    suspend fun getAllGlobal(): List<CastCharacter>

    @Query(
        """
        SELECT * FROM cast_characters
        WHERE bookUrl = :bookUrl AND name = :name AND poolLabel = :poolLabel
        """
    )
    suspend fun getByNameAndPool(
        bookUrl: String,
        name: String,
        poolLabel: String,
    ): CastCharacter?

    /** 身份唯一键冲突时忽略并返回 -1（创建按钮的「已存在」判定依赖此行为）。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(character: CastCharacter): Long

    @Update
    suspend fun update(character: CastCharacter)

    /** 拖动排序落库：只动排序位，不碰名字/音色（那些是编辑面板管的）。 */
    @Query("UPDATE cast_characters SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: String, sortOrder: Int)

    @Query("DELETE FROM cast_characters WHERE id = :id")
    suspend fun delete(id: String)
}
