package io.legado.app.feature.reader

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderRect
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTextDrawRunsTest {
    private fun row(values: List<String>, gap: Float, widthOf: (Int) -> Float = { 10f }): List<ReaderElement.Text> {
        var left = 0f
        val style = ReaderTextStyle(0, 10f)
        return values.mapIndexed { index, value ->
            val width = widthOf(index)
            ReaderElement.Text(
                bounds = ReaderRect(left, 0f, left + width, 12f),
                baselinePx = 10f,
                value = value,
                style = style,
                selected = false,
                emphasized = false,
                chapterPosition = index,
            ).also { left += width + gap }
        }
    }

    private fun runs(elements: List<ReaderElement>, sdk: Int = 30) =
        buildMergedTextRuns(elements, sdk) { run, members ->
            val spacing = minikinLetterSpacingPx(run.gapExtraPx, run.style.linearText, sdk)
            run.matchesLayout(members, FloatArray(members.size) { 10f + spacing })
        }

    @Test
    fun modernAndroidRetainsFractionalPositiveAndNegativeGaps() {
        for (sdk in listOf(30, 35, 37)) {
            for (gap in listOf(0f, .6f, 1f, 2.4f, -1f, -2.4f)) {
                val elements = row(listOf("甲", "乙", "丙"), gap)
                val run = runs(elements, sdk).getValue(elements[0])
                assertSame(elements[0], run.first)
                assertEquals("甲乙丙", run.text)
                assertEquals(-run.gapExtraPx / 2f, run.mergedDrawStartLeftPx(sdk), .001f)
            }
        }
    }

    @Test
    fun oldAndroidRejectsUnrepresentableSpacingAndKeepsIntegerSpacing() {
        for (sdk in listOf(26, 28, 29)) {
            for (gap in listOf(.6f, 1.6f, -2.4f)) {
                assertTrue(runs(row(listOf("甲", "乙", "丙"), gap), sdk).isEmpty())
            }
            val elements = row(listOf("甲", "乙", "丙"), -1f)
            val run = runs(elements, sdk).getValue(elements[0])
            assertEquals(1f, run.mergedDrawStartLeftPx(sdk), 0f)
        }
        assertEquals(3f, minikinLetterSpacingPx(2.5f, false, 29), 0f)
        assertEquals(-3f, minikinLetterSpacingPx(-2.5f, false, 29), 0f)
        assertEquals(2.5f, minikinLetterSpacingPx(2.5f, true, 29), 0f)
        assertEquals(.5f, minikinLeadingHalfSpacingPx(1f, false, 30), 0f)
        assertEquals(0f, minikinLeadingHalfSpacingPx(1f, false, 29), 0f)
    }

    @Test
    fun mergedRunsAreGatedToApi29WhereGetTextRunAdvancesExists() {
        for (sdk in listOf(26, 27, 28)) {
            assertFalse("sdk=$sdk", supportsMergedTextRuns(sdk))
        }
        for (sdk in listOf(29, 30, 35, 37)) {
            assertTrue("sdk=$sdk", supportsMergedTextRuns(sdk))
        }
    }

    @Test
    fun actualAdvancesMustMatchEveryPositionIncludingTheLastRightEdge() {
        val elements = row(listOf("甲", "乙", "丙"), 2f)
        val run = runs(elements).getValue(elements[0])
        assertTrue(run.matchesLayout(elements, floatArrayOf(12f, 12f, 12f)))
        assertFalse(run.matchesLayout(elements, floatArrayOf(12f, 12f, 13f)))
        assertFalse(run.matchesLayout(elements, floatArrayOf(13f, 11f, 12f)))
        assertFalse(run.matchesLayout(elements, floatArrayOf(12f, Float.NaN, 12f)))
        assertTrue(buildMergedTextRuns(elements, 30) { _, _ -> false }.isEmpty())
    }

    @Test
    fun smallPerGapErrorsCannotAccumulateAcrossALongLine() {
        val elements = row(List(100) { "甲" }, .009f)
        assertTrue(buildMergedTextRuns(elements, 30) { run, members ->
            run.matchesLayout(members, FloatArray(members.size) { 10f })
        }.isEmpty())
    }

    @Test
    fun complexClustersZeroWidthPlaceholdersAndWordSpacesSplitRuns() {
        for (separator in listOf("e\u0301", "字\uFE0F", "\uD83D\uDE00", " ", "\u200D", "س")) {
            val elements = row(listOf("甲", "乙", separator, "丙", "丁"), 2f)
            val runs = runs(elements)
            assertFalse("separator=$separator", runs.containsKey(elements[2]))
            assertEquals("甲乙", runs.getValue(elements[0]).text)
            assertEquals("丙丁", runs.getValue(elements[3]).text)
        }
        val elements = row(listOf("甲", "乙", "　", "丙"), 2f) { if (it == 2) 0f else 10f }
        assertEquals("甲乙", runs(elements).getValue(elements[0]).text)
        assertFalse(runs(elements).containsKey(elements[2]))
    }

    @Test
    fun nonuniformGapsRowsAndRubyGroupsAreNotMerged() {
        val elements = row(listOf("甲", "乙"), 2f)
        assertTrue(runs(listOf(elements[0], elements[1].copy(baselinePx = 20f))).isEmpty())
        assertTrue(runs(listOf(elements[0], elements[1].copy(style = elements[1].style.copy(italic = true)))).isEmpty())
        val row = row(listOf("甲", "乙", "丙"), 2f)
        val shifted = row.take(2) + row[2].copy(bounds = row[2].bounds.copy(left = 25f, right = 35f))
        val runs = runs(shifted)
        assertEquals("甲乙", runs.getValue(shifted[0]).text)
        assertFalse(runs.containsKey(shifted[2]))

        val ruby = row(listOf("甲", "東", "京", "文"), 2f).mapIndexed { index, element ->
            when (index) {
                1 -> element.copy(rubyGroupId = 1, rubyText = "とうきょう", glyphOffsetX = 5f)
                2 -> element.copy(rubyGroupId = 1)
                else -> element
            }
        }
        assertTrue(buildMergedTextRuns(ruby, 30) { _, _ -> true }
            .keys.none { it.rubyGroupId != 0 })
    }
}
