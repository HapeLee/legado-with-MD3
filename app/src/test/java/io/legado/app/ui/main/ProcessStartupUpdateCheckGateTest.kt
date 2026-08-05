package io.legado.app.ui.main

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessStartupUpdateCheckGateTest {

    @Test
    fun `enabled check runs only once per process`() {
        val gate = ProcessStartupUpdateCheckGate()

        assertTrue(gate.consume(enabled = true))
        assertFalse(gate.consume(enabled = true))
    }

    @Test
    fun `disabled first startup still consumes the process check`() {
        val gate = ProcessStartupUpdateCheckGate()

        assertFalse(gate.consume(enabled = false))
        assertFalse(gate.consume(enabled = true))
    }
}
