package io.legado.app.smoke.roomprobe

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contract test proving Room 2.8.4 KMP actually compiles, generates
 * implementations via KSP for each target, and runs real SQLite queries
 * through [BundledSQLiteDriver] on JVM.
 *
 * The database builder is constructed per-target ([createDatabase]) because
 * `Room.databaseBuilder` has different signatures on Android vs other targets.
 */
abstract class RoomKmpProbeTest {

    /** Platform-specific writable path for the test database file. */
    protected abstract fun dbPath(): String

    /** Platform-specific database construction. */
    protected abstract fun createDatabase(path: String): ProbeDatabase

    private lateinit var database: ProbeDatabase

    @AfterTest
    fun teardown() {
        if (::database.isInitialized) {
            database.close()
        }
    }

    @Test
    fun insert_and_query_roundtrip() = runTest {
        database = createDatabase(dbPath())
        val dao = database.probeDao()

        val id1 = dao.insert(ProbeEntity(content = "hello", createdAt = 1000L))
        val id2 = dao.insert(ProbeEntity(content = "world", createdAt = 2000L))

        assertTrue(id1 != id2, "auto-generated ids should differ")
        assertEquals(2, dao.count())

        val all = dao.getAll()
        assertEquals(2, all.size)
        assertEquals("hello", all[0].content)
        assertEquals("world", all[1].content)
        assertEquals(1000L, all[0].createdAt)
    }

    @Test
    fun flow_query_emits_inserted_rows() = runTest {
        database = createDatabase(dbPath())
        val dao = database.probeDao()
        dao.insert(ProbeEntity(content = "flow-row", createdAt = 42L))

        val rows = dao.observeAll().first()
        assertEquals(1, rows.size)
        assertEquals("flow-row", rows[0].content)
    }

    @Test
    fun delete_removes_matching_row() = runTest {
        database = createDatabase(dbPath())
        val dao = database.probeDao()
        val id = dao.insert(ProbeEntity(content = "to-delete", createdAt = 0L))
        assertEquals(1, dao.count())

        val deleted = dao.deleteById(id)
        assertEquals(1, deleted)
        assertEquals(0, dao.count())
    }
}
