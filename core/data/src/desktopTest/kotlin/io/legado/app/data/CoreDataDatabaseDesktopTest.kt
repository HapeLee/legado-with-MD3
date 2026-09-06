package io.legado.app.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.legado.app.data.entities.TagGroupRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Desktop 端最小骨架冒烟：验证「去 Android 化后的真实 entity + DAO + @Database」经
 * BundledSQLiteDriver 能实际建表、insert/query/Flow。
 */
class CoreDataDatabaseDesktopTest {

    private var database: CoreDataDatabase? = null

    @AfterTest
    fun teardown() {
        database?.close()
    }

    private fun createDatabase(path: String): CoreDataDatabase =
        Room.databaseBuilder<CoreDataDatabase>(name = path)
            .setDriver(BundledSQLiteDriver())
            .build()

    @Test
    fun insert_and_query_roundtrip() = runTest {
        val path = System.getProperty("java.io.tmpdir") + "core-data-${System.nanoTime()}.db"
        database = createDatabase(path)
        val dao = database!!.tagGroupRuleDao

        dao.insert(TagGroupRule(id = 1L, pattern = "p1", groupName = "g1", order = 0))
        dao.insert(TagGroupRule(id = 2L, pattern = "p2", groupName = "g2", order = 1))

        val all = dao.getAll()
        assertEquals(2, all.size)

        assertEquals("p1", dao.getByGroupName("g1")?.pattern)
        assertEquals(1, dao.maxOrder())
    }

    @Test
    fun flow_emits_inserted_rows() = runTest {
        val path = System.getProperty("java.io.tmpdir") + "core-data-flow-${System.nanoTime()}.db"
        database = createDatabase(path)
        val dao = database!!.tagGroupRuleDao

        dao.insert(TagGroupRule(id = 1L, pattern = "p1", groupName = "g1"))

        val rows = dao.flowAll().first()
        assertEquals(1, rows.size)
        assertEquals("g1", rows[0].groupName)
    }
}
