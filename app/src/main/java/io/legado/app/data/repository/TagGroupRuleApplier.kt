package io.legado.app.data.repository

import io.legado.app.data.AppDatabase
import io.legado.app.help.book.applyTagGroupRules

class TagGroupRuleApplier(
    private val database: AppDatabase,
) {

    suspend fun applyInCurrentTransaction() {
        applyTagGroupRules(
            books = database.bookDao.getAll(),
            rules = database.tagGroupRuleDao.getAll(),
            groupDao = database.bookGroupDao,
            bookDao = database.bookDao,
        )
    }
}
