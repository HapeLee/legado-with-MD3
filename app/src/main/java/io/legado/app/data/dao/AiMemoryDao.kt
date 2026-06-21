package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.AiMemoryFragment
import io.legado.app.data.entities.AiMemoryItem

@Dao
interface AiMemoryDao {

    @Query("SELECT * FROM ai_memory_items WHERE memoryId = :memoryId LIMIT 1")
    fun item(memoryId: String): AiMemoryItem?

    @Query("SELECT * FROM ai_memory_fragments WHERE fragmentId = :fragmentId LIMIT 1")
    fun fragment(fragmentId: String): AiMemoryFragment?

    @Query(
        """
        SELECT * FROM ai_memory_items
        WHERE (:scope = '' OR scope = :scope OR scope = 'global')
          AND (:bookKey = '' OR bookKey = '' OR bookKey = :bookKey)
          AND (:sessionId = '' OR sessionId = '' OR sessionId = :sessionId)
        ORDER BY importance DESC, lastUsedAt DESC, updatedAt DESC
        LIMIT :limit
        """
    )
    fun candidateItems(
        scope: String,
        bookKey: String,
        sessionId: String,
        limit: Int = 80
    ): List<AiMemoryItem>

    @Query(
        """
        SELECT * FROM ai_memory_fragments
        WHERE (:scope = '' OR scope = :scope OR scope = 'global')
          AND (:bookKey = '' OR bookKey = '' OR bookKey = :bookKey)
          AND (:sessionId = '' OR sessionId = '' OR sessionId = :sessionId)
        ORDER BY importance DESC, lastUsedAt DESC, updatedAt DESC
        LIMIT :limit
        """
    )
    fun candidateFragments(
        scope: String,
        bookKey: String,
        sessionId: String,
        limit: Int = 80
    ): List<AiMemoryFragment>

    @Query(
        """
        SELECT m.* FROM ai_memory_items_fts f
        INNER JOIN ai_memory_items m ON m.memoryId = f.memoryId
        WHERE ai_memory_items_fts MATCH :query
          AND (:scope = '' OR m.scope = :scope OR m.scope = 'global')
          AND (:bookKey = '' OR m.bookKey = '' OR m.bookKey = :bookKey)
          AND (:sessionId = '' OR m.sessionId = '' OR m.sessionId = :sessionId)
        ORDER BY bm25(ai_memory_items_fts), m.importance DESC, m.updatedAt DESC
        LIMIT :limit
        """
    )
    fun searchItems(
        query: String,
        scope: String,
        bookKey: String,
        sessionId: String,
        limit: Int = 8
    ): List<AiMemoryItem>

    @Query(
        """
        SELECT m.* FROM ai_memory_fragments_fts f
        INNER JOIN ai_memory_fragments m ON m.fragmentId = f.fragmentId
        WHERE ai_memory_fragments_fts MATCH :query
          AND (:scope = '' OR m.scope = :scope OR m.scope = 'global')
          AND (:bookKey = '' OR m.bookKey = '' OR m.bookKey = :bookKey)
          AND (:sessionId = '' OR m.sessionId = '' OR m.sessionId = :sessionId)
        ORDER BY bm25(ai_memory_fragments_fts), m.importance DESC, m.updatedAt DESC
        LIMIT :limit
        """
    )
    fun searchFragments(
        query: String,
        scope: String,
        bookKey: String,
        sessionId: String,
        limit: Int = 8
    ): List<AiMemoryFragment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertItem(item: AiMemoryItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertFragment(fragment: AiMemoryFragment)

    @Query(
        """
        INSERT OR REPLACE INTO ai_memory_items_fts(memoryId, subject, predicate, objectValue, content)
        VALUES(:memoryId, :subject, :predicate, :objectValue, :content)
        """
    )
    fun upsertItemFts(memoryId: String, subject: String, predicate: String, objectValue: String, content: String)

    @Query(
        """
        INSERT OR REPLACE INTO ai_memory_fragments_fts(fragmentId, title, content, chapterTitle)
        VALUES(:fragmentId, :title, :content, :chapterTitle)
        """
    )
    fun upsertFragmentFts(fragmentId: String, title: String, content: String, chapterTitle: String)

    @Query("DELETE FROM ai_memory_items WHERE memoryId = :memoryId")
    fun deleteItem(memoryId: String)

    @Query("DELETE FROM ai_memory_fragments WHERE fragmentId = :fragmentId")
    fun deleteFragment(fragmentId: String)

    @Query("DELETE FROM ai_memory_items_fts WHERE memoryId = :memoryId")
    fun deleteItemFts(memoryId: String)

    @Query("DELETE FROM ai_memory_fragments_fts WHERE fragmentId = :fragmentId")
    fun deleteFragmentFts(fragmentId: String)

    @Query("DELETE FROM ai_memory_items WHERE scope = :scope AND (:bookKey = '' OR bookKey = :bookKey)")
    fun deleteItemsByScope(scope: String, bookKey: String = "")

    @Query("DELETE FROM ai_memory_fragments WHERE scope = :scope AND (:bookKey = '' OR bookKey = :bookKey)")
    fun deleteFragmentsByScope(scope: String, bookKey: String = "")

    @Query("UPDATE ai_memory_items SET lastUsedAt = :now WHERE memoryId IN (:ids)")
    fun markItemsUsed(ids: List<String>, now: Long = System.currentTimeMillis())

    @Query("UPDATE ai_memory_fragments SET lastUsedAt = :now WHERE fragmentId IN (:ids)")
    fun markFragmentsUsed(ids: List<String>, now: Long = System.currentTimeMillis())
}
