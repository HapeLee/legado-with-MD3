package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.legado.app.data.entities.BookCastMemory

@Dao
interface BookCastMemoryDao {

    @Query("SELECT * FROM book_cast_memory WHERE bookUrl = :bookUrl")
    suspend fun get(bookUrl: String): BookCastMemory?

    @Upsert
    suspend fun upsert(memory: BookCastMemory)

    @Query("DELETE FROM book_cast_memory WHERE bookUrl = :bookUrl")
    suspend fun delete(bookUrl: String)
}
