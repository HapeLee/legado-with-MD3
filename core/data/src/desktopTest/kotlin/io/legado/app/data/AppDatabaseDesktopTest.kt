package io.legado.app.data

import androidx.room.Room
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreRule
import io.legado.app.data.entities.rule.SearchRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * P3 退出条件守卫：**在非 Android（desktop / JVM）target 上真实建起下沉后的 [AppDatabase]**。
 *
 * 下沉把 103 个 entity、39 个 DAO 与 `@Database` 注解整体搬进 `:core:data` commonMain，
 * 「能编译」只证明 KSP 生成了代码；本测试证明这套代码在 **desktop 上真能跑**：
 * - KSP 为 desktop 生成的 `AppDatabase_Impl` / 各 DAO `_Impl` 可用，104 版全表可建；
 * - `room_master_table` 的 identityHash 与下沉前导出的 schema 一致（老库可平滑升级）；
 * - 深水区实体（Book / BookSource 含 rule 簇 TypeConverter / HttpTTS / RssSource /
 *   SearchBook 含外键级联）可往返读写；
 * - 事务回滚与并发写语义正确。
 *
 * 驱动：desktop 注入 `BundledSQLiteDriver`（Android 侧由 app 的 `appDb` 注入
 * `AndroidSQLiteDriver`），两端 driver 分工即 P3 退出条件。
 */
class AppDatabaseDesktopTest {

    /**
     * 下沉前 app 侧导出的 104 版 schema identityHash
     * （`core/data/schemas/io.legado.app.data.AppDatabase/104.json`）。
     * 一旦下沉改变了任何表/列/索引，Room 会在建库时校验失败、或这里的值漂移。
     */
    private val expectedIdentityHash = "a6f43940451deb4c04a525583e1bea59"

    private fun tempDbPath(): String {
        val file = File.createTempFile("legado-p3-desktop", ".db")
        file.delete()
        return file.absolutePath
    }

    private fun createDatabase(path: String): AppDatabase =
        Room.databaseBuilder<AppDatabase>(name = path)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    /** 绕开 Room 直接用 bundled driver 读库文件，用于校验真实落盘内容。 */
    private fun queryRawColumn(path: String, sql: String): List<String> =
        queryRaw(path, sql) { it.getText(0) }

    private fun queryRawLong(path: String, sql: String): List<Long> =
        queryRaw(path, sql) { it.getLong(0) }

    private fun <T> queryRaw(path: String, sql: String, read: (SQLiteStatement) -> T): List<T> {
        val connection = BundledSQLiteDriver().open(path)
        return try {
            val statement = connection.prepare(sql)
            val result = mutableListOf<T>()
            while (statement.step()) result.add(read(statement))
            statement.close()
            result
        } finally {
            connection.close()
        }
    }

    @Test
    fun buildsCurrentSchemaOnDesktop() = runTest {
        val path = tempDbPath()
        val db = createDatabase(path)
        try {
            // 首次 DAO 调用触发建库 + schema 校验（不匹配会抛 IllegalStateException）
            assertTrue(db.bookDao.getAll().isEmpty(), "新库 books 表应为空")
            assertTrue(db.bookSourceDao.all().isEmpty(), "新库 book_sources 表应为空")
        } finally {
            db.close()
        }

        val hashes = queryRawColumn(path, "SELECT identity_hash FROM room_master_table")
        assertEquals(
            expectedIdentityHash,
            hashes.singleOrNull(),
            "desktop 建库的 identityHash 与下沉前导出 schema 不一致 —— schema 被改动",
        )

        val tables = queryRawColumn(
            path,
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'",
        ).toSet()
        // 103 个 entity 中绝大多数有独立表；这里只钉住深水区枢纽表存在
        assertTrue(
            tables.containsAll(
                listOf(
                    "books", "book_sources", "chapters", "searchBooks",
                    "rssSources", "httpTTS", "book_groups", "replace_rules",
                ),
            ),
            "缺失关键表：$tables",
        )
        assertTrue(tables.size > 50, "建表数量异常偏少：${tables.size}")
    }

