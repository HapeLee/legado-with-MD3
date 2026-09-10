package io.legado.app.ui.theme

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 锁住引擎条件式间距刻度表。
 *
 * 这些数字是**既有 UI 行为**（从 `:core:ui/AdaptivePadding.kt` 原样搬来，M1-2）。它们没有
 * 产品规格文档，唯一的事实来源就是代码——所以这里逐条冻结：将来做 token 层或重构时若
 * 把某个值写错，测试会红，而不是等到用户发现 Miuix 主题下间距不对。
 */
class AdaptiveSpacingTest {

    @Test
    fun horizontalContentPadding() {
        assertEquals(12.dp, AdaptiveSpacing.horizontal(ComposeEngine.Miuix))
        assertEquals(16.dp, AdaptiveSpacing.horizontal(ComposeEngine.Material3))
    }

    @Test
    fun tabPaddingDiffersAtBothEnds() {
        assertEquals(12.dp, AdaptiveSpacing.tabStart(ComposeEngine.Miuix))
        assertEquals(0.dp, AdaptiveSpacing.tabStart(ComposeEngine.Material3))
        assertEquals(12.dp, AdaptiveSpacing.tabEnd(ComposeEngine.Miuix))
        assertEquals(16.dp, AdaptiveSpacing.tabEnd(ComposeEngine.Material3))
    }

    @Test
    fun verticalHelperUsesItsOwnHorizontalValues() {
        assertEquals(12.dp, AdaptiveSpacing.verticalHorizontal(ComposeEngine.Miuix))
        assertEquals(8.dp, AdaptiveSpacing.verticalHorizontal(ComposeEngine.Material3))
    }

    @Test
    fun topAdjustments() {
        assertEquals(8.dp, AdaptiveSpacing.onlyVerticalTop(ComposeEngine.Miuix))
        assertEquals(0.dp, AdaptiveSpacing.onlyVerticalTop(ComposeEngine.Material3))
        assertEquals(12.dp, AdaptiveSpacing.contentTop(ComposeEngine.Miuix))
        assertEquals(16.dp, AdaptiveSpacing.contentTop(ComposeEngine.Material3))
        assertEquals(12.dp, AdaptiveSpacing.bookshelfTop(ComposeEngine.Miuix))
        assertEquals(8.dp, AdaptiveSpacing.bookshelfTop(ComposeEngine.Material3))
    }

    @Test
    fun bookshelfHorizontalDeltaIsAddedOnTopOfCallerValue() {
        // 调用方传 16.dp 时，Miuix 应得 22.dp、Material3 应得 20.dp。
        val caller = 16.dp
        assertEquals(22.dp, AdaptiveSpacing.bookshelfHorizontal(ComposeEngine.Miuix) + caller)
        assertEquals(20.dp, AdaptiveSpacing.bookshelfHorizontal(ComposeEngine.Material3) + caller)
    }
}
