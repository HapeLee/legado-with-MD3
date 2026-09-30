package io.legado.app.feature.reader.core.transition

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 书架↔阅读页形变的时序与封面几何：算错一处就是「封面消失得太快」「封面不跟着放大」
 * 「面板比封面还小」这类观感问题，而这三条都只能靠数字断言，肉眼在录屏里分不清。
 */
class ReaderMorphFramesTest {

    private val start = Rect(100f, 200f, 300f, 480f)
    private val canvasWidth = 1080f
    private val canvasHeight = 2400f

    @Test
    fun `cover is fully gone at the configured percent of the duration`() {
        // 用户口径：这个百分比就是封面**完全不可见**的时刻，0 为一出发就没了。
        assertEquals(1f, openCoverAlphaAt(0f), 0f)
        assertEquals(0f, openCoverAlphaAt(0.35f), 1e-6f)
        assertEquals(0f, openCoverAlphaAt(1f), 0f)
        assertEquals(0.5f, openCoverAlphaAt(0.175f), 1e-6f)
    }

    @Test
    fun `cover alpha only falls and never comes back`() {
        var previous = Float.MAX_VALUE
        var steps = 0
        while (steps <= 100) {
            val value = openCoverAlphaAt(steps / 100f)
            assertTrue("alpha must not rise: $value after $previous", value <= previous + 1e-6f)
            previous = value
            steps++
        }
    }

    @Test
    fun `returning is exactly the reverse playback of opening`() {
        // 「返回书架的封面转场改成打开的倒放」：逐点相等才算倒放。
        var steps = 0
        while (steps <= 100) {
            val fraction = steps / 100f
            assertEquals(
                openCoverAlphaAt(1f - fraction),
                backCoverAlphaAt(fraction),
                1e-6f,
            )
            steps++
        }
        assertEquals(0f, backCoverAlphaAt(0f), 0f)
        assertEquals(0f, backCoverAlphaAt(0.5f), 0f)
        // 倒放：打开时封面在前 35% 里淡出，返回就是在最后 35% 里淡入，落地那一刻完全显出。
        assertEquals(0f, backCoverAlphaAt(0.65f), 0f)
        assertEquals(0.5f, backCoverAlphaAt(0.825f), 1e-6f)
        assertEquals(1f, backCoverAlphaAt(1f), 0f)
    }

    @Test
    fun `cover starts on the shelf cell and grows uniformly`() {
        val first = coverRectAt(start, canvasWidth, canvasHeight, 0f)
        assertEquals(start, first)
        var steps = 0
        while (steps <= 20) {
            val frame = coverRectAt(start, canvasWidth, canvasHeight, steps / 20f)
            // 等比：宽高倍率必须相同，否则正方的格子会被拉成竖条。
            val scaleX = frame.width / start.width
            val scaleY = frame.height / start.height
            assertEquals(scaleX, scaleY, 1e-4f)
            assertTrue("scale must never shrink below the cell", scaleX >= 1f - 1e-4f)
            // 中心贴着面板中心：封面永远长在正在扩大的那一块里，不会偏到一侧。
            val panelCenter = panelRectAt(start, canvasWidth, canvasHeight, steps / 20f).center
            assertEquals(panelCenter.x, frame.center.x, 1e-3f)
            assertEquals(panelCenter.y, frame.center.y, 1e-3f)
            steps++
        }
    }

    @Test
    fun `cover never escapes the clipped panel`() {
        var steps = 0
        while (steps <= 40) {
            val progress = steps / 40f
            val panel = panelRectAt(start, canvasWidth, canvasHeight, progress)
            val cover = coverRectAt(start, canvasWidth, canvasHeight, progress)
            assertTrue(cover.left >= panel.left - 0.01f)
            assertTrue(cover.top >= panel.top - 0.01f)
            assertTrue(cover.right <= panel.right + 0.01f)
            assertTrue(cover.bottom <= panel.bottom + 0.01f)
            steps++
        }
    }

    @Test
    fun `degenerate anchor rectangle does not produce a NaN scale`() {
        val empty = Rect(10f, 10f, 10f, 10f)
        assertEquals(empty, coverRectAt(empty, canvasWidth, canvasHeight, 0.5f))
    }

    @Test
    fun `panel contains the cover at every progress`() {
        var steps = 0
        while (steps <= 20) {
            val rect = panelRectAt(start, canvasWidth, canvasHeight, steps / 20f)
            assertTrue(rect.left <= start.left + 1e-3f)
            assertTrue(rect.top <= start.top + 1e-3f)
            assertTrue(rect.right >= start.right - 1e-3f)
            assertTrue(rect.bottom >= start.bottom - 1e-3f)
            assertTrue(rect.width >= 0f && rect.height >= 0f)
            steps++
        }
        assertEquals(start, panelRectAt(start, canvasWidth, canvasHeight, 0f))
        assertEquals(Rect(0f, 0f, canvasWidth, canvasHeight), panelRectAt(start, canvasWidth, canvasHeight, 1f))
    }

    @Test
    fun `corner radius never exceeds the frame`() {
        // 起点那一格只有 200x280，圆角不夹住就会画出自交路径（气泡状缺口）。
        val radius = panelCornerRadiusAt(
            startRadiusPx = 24f,
            screenRadiusPx = 90f,
            progress = 0.01f,
            frameWidthPx = 202f,
            frameHeightPx = 282f,
        )
        assertTrue(radius <= 101f)
        assertEquals(90f, panelCornerRadiusAt(24f, 90f, 1f, 1080f, 2400f), 0f)
    }

    @Test
    fun `clip helper agrees with the two curves it wraps`() {
        val (frame, radius) = panelClipAt(start, canvasWidth, canvasHeight, 24f, 90f, 0.4f)
        assertEquals(panelRectAt(start, canvasWidth, canvasHeight, 0.4f), frame)
        assertEquals(
            panelCornerRadiusAt(24f, 90f, 0.4f, frame.width, frame.height),
            radius,
            0f,
        )
    }
}
