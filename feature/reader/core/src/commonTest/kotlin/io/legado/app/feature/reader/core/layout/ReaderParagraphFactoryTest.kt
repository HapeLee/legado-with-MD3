package io.legado.app.feature.reader.core.layout

import io.legado.app.feature.reader.core.model.ReaderTextStyle
import kotlin.test.assertEquals
import kotlin.test.Test

class ReaderParagraphFactoryTest {
    @Test
    fun preservesShapedClustersAndSemanticMetadata() {
        val factory = ReaderParagraphFactory {
            GlyphClusters(listOf("A", "😀"), listOf(8f, 16f))
        }
        val paragraph = factory.create(
            text = "A😀",
            style = ReaderTextStyle(0, 16f),
            chapterPosition = 42,
            indentCharacters = 2,
            alignment = ReaderTextAlignment.JUSTIFY,
        )
        assertEquals(listOf("A", "😀"), paragraph.clusters)
        assertEquals(listOf(8f, 16f), paragraph.clusterWidthsPx)
        assertEquals(42, paragraph.chapterPosition)
        assertEquals(2, paragraph.indentCharacters)
    }
}
