package io.legado.app.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 纯值 token 契约测试（commonTest，零 Compose）。
 *
 * 钉住 token 的数值稳定性：任何改动间距/圆角/色值都必须是有意为之，
 * 否则依赖这些 token 的 UI 会在视觉上静默漂移。
 */
class TokensContractTest {

    @Test
    fun spacingUsesFourPointGrid() {
        assertTrue(Spacing.xs == 4f)
        assertTrue(Spacing.sm == 8f)
        assertTrue(Spacing.md == 12f)
        assertTrue(Spacing.lg == 16f)
        assertTrue(Spacing.xl == 20f)
        assertTrue(Spacing.xxl == 24f)
    }

    @Test
    fun radiusTiersAreDistinct() {
        assertTrue(Radius.sm < Radius.md)
        assertTrue(Radius.md < Radius.lg)
        assertTrue(Radius.lg < Radius.xl)
        assertTrue(Radius.xl < Radius.full)
    }

    @Test
    fun colorsAreOpaqueArgb() {
        // 高位字节是 alpha=0xFF（完全不透明）
        listOf(
            DesignColors.Neutral0,
            DesignColors.Neutral4,
            DesignColors.Neutral10,
            DesignColors.Neutral90,
            DesignColors.Neutral98,
            DesignColors.Neutral99,
        ).forEach { argb ->
            assertEquals(0xFFL, (argb ushr 24) and 0xFFL, "alpha 必须为 0xFF，got ${argb.toString(16)}")
        }
    }
}
