package io.legado.app.data.entities.readRecord

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadRecordTimeTotalsTest {
    @Test
    fun `sessions from target and source are accumulated`() {
        assertEquals(300L, merge(targetTotal = 100, targetSessions = 100, sourceTotal = 200, sourceSessions = 200))
    }

    @Test
    fun `source legacy history remains when target only has sessions`() {
        assertEquals(300L, merge(targetTotal = 100, targetSessions = 100, sourceTotal = 200, sourceSessions = 0))
    }

    @Test
    fun `target legacy history remains when source only has sessions`() {
        assertEquals(300L, merge(targetTotal = 100, targetSessions = 0, sourceTotal = 200, sourceSessions = 200))
    }

    @Test
    fun `legacy-only result is stable when calculated again`() {
        val merged = merge(targetTotal = 100, targetSessions = 0, sourceTotal = 200, sourceSessions = 0)
        assertEquals(300L, merged)
        assertEquals(merged, ReadRecordTimeTotals.total(0, ReadRecordTimeTotals.legacy(merged, 0)))
    }

    private fun merge(targetTotal: Long, targetSessions: Long, sourceTotal: Long, sourceSessions: Long): Long =
        ReadRecordTimeTotals.total(
            targetSessions + sourceSessions,
            ReadRecordTimeTotals.legacy(targetTotal, targetSessions) +
                ReadRecordTimeTotals.legacy(sourceTotal, sourceSessions),
        )
}
