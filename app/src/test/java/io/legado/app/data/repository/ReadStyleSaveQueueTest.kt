package io.legado.app.data.repository

import io.legado.app.help.config.ReadBookConfig
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadStyleSaveQueueTest {

    @Test
    fun `第一次保存失败后第二次仍可成功`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val attempts = Channel<String>(Channel.UNLIMITED)
        val failures = Channel<Throwable>(Channel.UNLIMITED)
        val saved = Channel<String>(Channel.UNLIMITED)
        val queue = ReadStyleSaveQueue(
            scope = scope,
            persist = { snapshot ->
                val name = snapshot.configs.single().name
                attempts.trySend(name)
                if (name == "first") throw IOException("disk full")
                saved.trySend(name)
            },
            onFailure = { failures.trySend(it) },
        )

        try {
            queue.submit(snapshot("first"))
            assertEquals("first", withTimeout(3_000) { attempts.receive() })
            assertEquals("disk full", withTimeout(3_000) { failures.receive() }.message)

            queue.submit(snapshot("second"))
            assertEquals("second", withTimeout(3_000) { saved.receive() })
        } finally {
            scope.cancel()
        }
    }

    private fun snapshot(name: String) = ReadStyleSaveSnapshot(
        configs = listOf(ReadBookConfig.Config(name = name)),
        shareConfig = ReadBookConfig.Config(name = "shared"),
    )
}
