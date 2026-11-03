package io.legado.app.ui.main.bookshelf

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 网格「固定列数 / 固定封面宽度」两个开关的尺寸换算。
 *
 * 这几组数值同时约束 Compose 侧的布局口径：封面宽度 = 单元格宽度 − 左右各 4dp 内边距，
 * 自适应列数的单元格最小宽度 = 封面宽度 + 8dp，间距固定 8dp。
 */
class BookshelfGridMetricsTest {

    @Test
    fun `固定列数与固定封面时封面被列宽钳制`() {
        val metrics = resolveBookshelfGridMetrics(
            availableWidthDp = 336f,
            configuredColumns = 3,
            configuredCoverWidthDp = 120,
            fixedColumns = true,
            fixedCoverWidth = true,
        )

        assertEquals(3, metrics.columns)
        // (336 - 2×8) / 3 = 106.7dp 单元格，减掉左右 4dp 内边距后 98.7dp，达不到 120dp 上限
        assertEquals(99, metrics.coverWidthDp)
    }

    @Test
    fun `关闭固定封面后封面铺满列宽`() {
        val metrics = resolveBookshelfGridMetrics(
            availableWidthDp = 336f,
            configuredColumns = 2,
            configuredCoverWidthDp = 120,
            fixedColumns = true,
            fixedCoverWidth = false,
        )

        assertEquals(2, metrics.columns)
        // (336 - 8) / 2 = 164dp 单元格，封面 156dp，不再受 120dp 约束
        assertEquals(156, metrics.coverWidthDp)
    }

    @Test
    fun `关闭固定列数后列数由封面宽度推出`() {
        val metrics = resolveBookshelfGridMetrics(
            availableWidthDp = 336f,
            configuredColumns = 3,
            configuredCoverWidthDp = 120,
            fixedColumns = false,
            fixedCoverWidth = true,
        )

        // 单元格最小宽度 128dp + 间距 8dp ⇒ (336 + 8) / 136 = 2 列
        assertEquals(2, metrics.columns)
        assertEquals(120, metrics.coverWidthDp)
    }

    @Test
    fun `自适应列数与铺满封面同时关闭时也不产生非法尺寸`() {
        val metrics = resolveBookshelfGridMetrics(
            availableWidthDp = 336f,
            configuredColumns = 4,
            configuredCoverWidthDp = 120,
            fixedColumns = false,
            fixedCoverWidth = false,
        )

        assertEquals(2, metrics.columns)
        assertEquals(156, metrics.coverWidthDp)
    }

    @Test
    fun `平板宽屏列数变多但固定封面仍保持设定值`() {
        val metrics = resolveBookshelfGridMetrics(
            availableWidthDp = 800f,
            configuredColumns = 7,
            configuredCoverWidthDp = 120,
            fixedColumns = false,
            fixedCoverWidth = true,
        )

        // (800 + 8) / 136 = 5 列；单元格 (800 - 4×8) / 5 = 153.6dp，封面被 120dp 钳制
        assertEquals(5, metrics.columns)
        assertEquals(120, metrics.coverWidthDp)
    }

    @Test
    fun `窄屏多列时封面不会变成负数或零`() {
        val metrics = resolveBookshelfGridMetrics(
            availableWidthDp = 156f,
            configuredColumns = 5,
            configuredCoverWidthDp = 120,
            fixedColumns = true,
            fixedCoverWidth = true,
        )

        assertEquals(5, metrics.columns)
        assertEquals(17, metrics.coverWidthDp)
    }
}
