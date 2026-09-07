package io.legado.app.data

import android.annotation.SuppressLint
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.SupportSQLiteConnection
import androidx.sqlite.execSQL
import io.legado.app.data.entities.BookGroup
import io.legado.app.help.DefaultData
import org.intellij.lang.annotations.Language
import splitties.init.appCtx
import java.util.Locale

/**
 * [AppDatabase]（已下沉 :core:data commonMain）的 Android 侧单例构造与回调。
 *
 * 原 app 侧 `AppDatabase.kt` 的 `@Database` 注解 + abstract DAO + companion 常量
 * 已下沉 commonMain；这里只保留依赖平台栈的部分：
 * - `appDb` 单例（`Room.databaseBuilder` 需 appCtx + AndroidSQLiteDriver）
 * - `dbCallback`（`setLocale(Locale.CHINESE)` 依赖 AndroidSQLiteConnection；
 *   预置分组/键盘助手 SQL 依赖 `DefaultData`）
 */
val appDb by lazy {
    Room.databaseBuilder(appCtx, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
        .fallbackToDestructiveMigrationFrom(false, 1, 2, 3, 4, 5, 6, 7, 8, 9)
        .addMigrations(*DatabaseMigrations.migrations)
        .allowMainThreadQueries()
        .addCallback(dbCallback)
        .build()
}

/**
 * 建库/开库回调。挂载点 `RoomDatabase.Callback` 依赖 AndroidSQLiteConnection
 * （`setLocale` 无 commonMain 对应物），故留 app 侧。
 */
val dbCallback = object : RoomDatabase.Callback() {

        @SuppressLint("RestrictedApi")
        override fun onCreate(connection: SQLiteConnection) {
            // SQLiteConnection 无 setLocale；经 SupportSQLiteConnection 保留原语义
            // （BookmarkDao 的 collate localized 依赖它）。
            (connection as? SupportSQLiteConnection)?.db?.setLocale(Locale.CHINESE)
        }

        @SuppressLint("RestrictedApi")
        override fun onOpen(connection: SQLiteConnection) {
            @Language("sql")
            val insertBookGroupAllSql = """
                insert into book_groups(groupId, groupName, 'order', show)
                select ${BookGroup.IdAll}, '全部', -10, 1
                where not exists (select * from book_groups where groupId = ${BookGroup.IdAll})
            """.trimIndent()
            connection.execSQL(insertBookGroupAllSql)
            @Language("sql")
            val insertBookGroupLocalSql = """
                insert into book_groups(groupId, groupName, 'order', enableRefresh, show)
                select ${BookGroup.IdLocal}, '本地', -9, 0, 1
                where not exists (select * from book_groups where groupId = ${BookGroup.IdLocal})
            """.trimIndent()
            connection.execSQL(insertBookGroupLocalSql)
            @Language("sql")
            val insertBookGroupTextSql = """
                insert into book_groups(groupId, groupName, 'order', show)
                select ${BookGroup.IdText}, '小说', -26, 1
                where not exists (select * from book_groups where groupId = ${BookGroup.IdText})
            """.trimIndent()
            connection.execSQL(insertBookGroupTextSql)
            @Language("sql")
            val insertBookGroupMangaSql = """
                insert into book_groups(groupId, groupName, 'order', show)
                select ${BookGroup.IdManga}, '漫画', -25, 1
                where not exists (select * from book_groups where groupId = ${BookGroup.IdManga})
            """.trimIndent()
            connection.execSQL(insertBookGroupMangaSql)
            @Language("sql")
            val insertBookGroupMusicSql = """
                insert into book_groups(groupId, groupName, 'order', show)
                select ${BookGroup.IdAudio}, '音频', -8, 1
                where not exists (select * from book_groups where groupId = ${BookGroup.IdAudio})
            """.trimIndent()
            connection.execSQL(insertBookGroupMusicSql)
            @Language("sql")
            val insertGroupReading = """
                insert into book_groups(groupId, groupName, 'order', show)
                select ${BookGroup.IdReading}, '在读', -30, 1
                where not exists (select * from book_groups where groupId = ${BookGroup.IdReading})
            """.trimIndent()
            connection.execSQL(insertGroupReading)
            @Language("sql")
            val insertGroupUnread = """
                insert into book_groups(groupId, groupName, 'order', show)
                select ${BookGroup.IdUnread}, '未读', -29, 1
                where not exists (select * from book_groups where groupId = ${BookGroup.IdUnread})
            """.trimIndent()
            connection.execSQL(insertGroupUnread)
            @Language("sql")
            val insertGroupReadFinished = """
                insert into book_groups(groupId, groupName, 'order', show)
                select ${BookGroup.IdReadFinished}, '已读', -28, 1
                where not exists (select * from book_groups where groupId = ${BookGroup.IdReadFinished})
            """.trimIndent()
            connection.execSQL(insertGroupReadFinished)
            @Language("sql")
            val insertGroupReadFinishedUpdate = """
                insert into book_groups(groupId, groupName, 'order', show)
                select ${BookGroup.IdReadFinishedUpdate}, '连载已读', -27, 1
                where not exists (select * from book_groups where groupId = ${BookGroup.IdReadFinishedUpdate})
            """.trimIndent()
            connection.execSQL(insertGroupReadFinishedUpdate)
            @Language("sql")
            val insertGroupReadFinishedComplete = """
                insert into book_groups(groupId, groupName, 'order', show)
                select ${BookGroup.IdReadFinishedComplete}, '完本已读', -26, 1
                where not exists (select * from book_groups where groupId = ${BookGroup.IdReadFinishedComplete})
            """.trimIndent()
            connection.execSQL(insertGroupReadFinishedComplete)
            @Language("sql")
            val insertBookGroupNetNoneGroupSql = """
                insert into book_groups(groupId, groupName, 'order', show)
                select ${BookGroup.IdNetNone}, '网络未分组', -7, 1
                where not exists (select * from book_groups where groupId = ${BookGroup.IdNetNone})
            """.trimIndent()
            connection.execSQL(insertBookGroupNetNoneGroupSql)
            @Language("sql")
            val insertBookGroupLocalNoneGroupSql = """
                insert into book_groups(groupId, groupName, 'order', show)
                select ${BookGroup.IdLocalNone}, '本地未分组', -6, 0
                where not exists (select * from book_groups where groupId = ${BookGroup.IdLocalNone})
            """.trimIndent()
            connection.execSQL(insertBookGroupLocalNoneGroupSql)
            @Language("sql")
            val insertBookGroupErrorSql = """
                insert into book_groups(groupId, groupName, 'order', show)
                select ${BookGroup.IdError}, '更新失败', -1, 1
                where not exists (select * from book_groups where groupId = ${BookGroup.IdError})
            """.trimIndent()
            connection.execSQL(insertBookGroupErrorSql)
            @Language("sql")
            val upBookSourceLoginUiSql =
                "update book_sources set loginUi = null where loginUi = 'null'"
            connection.execSQL(upBookSourceLoginUiSql)
            @Language("sql")
            val upRssSourceLoginUiSql =
                "update rssSources set loginUi = null where loginUi = 'null'"
            connection.execSQL(upRssSourceLoginUiSql)
            @Language("sql")
            val upHttpTtsLoginUiSql =
                "update httpTTS set loginUi = null where loginUi = 'null'"
            connection.execSQL(upHttpTtsLoginUiSql)
            @Language("sql")
            val upHttpTtsConcurrentRateSql =
                "update httpTTS set concurrentRate = '0' where concurrentRate is null"
            connection.execSQL(upHttpTtsConcurrentRateSql)
            val isEmpty = connection.prepare(
                "select * from keyboardAssists order by serialNo"
            ).use { stmt -> !stmt.step() }
            if (isEmpty) {
                connection.prepare(
                    "INSERT OR REPLACE INTO keyboardAssists(type, key, value, serialNo) " +
                        "VALUES(?, ?, ?, ?)"
                ).use { insert ->
                    DefaultData.keyboardAssists.forEach { keyboardAssist ->
                        insert.bindLong(1, keyboardAssist.type.toLong())
                        insert.bindText(2, keyboardAssist.key)
                        insert.bindText(3, keyboardAssist.value)
                        insert.bindLong(4, keyboardAssist.serialNo.toLong())
                        insert.step()
                    }
                }
            }
        }
    }
