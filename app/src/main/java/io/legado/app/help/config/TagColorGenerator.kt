package io.legado.app.help.config

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import io.legado.app.ui.config.themeConfig.TagColorPair

object TagColorGenerator {

    fun generateTagColors(baseColor: Color): List<TagColorPair> {
        val baseHsl = FloatArray(3)
        ColorUtils.colorToHSL(baseColor.toArgb(), baseHsl)
        val baseHue = baseHsl[0]

        val result = mutableListOf<TagColorPair>()

        val hueOffsets = listOf(0, 45, 90, 135, 180, 225, 270, 315)

        hueOffsets.forEach { offset ->
            val newHue = (baseHue + offset) % 360

            val textHsl = floatArrayOf(newHue, 0.75f, 0.45f)
            val textColor = Color.hsl(textHsl[0], textHsl[1], textHsl[2]).toArgb()

            val bgHsl = floatArrayOf(newHue, 0.35f, 0.90f)
            val bgColor = Color.hsl(bgHsl[0], bgHsl[1], bgHsl[2]).toArgb()

            result.add(TagColorPair(textColor, bgColor))
        }

        return result
    }
}
