package io.legado.app.ui.widget.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.legado.app.domain.model.settings.customColors
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalAppUiConfiguration

object GlassDefaults {

    /**
     * 统一的玻璃颜色处理方法
     * @param noBlurColor 未开启模糊时使用的颜色
     * @param blurAlpha 开启模糊时应用的透明度
     */
    @Composable
    fun glassColor(noBlurColor: Color, blurAlpha: Float): Color {
        return if (LocalAppUiConfiguration.current.theme.enableBlur) {
            noBlurColor.copy(alpha = blurAlpha)
        } else {
            noBlurColor
        }
    }

    @Composable
    fun secondaryColorOr(fallback: @Composable () -> Color): Color {
        val themeSettings = LocalAppUiConfiguration.current.theme
        val secondaryColor = themeSettings.customColors(LegadoTheme.isDark).secondary
        return if (themeSettings.enableDeepPersonalization && secondaryColor != 0) {
            Color(secondaryColor)
        } else {
            fallback()
        }
    }

    val DefaultBlurAlpha = 0.36f
    val ThickBlurAlpha = 0.72f
    val TransparentAlpha = 0f
}
