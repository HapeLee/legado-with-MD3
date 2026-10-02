package io.legado.app.core.ui.morph

import io.legado.app.ui.book.readaloud.morph.MORPH_VEIL_END
import io.legado.app.ui.book.readaloud.morph.computeMorphVeil
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 形变内容层的放行时机。
 *
 * 内容透明度在 `MORPH_VEIL_END` 就已经满格，弹簧进度要到收敛后才正好等于 1f；
 * 闸门若按进度判定，中间这段就是「看着停稳了但点不动」，动画被取消时还会永久卡住
 * （阅读页底栏点不动就是这么来的）。这里钉住判据只能跟透明度同源。
 */
class BookMorphContentGateTest {

    @Test
    fun `content that is fully opaque is tappable even while the spring has not converged`() {
        val progress = MORPH_VEIL_END

        assertTrue(morphContentSettled(computeMorphVeil(progress), progress))
        // 旧判据（progress >= 1f）在这一帧仍然屏蔽点击，正是卡死窗口
        assertFalse(progress >= 1f)
    }

    @Test
    fun `content still fading in keeps swallowing input`() {
        val progress = 0.4f

        assertFalse(morphContentSettled(computeMorphVeil(progress), progress))
    }

    @Test
    fun `a page opened without a morph never blocks its own content`() {
        assertTrue(morphContentSettled(veil = 0f, progress = 0f))
    }

    @Test
    fun `fade-only mode follows progress because its veil is the progress itself`() {
        assertFalse(morphContentSettled(veil = 0.9f, progress = 0.9f))
        assertTrue(morphContentSettled(veil = 1f, progress = 1f))
    }
}