    @Test
    fun deepWaterEntitiesRoundTrip() = runTest {
        val path = tempDbPath()
        val db = createDatabase(path)
        try {
            val source = BookSource(
                bookSourceUrl = "https://example.com/book",
                bookSourceName = "测试书源",
                bookSourceGroup = "测试分组",
                ruleSearch = SearchRule(bookList = "li.book", name = "h2"),
                ruleExplore = ExploreRule(bookList = "ul.list li"),
            )
            db.bookSourceDao.insert(source)

            val book = Book(
                bookUrl = "https://example.com/book/1",
                tocUrl = "https://example.com/book/1/toc",
                origin = source.bookSourceUrl,
                originName = source.bookSourceName,
                name = "测试书籍",
                author = "测试作者",
                kind = "玄幻",
                intro = "简介",
                latestChapterTitle = "第一章",
            )
            db.bookDao.insert(book)

            val tts = HttpTTS(id = 1L, name = "测试朗读", url = "https://example.com/tts")
            db.httpTTSDao.insert(tts)

            val rss = RssSource(sourceUrl = "https://example.com/rss", sourceName = "测试订阅")
            db.rssSourceDao.insert(rss)

            val searchBook = SearchBook(
                bookUrl = "https://example.com/book/1",
                origin = source.bookSourceUrl,
                originName = source.bookSourceName,
                name = book.name,
                author = book.author,
                latestChapterTitle = "第一章",
            )
            db.searchBookDao.insert(searchBook)

            val readBackSource = assertNotNull(db.bookSourceDao.getBookSource(source.bookSourceUrl))
            assertEquals("测试书源", readBackSource.bookSourceName)
            assertEquals("测试分组", readBackSource.bookSourceGroup)
            // rule 簇经 BookSourceConverters（JsonCodec）往返
            assertEquals("li.book", readBackSource.ruleSearch?.bookList)
            assertEquals("h2", readBackSource.ruleSearch?.name)
            assertEquals("ul.list li", readBackSource.ruleExplore?.bookList)

            val readBackBook = assertNotNull(db.bookDao.getBook(book.bookUrl))
            assertEquals("测试书籍", readBackBook.name)
            assertEquals("测试作者", readBackBook.author)
            assertEquals("玄幻", readBackBook.kind)
            assertEquals("第一章", readBackBook.latestChapterTitle)

            assertEquals("测试朗读", db.httpTTSDao.get(1L)?.name)
            assertEquals("测试订阅", db.rssSourceDao.getByKey(rss.sourceUrl)?.sourceName)
            assertEquals(
                "测试书籍",
                db.searchBookDao.getSearchBook(searchBook.bookUrl)?.name,
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun searchBookCascadesWithBookSource() = runTest {
        val path = tempDbPath()
        val db = createDatabase(path)
        try {
            val source = BookSource(bookSourceUrl = "https://a.com", bookSourceName = "A")
            db.bookSourceDao.insert(source)
            db.searchBookDao.insert(
                SearchBook(bookUrl = "https://a.com/1", origin = source.bookSourceUrl, name = "书1"),
                SearchBook(bookUrl = "https://a.com/2", origin = source.bookSourceUrl, name = "书2"),
            )
            assertNotNull(db.searchBookDao.getSearchBook("https://a.com/1"))
            assertNotNull(db.searchBookDao.getSearchBook("https://a.com/2"))

            // SearchBook → BookSource 外键 onDelete = CASCADE
            db.bookSourceDao.delete(source)
            assertTrue(
                db.searchBookDao.getSearchBook("https://a.com/1") == null &&
                    db.searchBookDao.getSearchBook("https://a.com/2") == null,
                "删除书源应级联删除其搜索结果",
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun transactionRollsBackOnFailure() = runTest {
        val path = tempDbPath()
        val db = createDatabase(path)
        try {
            db.bookDao.insert(Book(bookUrl = "https://keep.com/1", name = "保留"))

            // Room 2.8 KMP 的事务入口是 `useWriterConnection { it.immediateTransaction { } }`
            // （旧 `RoomDatabase.withTransaction` 扩展在 KMP 上不存在）。
            var thrown: Throwable? = null
            try {
                db.useWriterConnection { transactor ->
                    transactor.immediateTransaction<Unit> {
                        db.bookDao.insert(
                            Book(bookUrl = "https://rollback.com/1", name = "应回滚"),
                        )
                        error("boom")
                    }
                }
            } catch (e: Throwable) {
                thrown = e
            }
            assertNotNull(thrown, "事务内异常必须向上传播")

            val all = db.bookDao.getAll()
            assertEquals(1, all.size, "事务抛异常后写入必须整体回滚")
            assertEquals("保留", all.single().name)
        } finally {
            db.close()
        }
    }

    @Test
    fun concurrentInsertsAreAllPersisted() = runTest {
        val path = tempDbPath()
        val db = createDatabase(path)
        try {
            coroutineScope {
                (0 until 32).map { index ->
                    async(Dispatchers.IO) {
                        db.bookDao.insert(
                            Book(bookUrl = "https://concurrent.com/$index", name = "并发$index"),
                        )
                    }
                }.awaitAll()
            }
            assertEquals(32, db.bookDao.getAll().size, "并发写不应丢数据")
        } finally {
            db.close()
        }
    }

    /**
     * 迁移兼容：用 **导出的旧版 schema JSON**（103）手工建库后交给 Room 升级到当前版本。
     *
     * 103 → 104 是 autoMigration（给 `httpTTS` 加 `speed` 列）。这条测试证明：
     * 下沉后的 `core/data/schemas/` 仍是 Room 迁移链路的真实依据，且迁移在 **desktop**
     * 上可执行、能保留老数据。
     */
    @Test
    fun migratesFromExportedVersion103Schema() = runTest {
        val path = tempDbPath()
        createDatabaseFromExportedSchema(path, version = 103)

        val db = Room.databaseBuilder<AppDatabase>(name = path)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(*DatabaseMigrations.migrations)
            .build()
        try {
            // 首次 DAO 调用触发开库 → 检测到 user_version=103 → 跑迁移 → 校验 104 schema
            db.httpTTSDao.get(1L)
        } finally {
            db.close()
        }

        assertEquals(
            listOf(104L),
            queryRawLong(path, "PRAGMA user_version"),
            "autoMigration 103→104 后 user_version 必须是 104",
        )
        assertEquals(
            listOf("迁移前分组"),
            queryRawColumn(path, "SELECT groupName FROM book_groups WHERE groupId = 4242"),
            "迁移必须保留老数据",
        )
        // 104 新增的 httpTTS.speed 列必须真的建出来
        assertTrue(
            queryRawColumn(path, "SELECT name FROM pragma_table_info('httpTTS')").contains("speed"),
            "autoMigration 未建出 httpTTS.speed 列",
        )
    }

    /**
     * 按导出 schema JSON 建库，并把 `user_version` 钉成旧版本号。
     * Room 的 `createSql` 里表名是 `${TABLE_NAME}` 占位符，需按所在 entity/view 回填。
     */
    private fun createDatabaseFromExportedSchema(path: String, version: Int) {
        val statements = createStatementsOf(exportedSchemaJson(version))
        val connection = BundledSQLiteDriver().open(path)
        try {
            statements.forEach { connection.execSQL(it) }
            connection.execSQL("PRAGMA user_version = $version")
            connection.execSQL(
                "INSERT INTO book_groups(groupId, groupName, `order`, show) " +
                    "VALUES(4242, '迁移前分组', 1, 1)",
            )
        } finally {
            connection.close()
        }
    }

    private fun exportedSchemaJson(version: Int): String {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "core/data/schemas/io.legado.app.data.AppDatabase/$version.json")
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
        }
        throw IllegalStateException("找不到导出的 schema JSON：$version.json")
    }

    private fun createStatementsOf(json: String): List<String> {
        val token = Regex(
            "\"(?:tableName|viewName)\"\\s*:\\s*\"([^\"]+)\"" +
                "|\"createSql\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"",
        )
        var currentName = ""
        val statements = mutableListOf<String>()
        for (match in token.findAll(json)) {
            val name = match.groupValues[1]
            if (name.isNotEmpty()) {
                currentName = name
                continue
            }
            val sql = match.groupValues[2]
            if (sql.isNotEmpty()) {
                statements += sql
                    .replace("\\\"", "\"")
                    // 导出 schema 的 view SQL 里带字面 `\n` 续行（JSON 中为 `\\n`），
                    // SQLite 不认该 token，需摊平成空格。
                    .replace("\\n", " ")
                    .replace("\${TABLE_NAME}", currentName)
                    .replace("\${VIEW_NAME}", currentName)
            }
        }
        check(statements.isNotEmpty()) { "未能从 schema JSON 解析出建表语句" }
        return statements
    }
}
