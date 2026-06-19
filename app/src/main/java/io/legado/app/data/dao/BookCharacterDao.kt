package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.legado.app.data.entities.BookCharacter
import io.legado.app.data.entities.BookCharacterRelation
import kotlinx.coroutines.flow.Flow

@Dao
interface BookCharacterDao {

    @Query("SELECT * FROM book_characters WHERE bookUrl = :bookUrl ORDER BY roleLevel DESC, sortOrder ASC, id ASC")
    fun flowCharacters(bookUrl: String): Flow<List<BookCharacter>>

    @Query("SELECT * FROM book_characters WHERE bookUrl = :bookUrl ORDER BY roleLevel DESC, sortOrder ASC, id ASC")
    fun characters(bookUrl: String): List<BookCharacter>

    @Query("SELECT * FROM book_characters ORDER BY bookUrl ASC, roleLevel DESC, sortOrder ASC, id ASC")
    fun allCharacters(): List<BookCharacter>

    @Query("SELECT * FROM book_characters WHERE id = :id")
    fun getCharacter(id: Long): BookCharacter?

    @Query("SELECT * FROM book_characters WHERE bookUrl = :bookUrl AND name = :name LIMIT 1")
    fun getCharacter(bookUrl: String, name: String): BookCharacter?

    @Query("SELECT MAX(sortOrder) FROM book_characters WHERE bookUrl = :bookUrl")
    fun maxCharacterOrder(bookUrl: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCharacter(character: BookCharacter): Long

    @Update
    fun updateCharacter(character: BookCharacter)

    @Delete
    fun deleteCharacter(character: BookCharacter)

    @Query("DELETE FROM book_characters WHERE id = :id")
    fun deleteCharacterById(id: Long)

    @Query("SELECT * FROM book_character_relations WHERE bookUrl = :bookUrl ORDER BY sortOrder ASC, id ASC")
    fun flowRelations(bookUrl: String): Flow<List<BookCharacterRelation>>

    @Query("SELECT * FROM book_character_relations WHERE bookUrl = :bookUrl ORDER BY sortOrder ASC, id ASC")
    fun relations(bookUrl: String): List<BookCharacterRelation>

    @Query("SELECT * FROM book_character_relations ORDER BY bookUrl ASC, sortOrder ASC, id ASC")
    fun allRelations(): List<BookCharacterRelation>

    @Query("SELECT * FROM book_character_relations WHERE id = :id")
    fun getRelation(id: Long): BookCharacterRelation?

    @Query(
        """
        SELECT * FROM book_character_relations
        WHERE bookUrl = :bookUrl
          AND fromCharacterId = :fromCharacterId
          AND toCharacterId = :toCharacterId
          AND relationName = :relationName
        LIMIT 1
        """
    )
    fun getRelation(
        bookUrl: String,
        fromCharacterId: Long,
        toCharacterId: Long,
        relationName: String
    ): BookCharacterRelation?

    @Query("SELECT MAX(sortOrder) FROM book_character_relations WHERE bookUrl = :bookUrl")
    fun maxRelationOrder(bookUrl: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRelation(relation: BookCharacterRelation): Long

    @Update
    fun updateRelation(relation: BookCharacterRelation)

    @Delete
    fun deleteRelation(relation: BookCharacterRelation)

    @Query("DELETE FROM book_character_relations WHERE id = :id")
    fun deleteRelationById(id: Long)

    @Transaction
    fun deleteCharacterWithRelations(character: BookCharacter) {
        deleteCharacter(character)
    }
}
