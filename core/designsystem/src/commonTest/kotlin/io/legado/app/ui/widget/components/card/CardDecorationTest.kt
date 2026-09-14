package io.legado.app.ui.widget.components.card

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import io.legado.app.domain.model.settings.ThemeSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * `resolveCardDecoration` 的主题覆盖语义。
 *
 * 这段逻辑原先内联在 `GlassCard.kt` 的私有 `BaseCard` 里，M1-3f 才抽成纯函数，供共享层的
 * `NormalCard` 与 `:core:ui` 的 `GlassCard` 共用。抽出的唯一理由就是让「卡片圆角/描边由谁决定」
 * 这个语义能被钉住——两个调用方不再各写一份。
 */
class CardDecorationTest {

    @Test
    fun `覆盖关闭时原样透传调用方请求`() {
        val requested = BorderStroke(3.dp, Color.Red)
        val decoration = resolveCardDecoration(
            theme = ThemeSettings(
                overrideBaseCardCornerRadius = false,
                baseCardCornerRadius = 99f,
                overrideBaseCardBorder = false,
                baseCardBorderWidth = 9f,
                baseCardBorderColor = 0xFF_00_00_00.toInt(),
            ),
            isDark = false,
            requestedCornerRadius = 12.dp,
            requestedBorder = requested,
            fallbackBorderColor = Color.Gray,
        )

        assertEquals(12.dp, decoration.cornerRadius)
        assertSame(requested, decoration.border)
    }

    @Test
    fun `覆盖开启时用主题圆角与主题描边颜色_日间`() {
        val decoration = resolveCardDecoration(
            theme = ThemeSettings(
                overrideBaseCardCornerRadius = true,
                baseCardCornerRadius = 20f,
                overrideBaseCardBorder = true,
                baseCardBorderWidth = 2f,
                baseCardBorderColor = 0xFF_12_34_56.toInt(),
                baseCardBorderColorNight = 0xFF_65_43_21.toInt(),
            ),
            isDark = false,
            requestedCornerRadius = 12.dp,
            requestedBorder = BorderStroke(3.dp, Color.Red),
            fallbackBorderColor = Color.Gray,
        )

        assertEquals(20.dp, decoration.cornerRadius)
        assertEquals(2.dp, decoration.border?.width)
        assertEquals(SolidColor(Color(0xFF_12_34_56)), decoration.border?.brush)
    }

    @Test
    fun `夜间取 night 描边颜色`() {
        val decoration = resolveCardDecoration(
            theme = ThemeSettings(
                overrideBaseCardBorder = true,
                baseCardBorderColor = 0xFF_12_34_56.toInt(),
                baseCardBorderColorNight = 0xFF_65_43_21.toInt(),
            ),
            isDark = true,
            requestedCornerRadius = 12.dp,
            requestedBorder = null,
            fallbackBorderColor = Color.Gray,
        )

        assertEquals(SolidColor(Color(0xFF_65_43_21)), decoration.border?.brush)
    }

    @Test
    fun `主题描边颜色为 0 时回落 fallback`() {
        val fallback = Color(0xFF_A0_B0_C0)
        val decoration = resolveCardDecoration(
            theme = ThemeSettings(
                overrideBaseCardBorder = true,
                baseCardBorderWidth = 1f,
                baseCardBorderColor = 0,
                baseCardBorderColorNight = 0,
            ),
            isDark = true,
            requestedCornerRadius = 12.dp,
            requestedBorder = BorderStroke(3.dp, Color.Red),
            fallbackBorderColor = fallback,
        )

        assertEquals(SolidColor(fallback), decoration.border?.brush)
    }

    @Test
    fun `覆盖开启时即使调用方没要求也会给出主题描边`() {
        val decoration = resolveCardDecoration(
            theme = ThemeSettings(
                overrideBaseCardBorder = true,
                baseCardBorderWidth = 4f,
                baseCardBorderColor = 0xFF_11_22_33.toInt(),
            ),
            isDark = false,
            requestedCornerRadius = 12.dp,
            requestedBorder = null,
            fallbackBorderColor = Color.Gray,
        )

        assertEquals(4.dp, decoration.border?.width)
    }
}
