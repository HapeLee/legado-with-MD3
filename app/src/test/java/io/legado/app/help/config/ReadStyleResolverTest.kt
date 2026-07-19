package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadStyleResolverTest {

    @Test
    fun `system dark mode selects night reading style`() {
        assertEquals(
            ReadStyleResolver.ReadStyleMode.Night,
            resolveReadStyleMode(isEInkMode = false, isNightTheme = true)
        )
        assertEquals(
            ReadStyleResolver.ReadStyleMode.Day,
            resolveReadStyleMode(isEInkMode = false, isNightTheme = false)
        )
    }

    @Test
    fun `e ink mode keeps priority over system dark mode`() {
        assertEquals(
            ReadStyleResolver.ReadStyleMode.EInk,
            resolveReadStyleMode(isEInkMode = true, isNightTheme = true)
        )
    }
}
