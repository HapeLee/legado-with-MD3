package io.legado.app.ui.theme

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * 当前生效的 UI 引擎。
 *
 * 本仓的间距 / 圆角 / 组件形态是**按引擎条件式**的：Miuix 一套、Material3 一套
 * （最典型的就是内容横向间距 Miuix 12dp / Material3 16dp）。所以共享进 CMP 的第一件事
 * **不是**把那些数字抽成常量 token，而是先把"当前是哪个引擎"这个语义搬进来——token
 * 化会把真实的条件语义抹平成假抽象（见本模块 `build.gradle.kts` 顶部注记）。
 *
 * 这是 `:core:designsystem` 作为 CMP 模块承载的第一件真实东西：消费方是目前
 * `commonMain` 里的 `adaptive*Padding`（被 `:feature:tagrules` 等真实使用）。
 */
public enum class ComposeEngine {
    Miuix,
    Material3,
}

private const val ENGINE_MIUIX = "miuix"

/**
 * 从配置里存的引擎字符串解析；未识别的值回落到 [ComposeEngine.Material3]，
 * 与 [LocalComposeEngine] 的默认值一致（也是 `LegadoThemeMode` 的默认值）。
 */
public fun parseComposeEngine(value: String?): ComposeEngine =
    if (value.equals(ENGINE_MIUIX, ignoreCase = true)) {
        ComposeEngine.Miuix
    } else {
        ComposeEngine.Material3
    }

/**
 * 当前引擎。
 *
 * 由 `:core:ui` 在提供 `LocalLegadoThemeColors` 的同一处提供（那里才有 Android 侧的
 * 主题配置来源）；新增提供点时必须同时提供本 Local，否则间距会静默退回 Material3 刻度。
 */
public val LocalComposeEngine: ProvidableCompositionLocal<ComposeEngine> =
    compositionLocalOf { ComposeEngine.Material3 }
