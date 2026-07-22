package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.BookCharacterEvent
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.data.entities.BookCharacterRelation
import io.legado.app.data.entities.BookKnowledgeEntry
import io.legado.app.data.entities.BookOutlineNode

@Dao
interface BookKnowledgeDao {

    @Query(
        """
        select * from book_character_profiles
        where bookUrl = :bookUrl
          and status != ${BookCharacterProfile.STATUS_DELETED}
          and (:query = '' or name like '%' || :query || '%' or aliasesJson like '%' || :query || '%' or summary like '%' || :query || '%')
        order by updatedAt desc
        limit :limit
        """
    )
    suspend fun searchCharacterProfiles(
        bookUrl: String,
        query: String,
        limit: Int,
    ): List<BookCharacterProfile>

    @Query(
        """
        select * from book_character_profiles
        where bookUrl = :bookUrl
          and status != ${BookCharacterProfile.STATUS_DELETED}
        order by updatedAt desc
        limit :limit
        """
    )
    suspend fun getCharacterProfiles(
        bookUrl: String,
        limit: Int,
    ): List<BookCharacterProfile>

    @Query(
        """
        select * from book_character_profiles
        where bookUrl = :bookUrl
          and status != ${BookCharacterProfile.STATUS_DELETED}
          and (id = :idOrName or name = :idOrName or aliasesJson like '%' || :idOrName || '%')
        order by updatedAt desc
        limit 1
        """
    )
    suspend fun getCharacterProfile(bookUrl: String, idOrName: String): BookCharacterProfile?

    @Query(
        """
        select * from book_character_events
        where bookUrl = :bookUrl
          and (:characterId is null or characterId = :characterId)
          and (:maxChapterIndex is null or chapterIndex is null or chapterIndex <= :maxChapterIndex)
        order by coalesce(chapterIndex, 2147483647), importance desc, updatedAt desc
        limit :limit
        """
    )
    suspend fun getCharacterEvents(
        bookUrl: String,
        characterId: String?,
        maxChapterIndex: Int?,
        limit: Int,
    ): List<BookCharacterEvent>

    @Query(
        """
        select * from book_character_relations
        where bookUrl = :bookUrl
          and status != ${BookCharacterProfile.STATUS_DELETED}
          and (fromCharacterId = :characterId or toCharacterId = :characterId)
        order by updatedAt desc
        limit :limit
        """
    )
    suspend fun getCharacterRelations(
        bookUrl: String,
        characterId: String,
        limit: Int,
    ): List<BookCharacterRelation>

    @Query(
        """
        select * from book_character_relations
        where bookUrl = :bookUrl
          and status != ${BookCharacterProfile.STATUS_DELETED}
        order by updatedAt desc
        limit :limit
        """
    )
    suspend fun getBookCharacterRelations(
        bookUrl: String,
        limit: Int,
    ): List<BookCharacterRelation>

    @Query(
        """
        select * from book_knowledge_entries
        where bookUrl = :bookUrl
          and (:type is null or type = :type)
          and (:chapterIndex is null
               or (scopeStartChapter is null or scopeStartChapter <= :chapterIndex)
               and (scopeEndChapter is null or scopeEndChapter >= :chapterIndex))
          and (:query = '' or title like '%' || :query || '%' or keywordsJson like '%' || :query || '%' or content like '%' || :query || '%')
        order by priority desc, updatedAt desc
        limit :limit
        """
    )
    suspend fun searchKnowledgeEntries(
        bookUrl: String,
        query: String,
        type: String?,
        chapterIndex: Int?,
        limit: Int,
    ): List<BookKnowledgeEntry>

    @Query(
        """
        select * from book_outline_nodes
        where bookUrl = :bookUrl
          and (:nodeType is null or nodeType = :nodeType)
          and (:chapterIndex is null
               or (startChapterIndex is null or startChapterIndex <= :chapterIndex)
               and (endChapterIndex is null or endChapterIndex >= :chapterIndex))
        order by coalesce(startChapterIndex, -1), `order`, updatedAt desc
        limit :limit
        """
    )
    suspend fun getOutlineNodes(
        bookUrl: String,
        chapterIndex: Int?,
        nodeType: String?,
        limit: Int,
    ): List<BookOutlineNode>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCharacterProfile(profile: BookCharacterProfile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCharacterEvent(event: BookCharacterEvent)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCharacterRelation(relation: BookCharacterRelation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertKnowledgeEntry(entry: BookKnowledgeEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOutlineNode(node: BookOutlineNode)

    @Query("delete from book_character_profiles where bookUrl = :bookUrl and id = :characterId")
    suspend fun deleteCharacterProfile(bookUrl: String, characterId: String)

    @Query("update book_character_relations set status = ${BookCharacterProfile.STATUS_DELETED} where fromCharacterId = :characterId or toCharacterId = :characterId")
    suspend fun deleteRelationsForCharacter(characterId: String)

    @Query("delete from book_character_events where bookUrl = :bookUrl and characterId = :characterId")
    suspend fun deleteEventsForCharacter(bookUrl: String, characterId: String)

    @Query("delete from book_character_relations where id = :relationId")
    suspend fun deleteCharacterRelation(relationId: String)

    @Query("delete from book_knowledge_entries where id = :entryId")
    suspend fun deleteKnowledgeEntry(entryId: String)
}
