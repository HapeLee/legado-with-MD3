package io.legado.app.ui.widget.components.reader

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 底栏菜单着色层(tint)不透明度封顶逻辑测试。
 *
 * 背景：HazeStyle 的 tint 绘制在模糊内容之上，「菜单不透明度」readMenuBlurAlpha 默认 100
 * （= 1.0 完全不透明）。填充样式下若直接用该值，tint 会完全遮住模糊，表现为 issue #2044
 * 「底栏填充样式无模糊效果，只有渐变才生效」。封顶到半透明可保证模糊始终可见。
 */
class ReaderMenuTintAlphaTest {

    @Test
    fun `clamps tint alpha at max to keep blur visible`() {
        // 菜单不透明度拉满(100)时，着色层仍半透明，模糊可见
        assertEquals(MAX_MENU_TINT_ALPHA, menuTintAlpha(100))
        assertEquals(MAX_MENU_TINT_ALPHA, menuTintAlpha(80))
        assertEquals(MAX_MENU_TINT_ALPHA, menuTintAlpha(Int.MAX_VALUE))
    }

    @Test
    fun `clamps tint alpha at zero`() {
        assertEquals(0, menuTintAlpha(0))
        assertEquals(0, menuTintAlpha(-5))
        assertEquals(0, menuTintAlpha(Int.MIN_VALUE))
    }

    @Test
    fun `keeps in-range tint alpha unchanged`() {
        assertEquals(30, menuTintAlpha(30))
        assertEquals(50, menuTintAlpha(50))
        assertEquals(60, menuTintAlpha(60))
    }
}
