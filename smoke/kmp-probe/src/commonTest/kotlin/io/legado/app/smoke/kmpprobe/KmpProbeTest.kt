package io.legado.app.smoke.kmpprobe

import kotlin.test.Test
import kotlin.test.assertEquals

class KmpProbeTest {
    @Test
    fun label_preserves_the_value_semantics() {
        assertEquals("reader#1", KmpProbe(title = "reader", revision = 1).label())
    }
}
