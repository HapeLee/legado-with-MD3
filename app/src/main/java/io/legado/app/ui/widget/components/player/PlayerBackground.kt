package io.legado.app.ui.widget.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.constant.ReadAloudBgMode
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.effect.BgEffectBackground
import io.legado.app.ui.widget.components.effect.BgEffectConfig
import io.legado.app.ui.widget.components.image.cover.CoverBlurBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported

/**
 * 播放器通用背景：纯色 / 封面模糊 / 流动光 / 透明 四种模式。
 */
@Composable
fun PlayerBackground(
    name: String?,
    author: String?,
    path: String?,
    sourceOrigin: String?,
    bgMode: Int,
    modifier: Modifier = Modifier,
) {
    when (bgMode) {
        ReadAloudBgMode.Blur -> {
            CoverBlurBackdrop(
                name, author, path, sourceOrigin,
                modifier = modifier,
            )
        }

        ReadAloudBgMode.FlowingLight -> {
            val shaderSupported = remember { isRuntimeShaderSupported() }
            if (shaderSupported) {
                val coverPreset = rememberCoverDerivedPreset()
                Box(modifier.fillMaxSize()) {
                    CoverBlurBackdrop(
                        name, author, path, sourceOrigin,
                        blurRadius = 64.dp,
                    )
                    BgEffectBackground(
                        dynamicBackground = true,
                        isOs3Effect = true,
                        isFullSize = true,
                        drawSurface = false,
                        customPreset = coverPreset,
                        modifier = Modifier.fillMaxSize(),
                        alpha = { 0.5f },
                    ) {
                        Box(Modifier.fillMaxSize())
                    }
                }
            } else {
                CoverBlurBackdrop(
                    name, author, path, sourceOrigin,
                    modifier = modifier,
                )
            }
        }

        ReadAloudBgMode.Transparent -> {
            Box(modifier = modifier.fillMaxSize())
        }

        else -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(LegadoTheme.colorScheme.surface),
            )
        }
    }
}

@Composable
private fun rememberCoverDerivedPreset(): BgEffectConfig.Config {
    val primary = LegadoTheme.colorScheme.primary
    val surface = LegadoTheme.colorScheme.secondaryContainer
    val tertiary = LegadoTheme.colorScheme.secondary
    val isDark = LegadoTheme.isDark

    return remember(primary, surface, tertiary, isDark) {
        val darken = if (isDark) 0.68f else 0.88f
        val p = primary.copy(
            red = primary.red * darken,
            green = primary.green * darken,
            blue = primary.blue * darken,
        ).toShaderColor()
        val s = surface.copy(
            red = surface.red * darken,
            green = surface.green * darken,
            blue = surface.blue * darken,
        ).toShaderColor()
        val t = tertiary.copy(
            red = tertiary.red * darken,
            green = tertiary.green * darken,
            blue = tertiary.blue * darken,
        ).toShaderColor()

        val m = floatArrayOf(
            (p[0] + t[0]) / 2f,
            (p[1] + t[1]) / 2f,
            (p[2] + t[2]) / 2f,
            1.0f,
        )

        fun stage(a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray): FloatArray =
            floatArrayOf(
                a[0], a[1], a[2], a[3],
                b[0], b[1], b[2], b[3],
                c[0], c[1], c[2], c[3],
                d[0], d[1], d[2], d[3],
            )

        BgEffectConfig.Config(
            points = floatArrayOf(
                0.8f, 0.2f, 1.0f,
                0.8f, 0.9f, 1.0f,
                0.2f, 0.9f, 1.0f,
                0.2f, 0.2f, 1.0f,
            ),
            colors1 = stage(p, t, s, m),
            colors2 = stage(t, s, m, p),
            colors3 = stage(s, m, p, t),
            colorInterpPeriod = 96.0f,
            lightOffset = if (isDark) 0.0f else 0.1f,
            saturateOffset = if (isDark) 0.17f else 0.2f,
            pointOffset = if (isDark) 0.4f else 0.2f,
        )
    }
}

private fun Color.toShaderColor(): FloatArray =
    floatArrayOf(red, green, blue, alpha)

@Composable
fun playerBgModeLabel(mode: Int): String = when (mode) {
    ReadAloudBgMode.Solid -> stringResource(R.string.read_aloud_bg_solid)
    ReadAloudBgMode.Blur -> stringResource(R.string.read_aloud_bg_blur)
    ReadAloudBgMode.FlowingLight -> stringResource(R.string.read_aloud_bg_flowing_light)
    ReadAloudBgMode.Transparent -> stringResource(R.string.read_aloud_bg_transparent)
    else -> stringResource(R.string.read_aloud_bg_blur)
}
