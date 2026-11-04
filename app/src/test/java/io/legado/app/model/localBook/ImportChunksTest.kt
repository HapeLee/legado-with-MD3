package io.legado.app.model.localBook

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 分块导入的失败语义：块事务内任一异常 ⇒ 整块回滚 ⇒ 逐本无事务重放。
 * 关键保证（审视 P1）：错误计数与最终落库严格一致——不会出现「块已静默回滚、
 * 计数只记个别本、调用方提示成功」的窗口。
 */
class ImportChunksTest {

    /** 模拟真实事务：块内异常 ⇒ 恢复进入前快照（等价回滚）并重抛。 */
    private fun transactionalDb(committed: MutableList<Int>): (() -> Unit) -> Unit = { block ->
        val snapshot = committed.toList()
        try {
            block()
        } catch (t: Throwable) {
            committed.clear()
            committed.addAll(snapshot)
            throw t
        }
    }

    @Test
    fun `mid-chunk failure rolls back chunk then replays per book`() {
        val committed = mutableListOf<Int>()
        val errors = mutableListOf<Int>()

        importInChunks(
            items = (0 until 5).toList(),
            chunkSize = 5,
            importOne = { i ->
                if (i == 2) throw IllegalStateException("dao write failed") else committed += i
            },
            runChunkInTransaction = transactionalDb(committed),
            onImportError = { i, _ -> errors += i },
        )

        // 块回滚（0、1 未留在库里）后逐本重放：其余 4 本各落一次，2 号单独失败
        assertEquals(listOf(0, 1, 3, 4), committed)
        assertEquals(listOf(2), errors)
    }

    @Test
    fun `multiple failing books are isolated per book`() {
        val committed = mutableListOf<Int>()
        val errors = mutableListOf<Int>()

        importInChunks(
            items = listOf(0, 1, 2, 3),
            chunkSize = 4,
            importOne = { i ->
                if (i % 2 == 0) throw IllegalArgumentException("bad $i") else committed += i
            },
            runChunkInTransaction = transactionalDb(committed),
            onImportError = { i, _ -> errors += i },
        )

        assertEquals(listOf(1, 3), committed)
        assertEquals(listOf(0, 2), errors)
    }

    @Test
    fun `successful chunks commit once without replay`() {
        val committed = mutableListOf<Int>()
        val txRuns = intArrayOf(0)
        val db: (() -> Unit) -> Unit = { block ->
            txRuns[0] += 1
            block()
        }

        importInChunks(
            items = (0 until 6).toList(),
            chunkSize = 3,
            importOne = { committed += it },
            runChunkInTransaction = db,
            onImportError = { _, _ -> },
        )

        assertEquals(listOf(0, 1, 2, 3, 4, 5), committed)
        assertEquals(2, txRuns[0]) // 两块各一次事务，无重放
    }
}
