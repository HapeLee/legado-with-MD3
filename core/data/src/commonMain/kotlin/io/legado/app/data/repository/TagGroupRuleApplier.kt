package io.legado.app.data.repository

import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.TagGroupRule
import io.legado.app.utils.splitNotBlank

/**
 * Applies tag-group rules to every shelf book, resolving rule patterns into group masks.
 *
 * **本类是这条规则匹配语义的唯一实现。** 原先 app 侧另有一份镜像
 * `io.legado.app.help.book.applyTagGroupRules(books, rules, groupDao, bookDao)`（已删除）；
 * 现在全量重算的入口是 `BookGroupMutationRepository.applyTagGroupRulesToAllBooks()`
 * （`BookGroupMutationGateway`），由规则列表页在规则增删改后触发。
 *
 * 单本书路径仍留在 app 侧 `applyTagGroupRulesForBook`（`Book.save()` 调用，只处理一本书，
 * 避免整表重算）；改动匹配语义时两处需同步。
 */
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

    private suspend fun applyTagGroupRules(
        books: List<Book>,
        rules: List<TagGroupRule>,
        groupDao: io.legado.app.data.dao.BookGroupDao,
        bookDao: io.legado.app.data.dao.BookDao,
    ) {
        if (rules.isEmpty()) return

        val compiledRules = rules.mapNotNull { rule ->
            val regex = try {
                Regex(rule.pattern)
            } catch (_: Exception) {
                return@mapNotNull null
            }
            rule to regex
        }
        if (compiledRules.isEmpty()) return

        // Resolve groupName -> groupId (find or create BookGroup)
        val groupCache = mutableMapOf<String, Long>()
        for ((rule, _) in compiledRules) {
            if (rule.groupName !in groupCache) {
                val existing = groupDao.getByName(rule.groupName)
                val groupId = existing?.groupId ?: run {
                    val newId = groupDao.getUnusedId()
                    groupDao.insert(
                        BookGroup(
                            groupId = newId,
                            groupName = rule.groupName,
                        )
                    )
                    newId
                }
                groupCache[rule.groupName] = groupId
            }
        }

        val updatedBooks = mutableListOf<Book>()
        for (book in books) {
            val kinds = book.displayTagList()
            var newGroupMask = 0L
            for ((rule, regex) in compiledRules) {
                if (kinds.any { regex.containsMatchIn(it) }) {
                    newGroupMask = newGroupMask or (groupCache[rule.groupName] ?: 0L)
                }
            }
            // Tag rules only add matching groups; manually assigned groups are preserved.
            val finalGroup = book.group or newGroupMask
            if (book.group != finalGroup) {
                book.group = finalGroup
                updatedBooks.add(book)
            }
        }

        if (updatedBooks.isNotEmpty()) {
            bookDao.update(*updatedBooks.toTypedArray())
        }
    }

    /**
     * Equivalent of `io.legado.app.help.book.Book.getDisplayTagList()`:
     * `(getCustomTagList() + getSourceTagList()).distinct()`.
     */
    private fun Book.displayTagList(): List<String> =
        (customTagList() + sourceTagList()).distinct()

    private fun Book.customTagList(): List<String> =
        customTag?.splitNotBlank(",", "\n").orEmpty().distinct()

    private fun Book.sourceTagList(): List<String> =
        kind?.splitNotBlank(",", "\n").orEmpty().distinct()
}
