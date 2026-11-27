package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalBookMetricInvalidationTest {

    @Test
    fun `signals sent before a subscriber exists are replayed to the first one`() {
        val received = mutableListOf<String>()
        val listener: (String) -> Unit = { received.add(it) }

        LocalBookMetricInvalidation.notifyChanged("book://early")
        LocalBookMetricInvalidation.register(listener)
        try {
            assertEquals(listOf("book://early"), received)
        } finally {
            LocalBookMetricInvalidation.unregister(listener)
        }
    }

    @Test
    fun `live subscribers get the signal immediately and buffered signals are drained once`() {
        val live = mutableListOf<String>()
        val liveListener: (String) -> Unit = { live.add(it) }

        LocalBookMetricInvalidation.register(liveListener)
        try {
            LocalBookMetricInvalidation.notifyChanged("book://live")
            assertEquals(listOf("book://live"), live)
        } finally {
            LocalBookMetricInvalidation.unregister(liveListener)
        }

        // 没有订阅者时先暂存，接入后补发
        LocalBookMetricInvalidation.notifyChanged("book://buffered")
        val buffered = mutableListOf<String>()
        val lateListener: (String) -> Unit = { buffered.add(it) }
        LocalBookMetricInvalidation.register(lateListener)
        try {
            assertEquals(listOf("book://buffered"), buffered)
        } finally {
            LocalBookMetricInvalidation.unregister(lateListener)
        }
        assertEquals(listOf("book://live"), live)
    }

    @Test
    fun `blank book urls are ignored`() {
        val received = mutableListOf<String>()
        val listener: (String) -> Unit = { received.add(it) }
        LocalBookMetricInvalidation.register(listener)
        try {
            LocalBookMetricInvalidation.notifyChanged("")
            assertEquals(emptyList<String>(), received)
        } finally {
            LocalBookMetricInvalidation.unregister(listener)
        }
    }
}
