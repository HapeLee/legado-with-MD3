package io.legado.app.ui.main.ai.compose

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.composeActionRadius
import io.legado.app.lib.theme.composePanelRadius
import io.legado.app.utils.ColorUtils

@Immutable
data class AiComposeColors(
    val accent: Color,
    val background: Color,
    val pageBackground: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val cardSurface: Color,
    val composerSurface: Color,
    val composerStroke: Color,
    val userBubble: Color,
    val userBubbleStroke: Color,
    val userText: Color,
    val assistantBubble: Color,
    val assistantBubbleStroke: Color,
    val processSurface: Color,
    val toolSurface: Color,
    val stroke: Color,
    val danger: Color
)

@Immutable
data class AiComposeMetrics(
    val cardRadius: Dp,
    val chipRadius: Dp,
    val strokeWidth: Dp
)

@Immutable
data class AiComposeStyle(
    val colors: AiComposeColors,
    val metrics: AiComposeMetrics
)

@Stable
fun aiComposeStyle(context: Context): AiComposeStyle {
    val night = AppConfig.isNightTheme
    val pageBackground = if (night) {
        ContextCompat.getColor(context, R.color.md_grey_900)
    } else {
        0xfff3f6f8.toInt()
    }
    val background = pageBackground
    val accent = context.accentColor
    val baseIsLight = !night
    val primaryText = if (night) 0xfff2f3f5.toInt() else 0xff202124.toInt()
    val secondaryText = if (night) 0xffaeb4bc.toInt() else 0xff6b7178.toInt()
    val cardSurface = if (baseIsLight) {
        0xfffbfcfe.toInt()
    } else {
        0xff24262b.toInt()
    }
    val composerSurface = if (baseIsLight) {
        ColorUtils.blendColors(0xfff7f9fc.toInt(), accent, 0.08f)
    } else {
        ColorUtils.blendColors(0xff22252a.toInt(), accent, 0.10f)
    }
    val assistantBubble = if (baseIsLight) 0xfffbfcfe.toInt() else 0xff2a2d33.toInt()
    val userBubble = ColorUtils.blendColors(
        if (baseIsLight) 0xffffffff.toInt() else 0xff202329.toInt(),
        accent,
        if (baseIsLight) 0.18f else 0.28f
    )
    val userText = if (ColorUtils.isColorLight(userBubble)) 0xff202124.toInt() else 0xffffffff.toInt()
    val processSurface = if (baseIsLight) 0xffeef3f7.toInt() else 0xff242a31.toInt()
    val toolSurface = ColorUtils.blendColors(cardSurface, accent, if (baseIsLight) 0.10f else 0.14f)
    val stroke = if (baseIsLight) 0x14000000 else 0x24ffffff
    return AiComposeStyle(
        colors = AiComposeColors(
            accent = Color(accent),
            background = Color(background),
            pageBackground = Color(pageBackground),
            primaryText = Color(primaryText),
            secondaryText = Color(secondaryText),
            cardSurface = Color(cardSurface),
            composerSurface = Color(composerSurface),
            composerStroke = Color(ColorUtils.adjustAlpha(accent, if (baseIsLight) 0.18f else 0.24f)),
            userBubble = Color(userBubble),
            userBubbleStroke = Color(ColorUtils.adjustAlpha(accent, if (baseIsLight) 0.28f else 0.36f)),
            userText = Color(userText),
            assistantBubble = Color(assistantBubble),
            assistantBubbleStroke = Color(stroke),
            processSurface = Color(processSurface),
            toolSurface = Color(toolSurface),
            stroke = Color(stroke),
            danger = Color(0xfff44336.toInt())
        ),
        metrics = AiComposeMetrics(
            cardRadius = context.composePanelRadius(),
            chipRadius = context.composeActionRadius(),
            strokeWidth = 1.dp
        )
    )
}
