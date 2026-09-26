package io.legado.app.help.coil

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MangaPrefetchRequestsTest {
    @Test
    fun `immediate cancellation can finish all active jobs without starting pending work`() =
        runTest {
            val queue = MangaPrefetchRequests(
                CoroutineScope(
                    coroutineContext + UnconfinedTestDispatcher(testScheduler)
                )
            )
            var starts = 0
            var closes = 0
            queue.update((0..2).map { "p$it" to 0 }) {
                starts++
                try {
                    awaitCancellation()
                } finally {
                    closes++
                }
            }
            assertEquals(2, starts)
            queue.close()
            assertEquals(2, closes)
            assertEquals(2, starts)
        }

    @Test
    fun `whole chapter advances with two background tasks and viewport reprioritizes only pending pages`() =
        runTest {
            val queue = MangaPrefetchRequests(this)
            val keys = (0..5).map { "p$it" to 0 }
            val started = mutableListOf<Pair<String, Int>>()
            val gates = keys.associateWith { CompletableDeferred<Unit>() }
            val prepare: suspend (Pair<String, Int>) -> Unit =
                { key -> started += key; gates.getValue(key).await() }
            queue.update(keys, prepare)
            runCurrent()
            assertEquals(keys.take(2), started)
            queue.update(keys.reversed(), prepare)
            runCurrent()
            assertEquals(keys.take(2), started)
            gates.getValue(keys[0]).complete(Unit)
            runCurrent()
            assertEquals(listOf(keys[0], keys[1], keys[5]), started)
            gates.values.forEach { it.complete(Unit) }
            runCurrent()
            assertEquals(listOf(keys[0], keys[1], keys[5], keys[4], keys[3], keys[2]), started)
            queue.update(keys, prepare)
            runCurrent()
            assertEquals(6, started.size)
            queue.close()
        }

    @Test
    fun `chapter exit cancels active work and failure does not block later pages or spin`() =
        runTest {
            val queue = MangaPrefetchRequests(this)
            val cancelled = mutableListOf<String>()
            queue.update(listOf("old1" to 0, "old2" to 0)) { key ->
                try {
                    awaitCancellation()
                } finally {
                    cancelled += key.first
                }
            }
            runCurrent()
            val started = mutableListOf<Pair<String, Int>>()
            val prepare: suspend (Pair<String, Int>) -> Unit = { key ->
                started += key
                if (key.first == "failed") error("network failure")
            }
            val next = listOf("failed" to 0, "next" to 0, "last" to 0)
            queue.update(next, prepare)
            runCurrent()
            assertEquals(listOf("old1", "old2"), cancelled)
            assertEquals(next, started)
            queue.update(next, prepare)
            runCurrent()
            assertEquals(next, started)
            queue.update(listOf("failed" to 1, "next" to 0, "last" to 0), prepare)
            runCurrent()
            assertEquals(next + ("failed" to 1), started)
            queue.close()
            queue.update(listOf("unused" to 0), prepare)
            runCurrent()
            assertEquals(4, started.size)
        }

    @Test
    fun `disabling prefetch releases active work and reenabling prepares again`() = runTest {
        val queue = MangaPrefetchRequests(this)
        var starts = 0
        var closes = 0
        val prepare: suspend (Pair<String, Int>) -> Unit = {
            starts++
            try {
                awaitCancellation()
            } finally {
                closes++
            }
        }
        queue.update(listOf("page" to 0), prepare)
        runCurrent()
        queue.update(emptyList(), prepare)
        runCurrent()
        assertEquals(1, closes)
        queue.update(listOf("page" to 0), prepare)
        runCurrent()
        assertEquals(2, starts)
        queue.close()
        queue.close()
        runCurrent()
        assertEquals(2, closes)
    }
}
