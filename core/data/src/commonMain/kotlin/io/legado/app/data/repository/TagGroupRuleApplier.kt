package io.legado.app.data.repository

import io.legado.app.data.AppDatabase
import io.legado.app.data.dao.BookDao
import io.legado.app.data.dao.BookGroupDao
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.TagGroupRule
import io.legado.app.domain.model.text.splitNotBlank

/**
 * Applies tag-group rules to shelf books, resolving rule patterns into group masks.
 *
 * **本类是这条规则匹配语义的唯一实现**（M3-6 起名副其实）。此前 app 侧
 * `io.legado.app.help.book.applyTagGroupRulesForBook` 是它的手抄镜像——那是 `Book.save()`
 * 调用的单本书路径（只处理一本书，避免整表重算），两边只能靠"改匹配语义时记得同步"维系。
 * M3-6 把单本书路径改成 [applyToBook] 并删掉镜像，现在正则编译、非法正则跳过、
 * 分组名 → 分组 id 的解析、`or` 掩码合并、只加不删这些语义全仓只有本类一份。
 *
 * 两个入口的差别只在**范围与落库**，不在匹配语义：
 * - [applyInCurrentTransaction]：全量重算，事务内直接读 `books` / `rules`，把 `group` 有变化的
 *   书写回 `bookDao`。入口是 `BookGroupMutationRepository.applyTagGroupRulesToAllBooks()`
 *   （`BookGroupMutationGateway`），由规则列表页在规则增删改后触发；
 * - [applyToBook]：单本书，**就地改 `book.group`、不写库**——调用方 `Book.save()` 随后自己
 *   `bookDao.update` / `insert`。
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
            persist = true,
        )
    }

    /**
     * 单本书路径：`Book.save()` / 目录刷新 / 书籍信息编辑后调用。
     *
     * ⚠️ **不写库**——与迁移前的 `io.legado.app.help.book.applyTagGroupRulesForBook` 逐字一致
     * （它当年也只写 `book.group`，持久化交给调用方）。把它实现成只读 [applyTagGroupRules] 的
     * 一个 `persist = false` 调用，是为了让单本书与全量重算共用同一份匹配语义。
     */
    suspend fun applyToBook(book: Book) {
        applyTagGroupRules(
            books = listOf(book),
            rules = database.tagGroupRuleDao.getAll(),
            groupDao = database.bookGroupDao,
            bookDao = database.bookDao,
            persist = false,
        )
    }

    private suspend fun applyTagGroupRules(
        books: List<Book>,
        rules: List<TagGroupRule>,
        groupDao: BookGroupDao,
        bookDao: BookDao,
        persist: Boolean,
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

        // 单本书路径（persist = false）不落库：调用方自己持久化。
        if (persist && updatedBooks.isNotEmpty()) {
            bookDao.update(*updatedBooks.toTypedArray())
        }
    }

    /**
     * Equivalent of `io.legado.app.help.book.Book.getDisplayTagList()`:
     * `(getCustomTagList() + getSourceTagList()).distinct()`.
     *
     * ⚠️ 这是 `:app` 那三个同名扩展（`BookExtensions.kt`）的**私有副本**，不是遗漏：
     * `:app` 依赖 `:core:data`（方向不可逆），所以本模块够不到 `io.legado.app.help.book.*`。
     * 三行纯字符串处理，拷贝成本低于为它新开一个共享模块；等 `Book` 实体随
     * `data:database` 迁移时再一并收口。
     */
    private fun Book.displayTagList(): List<String> =
        (customTagList() + sourceTagList()).distinct()

    private fun Book.customTagList(): List<String> =
        customTag?.splitNotBlank(",", "\n").orEmpty().distinct()

    private fun Book.sourceTagList(): List<String> =
        kind?.splitNotBlank(",", "\n").orEmpty().distinct()
}
