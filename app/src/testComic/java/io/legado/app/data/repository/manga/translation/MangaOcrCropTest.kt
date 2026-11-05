package io.legado.app.data.repository.manga.translation

import org.junit.Assert.*
import org.junit.Test

class MangaOcrCropTest {
    @Test fun `detector unclip restoration is preserved unless neighboring ink constrains it`() {
        val source = floatArrayOf(20f, 20f, 120f, 20f, 120f, 30f, 20f, 30f)
        val unclipped = floatArrayOf(14f, 14f, 126f, 14f, 126f, 36f, 14f, 36f)
        assertArrayEquals(unclipped, mangaOcrCropPlan(source, emptyList(), unclipped).corners, .001f)
        val neighbor = floatArrayOf(20f, 32f, 120f, 32f, 120f, 42f, 20f, 42f)
        assertEquals(31f, mangaOcrCropPlan(source, listOf(neighbor), unclipped).corners[5], .001f)
    }
    @Test fun `cubic sampler preserves integer pixels and uses cubic interpolation at subpixels`() {
        val source = IntArray(20 * 10) { i -> 0xff000000.toInt() or (i * 137 and 0xffffff) }
        val points = floatArrayOf(2f, 3f, 12f, 3f, 12f, 8f, 2f, 8f)
        val result = MangaOcrCropSampler.warp(source, 20, 10, 0, 0, points, 10, 5)
        for (y in 0..4) for (x in 0..9) assertEquals(source[(y + 3) * 20 + x + 2], result[y * 10 + x])
        val impulse = IntArray(6 * 5) { i -> if (i % 6 == 2) -1 else 0xff000000.toInt() }
        val fractional = floatArrayOf(2.5f, 2f, 3.5f, 2f, 3.5f, 3f, 2.5f, 3f)
        // A=-.75 at x=.5 gives .59375*255=151.40625; bilinear would give 128.
        assertEquals(0xff979797.toInt(), MangaOcrCropSampler.warp(impulse, 6, 5, 0, 0, fractional, 1, 1).single())
    }

    @Test fun `ROI sampling matches full image and frame edges replicate instead of introducing black`() {
        val width = 30; val height = 20
        val full = IntArray(width * height) { i -> 0xff000000.toInt() or (i * 31 and 0xffffff) }
        val points = floatArrayOf(6.5f, 4.25f, 21.75f, 6f, 20.5f, 12.5f, 5.25f, 10.75f)
        val expected = MangaOcrCropSampler.warp(full, width, height, 0, 0, points, 84, 48)
        val bounds = MangaOcrCropSampler.sourceBounds(points, width, height)
        val w = bounds[2] - bounds[0]; val h = bounds[3] - bounds[1]
        val roi = IntArray(w * h) { i -> full[(bounds[1] + i / w) * width + bounds[0] + i % w] }
        assertArrayEquals(expected, MangaOcrCropSampler.warp(roi, w, h, bounds[0], bounds[1], points, 84, 48))
        val outside = floatArrayOf(-2f, -2f, 20f, -2f, 20f, 12f, -2f, 12f)
        assertTrue(MangaOcrCropSampler.warp(IntArray(200) { -1 }, 20, 10, 0, 0, outside, 100, 48).all { it == -1 })
    }

    @Test fun `recognition padding stays clear of nearby source ink and leaves original corners unchanged`() {
        val source = floatArrayOf(10f, 10f, 110f, 10f, 110f, 20f, 10f, 20f)
        val copy = source.copyOf()
        val next = floatArrayOf(10f, 21f, 110f, 21f, 110f, 31f, 10f, 31f)
        val plan = mangaOcrCropPlan(source, listOf(next))
        assertArrayEquals(copy, source, 0f)
        assertEquals(20.5f, plan.corners[5], .001f)
        assertEquals(20.5f, plan.corners[7], .001f)
        assertTrue(plan.width in 1..3200)
        assertFalse(plan.vertical)
    }

    @Test fun `vertical orientation is folded into a single crop transform and long lines are not truncated`() {
        val vertical = floatArrayOf(10f, 10f, 20f, 10f, 20f, 110f, 10f, 110f)
        val plan = mangaOcrCropPlan(vertical, emptyList())
        assertTrue(plan.vertical)
        assertEquals(22f, plan.corners[0], .001f)
        assertEquals(8f, plan.corners[1], .001f)
        assertEquals(22f, plan.corners[2], .001f)
        assertEquals(112f, plan.corners[3], .001f)
        val tooLong = floatArrayOf(0f, 0f, 1000f, 0f, 1000f, 2f, 0f, 2f)
        assertTrue(runCatching { mangaOcrCropPlan(tooLong, emptyList()) }.isFailure)
    }
}
