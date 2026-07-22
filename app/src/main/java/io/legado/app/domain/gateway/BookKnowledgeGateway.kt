package io.legado.app.domain.gateway

import io.legado.app.data.entities.BookCharacterEvent
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.data.entities.BookCharacterRelation
import io.legado.app.data.entities.BookKnowledgeEntry
import io.legado.app.data.entities.BookOutlineNode

interface BookKnowledgeGateway {
    suspend fun searchCharacterProfiles(
        bookUrl: String,
        query: String,
        limit: Int = 8,
    ): List<BookCharacterProfile>

    suspend fun getCharacterProfiles(
        bookUrl: String,
        limit: Int = 30,
    ): List<BookCharacterProfile>

    suspend fun getCharacterProfile(
        bookUrl: String,
        idOrName: String,
    ): BookCharacterProfile?

    suspend fun getCharacterEvents(
        bookUrl: String,
        characterId: String?,
        maxChapterIndex: Int?,
        limit: Int = 20,
    ): List<BookCharacterEvent>

    suspend fun getCharacterRelations(
        bookUrl: String,
        characterId: String,
        limit: Int = 20,
    ): List<BookCharacterRelation>

    suspend fun getBookCharacterRelations(
        bookUrl: String,
        limit: Int = 120,
    ): List<BookCharacterRelation>

    suspend fun searchKnowledgeEntries(
        bookUrl: String,
        query: String,
        type: String?,
        chapterIndex: Int?,
        limit: Int = 12,
    ): List<BookKnowledgeEntry>

    suspend fun getOutlineNodes(
        bookUrl: String,
        chapterIndex: Int?,
        nodeType: String?,
        limit: Int = 20,
    ): List<BookOutlineNode>

    suspend fun upsertCharacterProfile(profile: BookCharacterProfile)
    suspend fun upsertCharacterEvent(event: BookCharacterEvent)
    suspend fun upsertCharacterRelation(relation: BookCharacterRelation)
    suspend fun upsertKnowledgeEntry(entry: BookKnowledgeEntry)
    suspend fun upsertOutlineNode(node: BookOutlineNode)
    suspend fun deleteCharacterProfile(
        bookUrl: String,
        characterId: String,
        deleteRelations: Boolean,
        deleteEvents: Boolean,
    )

    suspend fun deleteCharacterRelation(relationId: String)
    suspend fun deleteKnowledgeEntry(entryId: String)
}
