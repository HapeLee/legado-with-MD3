package io.legado.app.feature.reader.core.style

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class ReaderCharacterStyleTest {
    @Test
    fun compiledRangesMatchDirectResolutionAtEveryBoundaryAndOverlap() {
        val random = Random(21)
        repeat(100) {
            val ranges = List(random.nextInt(0, 20)) { index ->
                ReaderStyleRange(
                    start = random.nextInt(-5, 25),
                    endExclusive = random.nextInt(-5, 25),
                    target = ReaderStyleTarget.entries[random.nextInt(3)],
                    style = ReaderCharacterStyle(colorArgb = index),
                    priority = random.nextInt(-2, 3),
                )
            }
            val compiled = ReaderCharacterStyleResolver.compile(ranges)
            for (position in -6..25) {
                for (isTitle in listOf(false, true)) {
                    assertEquals(
                        ReaderCharacterStyleResolver.resolve(ranges, position, isTitle),
                        compiled.resolve(position, isTitle),
                    )
                }
            }
        }
    }

    @Test
    fun cachedIntervalCursorSurvivesOutOfOrderAndAlternatingTargets() {
        val ranges = listOf(
            ReaderStyleRange(0, 4, ReaderStyleTarget.ALL, ReaderCharacterStyle(colorArgb = 1)),
            ReaderStyleRange(4, 8, ReaderStyleTarget.BODY, ReaderCharacterStyle(colorArgb = 2)),
            ReaderStyleRange(8, 12, ReaderStyleTarget.TITLE, ReaderCharacterStyle(colorArgb = 3)),
        )
        val compiled = ReaderCharacterStyleResolver.compile(ranges)
        // 位置来回跳、title/body 交替：游标命中不能改变结果。
        for (position in listOf(11, 0, 6, 3, 20, 7, 1, 9, 4, 8, -1)) {
            for (isTitle in listOf(false, true)) {
                assertEquals(
                    ReaderCharacterStyleResolver.resolve(ranges, position, isTitle),
                    compiled.resolve(position, isTitle),
                )
            }
        }
    }

    @Test
    fun degenerateRangesCompileToAnEmptyIndex() {
        val compiled = ReaderCharacterStyleResolver.compile(
            listOf(ReaderStyleRange(5, 5, ReaderStyleTarget.ALL, ReaderCharacterStyle()))
        )
        assertNull(compiled.resolve(0, false))
        assertNull(compiled.resolve(5, true))
    }

    @Test
    fun higherPriorityMarkingOverridesRegexStyle() {
        val ranges = listOf(
            ReaderStyleRange(0, 5, ReaderStyleTarget.BODY, ReaderCharacterStyle(colorArgb = 1), priority = 2),
            ReaderStyleRange(1, 3, ReaderStyleTarget.BODY, ReaderCharacterStyle(colorArgb = 2, markingId = "m"), priority = 10_000),
        )
        assertEquals(1, ReaderCharacterStyleResolver.resolve(ranges, 0, false)?.colorArgb)
        assertEquals("m", ReaderCharacterStyleResolver.resolve(ranges, 2, false)?.markingId)
        assertNull(ReaderCharacterStyleResolver.resolve(ranges, 2, true))
    }

    @Test
    fun equalPriorityUsesLastMatchingRangeWithoutCrossingTarget() {
        val ranges = listOf(
            ReaderStyleRange(
                0,
                4,
                ReaderStyleTarget.ALL,
                ReaderCharacterStyle(colorArgb = 1),
                priority = 3
            ),
            ReaderStyleRange(
                1,
                3,
                ReaderStyleTarget.BODY,
                ReaderCharacterStyle(colorArgb = 2),
                priority = 3
            ),
            ReaderStyleRange(
                1,
                3,
                ReaderStyleTarget.TITLE,
                ReaderCharacterStyle(colorArgb = 3),
                priority = 2
            ),
        )
        assertEquals(2, ReaderCharacterStyleResolver.resolve(ranges, 2, false)?.colorArgb)
        assertEquals(1, ReaderCharacterStyleResolver.resolve(ranges, 2, true)?.colorArgb)
        assertNull(ReaderCharacterStyleResolver.resolve(ranges, 5, false))
    }
}
