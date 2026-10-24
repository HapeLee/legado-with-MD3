package io.legado.app.feature.reader.core.layout

import io.legado.app.feature.reader.core.model.ReaderTextStyle
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderParagraphFactoryTest {
    @Test
    fun preservesShapedClustersAndSemanticMetadata() {
        val factory = ReaderParagraphFactory {
            GlyphClusters(listOf("A", "😀"), floatArrayOf(8f, 16f))
        }
        val paragraph = factory.create(
            text = "A😀",
            style = ReaderTextStyle(0, 16f),
            chapterPosition = 42,
            indentCharacters = 2,
            alignment = ReaderTextAlignment.JUSTIFY,
        )
        assertEquals(listOf("A", "😀"), paragraph.clusters)
        assertArrayEquals(floatArrayOf(8f, 16f), paragraph.clusterWidthsPx, 0f)
        assertEquals(42, paragraph.chapterPosition)
        assertEquals(2, paragraph.indentCharacters)
    }
}
