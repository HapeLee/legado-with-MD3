package io.legado.app.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.legado.app.ui.config.themeConfig.TagColorPair

/**
 * 按基准色生成 8 个「标签配色」（色相每 45° 一档）。
 *
 * M5-19d：从 `:app` 的 `help/config/TagColorGenerator.kt` 搬进
 * `:core:designsystem/commonMain`。触发条件是共享层出现消费者：
 * `themeConfig` 的 `LabelColorManageSheet` 要迁进来（它是本类的**唯一**消费者）。
 *
 * ⚠️ **包名变了**（`io.legado.app.help.config` → `io.legado.app.utils`），这不只是审美：
 * 它搬进共享层后若仍留在 `help.config` 包下，共享模块就会**首次出现 `import io.legado.app.help.*`**
 * —— 而 G4 护栏把 `help.*` 导入当作「`:app` 私有耦合」的信号（`legacy-baseline.txt` 的
 * `legacyHelp` 规则）。本类其实**已不在 `:app`**，那条信号会是纯假阳性。与其把假条目登记进基线
 * （基线是"只降不升"的棘轮，且"新区域必须为零"），不如换个如实的住所。
 * 消费者只有那个 sheet 一个（同片一并改 import）⇒ 依旧是零额外改动。
 *
 * ⚠️ **一处实现改写**：原用 `androidx.core.graphics.ColorUtils.colorToHSL(argb, hsl)`
 * 求基准色相，而 `ColorUtils` 是 Android-only 制品 ⇒ 换成同等价的纯算式 [colorToHsl]
 * （算法逐句对应，等价性由 `ColorHslTest` 的已知值用例锁住）。其余（8 档色相偏移、
 * `Color.hsl(...).toArgb()`、文字色 S=0.75/L=0.45、背景色 S=0.35/L=0.90、返回顺序）
 * **逐字保留**。
 *
 * ⚠️ 它与**同名旧包** `io.legado.app.help.config` 下那一大块（`ThemePackageManager` 等）无关 ——
 * 那部分带 `Context`/`Uri`/GSON，留在 `:app`。
 */
object TagColorGenerator {

    fun generateTagColors(baseColor: Color): List<TagColorPair> {
        val baseHsl = colorToHsl(baseColor.toArgb())
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
