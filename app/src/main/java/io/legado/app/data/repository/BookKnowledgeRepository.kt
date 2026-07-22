package io.legado.app.data.repository

import io.legado.app.data.dao.BookKnowledgeDao
import io.legado.app.data.entities.BookCharacterEvent
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.data.entities.BookCharacterRelation
import io.legado.app.data.entities.BookKnowledgeEntry
import io.legado.app.data.entities.BookOutlineNode
import io.legado.app.domain.gateway.BookKnowledgeGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BookKnowledgeRepository(
    private val dao: BookKnowledgeDao,
) : BookKnowledgeGateway {

    override suspend fun searchCharacterProfiles(
        bookUrl: String,
        query: String,
        limit: Int,
    ): List<BookCharacterProfile> = withContext(Dispatchers.IO) {
        dao.searchCharacterProfiles(bookUrl, query.trim(), limit.coerceIn(1, 30))
    }

    override suspend fun getCharacterProfiles(
        bookUrl: String,
        limit: Int,
    ): List<BookCharacterProfile> = withContext(Dispatchers.IO) {
        dao.getCharacterProfiles(bookUrl, limit.coerceIn(1, 80))
    }

    override suspend fun getCharacterProfile(
        bookUrl: String,
        idOrName: String,
    ): BookCharacterProfile? = withContext(Dispatchers.IO) {
        dao.getCharacterProfile(bookUrl, idOrName.trim())
    }

    override suspend fun getCharacterEvents(
        bookUrl: String,
        characterId: String?,
        maxChapterIndex: Int?,
        limit: Int,
    ): List<BookCharacterEvent> = withContext(Dispatchers.IO) {
        dao.getCharacterEvents(bookUrl, characterId, maxChapterIndex, limit.coerceIn(1, 80))
    }

    override suspend fun getCharacterRelations(
        bookUrl: String,
        characterId: String,
        limit: Int,
    ): List<BookCharacterRelation> = withContext(Dispatchers.IO) {
        dao.getCharacterRelations(bookUrl, characterId, limit.coerceIn(1, 80))
    }

    override suspend fun getBookCharacterRelations(
        bookUrl: String,
        limit: Int,
    ): List<BookCharacterRelation> = withContext(Dispatchers.IO) {
        dao.getBookCharacterRelations(bookUrl, limit.coerceIn(1, 200))
    }

    override suspend fun searchKnowledgeEntries(
        bookUrl: String,
        query: String,
        type: String?,
        chapterIndex: Int?,
        limit: Int,
    ): List<BookKnowledgeEntry> = withContext(Dispatchers.IO) {
        dao.searchKnowledgeEntries(
            bookUrl = bookUrl,
            query = query.trim(),
            type = type?.trim()?.takeIf { it.isNotBlank() },
            chapterIndex = chapterIndex,
            limit = limit.coerceIn(1, 80),
        )
    }

    override suspend fun getOutlineNodes(
        bookUrl: String,
        chapterIndex: Int?,
        nodeType: String?,
        limit: Int,
    ): List<BookOutlineNode> = withContext(Dispatchers.IO) {
        dao.getOutlineNodes(
            bookUrl = bookUrl,
            chapterIndex = chapterIndex,
            nodeType = nodeType?.trim()?.takeIf { it.isNotBlank() },
            limit = limit.coerceIn(1, 80),
        )
    }

    override suspend fun upsertCharacterProfile(profile: BookCharacterProfile) =
        withContext(Dispatchers.IO) {
            dao.upsertCharacterProfile(profile)
        }

    override suspend fun upsertCharacterEvent(event: BookCharacterEvent) =
        withContext(Dispatchers.IO) {
            dao.upsertCharacterEvent(event)
        }

    override suspend fun upsertCharacterRelation(relation: BookCharacterRelation) =
        withContext(Dispatchers.IO) {
            dao.upsertCharacterRelation(relation)
        }

    override suspend fun upsertKnowledgeEntry(entry: BookKnowledgeEntry) =
        withContext(Dispatchers.IO) {
            dao.upsertKnowledgeEntry(entry)
        }

    override suspend fun upsertOutlineNode(node: BookOutlineNode) = withContext(Dispatchers.IO) {
        dao.upsertOutlineNode(node)
    }

    override suspend fun deleteCharacterProfile(
        bookUrl: String,
        characterId: String,
        deleteRelations: Boolean,
        deleteEvents: Boolean,
    ) = withContext(Dispatchers.IO) {
        dao.deleteCharacterProfile(bookUrl, characterId)
        if (deleteRelations) dao.deleteRelationsForCharacter(characterId)
        if (deleteEvents) dao.deleteEventsForCharacter(bookUrl, characterId)
    }

    override suspend fun deleteCharacterRelation(relationId: String) = withContext(Dispatchers.IO) {
        dao.deleteCharacterRelation(relationId)
    }

    override suspend fun deleteKnowledgeEntry(entryId: String) = withContext(Dispatchers.IO) {
        dao.deleteKnowledgeEntry(entryId)
    }
}
