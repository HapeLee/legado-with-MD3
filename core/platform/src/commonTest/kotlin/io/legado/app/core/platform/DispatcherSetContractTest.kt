package io.legado.app.core.platform

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals

class DispatcherSetContractTest {

    private val dispatchers: DispatcherSet = DefaultDispatcherSet

    @Test
    fun `io dispatcher runs a coroutine`() = runBlocking {
        val result = withContext(dispatchers.io) { 42 }
        assertEquals(42, result)
    }

    @Test
    fun `default dispatcher runs a coroutine`() = runBlocking {
        val result = withContext(dispatchers.default) { 7 }
        assertEquals(7, result)
    }
}
