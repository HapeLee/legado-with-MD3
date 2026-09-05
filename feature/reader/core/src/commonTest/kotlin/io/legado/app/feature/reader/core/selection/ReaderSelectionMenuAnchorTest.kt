package io.legado.app.feature.reader.core.selection

import io.legado.app.feature.reader.core.model.ReaderRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderSelectionMenuAnchorTest {

    @Test
    fun `multi line selection keeps distinct first and last line anchors`() {
        val anchor = ReaderSelectionMenuAnchor.from(
            listOf(
                ReaderRect(20f, 30f, 180f, 60f),
                ReaderRect(20f, 70f, 92f, 100f),
            )
        )

        assertEquals(
            ReaderSelectionMenuAnchor(20f, 30f, 60f, 92f, 100f),
            anchor,
        )
    }

    @Test
    fun `empty selection bounds have no menu anchor`() {
        assertNull(ReaderSelectionMenuAnchor.from(emptyList()))
    }
}
