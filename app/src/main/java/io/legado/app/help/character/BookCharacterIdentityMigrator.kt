package io.legado.app.help.character

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookIdentity

object BookCharacterIdentityMigrator {

    fun migrate(book: Book?): String {
        val workKey = BookIdentity.key(book)
        if (workKey.isBlank()) return ""
        BookIdentity.legacyKeys(book).forEach { legacyKey ->
            migrateCharacters(legacyKey, workKey)
            migrateRelations(legacyKey, workKey)
            migrateRoleCaches(legacyKey, workKey)
        }
        return workKey
    }

    private fun migrateCharacters(legacyKey: String, workKey: String) {
        val legacyCharacters = appDb.bookCharacterDao.characters(legacyKey)
        if (legacyCharacters.isEmpty()) return
        val existingByName = appDb.bookCharacterDao.characters(workKey)
            .associateBy { it.name }
            .toMutableMap()
        val now = System.currentTimeMillis()
        legacyCharacters.forEach { character ->
            if (character.name in existingByName) return@forEach
            val migrated = character.copy(
                bookUrl = workKey,
                updatedAt = now
            )
            appDb.bookCharacterDao.updateCharacter(migrated)
            existingByName[migrated.name] = migrated
        }
    }

    private fun migrateRelations(legacyKey: String, workKey: String) {
        val validIds = appDb.bookCharacterDao.characters(workKey)
            .map { it.id }
            .toSet()
        if (validIds.isEmpty()) return
        val now = System.currentTimeMillis()
        appDb.bookCharacterDao.relations(legacyKey)
            .filter { it.fromCharacterId in validIds && it.toCharacterId in validIds }
            .forEach { relation ->
                val exists = appDb.bookCharacterDao.getRelation(
                    workKey,
                    relation.fromCharacterId,
                    relation.toCharacterId,
                    relation.relationName
                )
                if (exists == null) {
                    appDb.bookCharacterDao.updateRelation(
                        relation.copy(bookUrl = workKey, updatedAt = now)
                    )
                }
            }
    }

    private fun migrateRoleCaches(legacyKey: String, workKey: String) {
        appDb.aiReadAloudRoleCacheDao.listByBook(legacyKey)
            .forEach { cache ->
                appDb.aiReadAloudRoleCacheDao.upsert(
                    cache.copy(bookUrl = workKey)
                )
            }
    }
}
