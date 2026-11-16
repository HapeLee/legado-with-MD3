package io.legado.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 当前子树是否处于 [ProvideColorSchemeOverride] 的局部色板覆盖内，以及覆盖的内容。
 * 由 [ProvideThemeOverride] 提供，用来把页面级色板带进独立 composition。
 */
val LocalThemeOverride = staticCompositionLocalOf<ThemeOverrideState?> { null }

/**
 * 在**调用点所在的 composition** 捕获当前生效的局部色板覆盖。
 *
 * 玻璃 sheet / dialog 的内容会被注册到最外层 Scaffold 的 popup host，由另一个 composition
 * 执行；那里不在页面级 [ProvideColorSchemeOverride] 的祖先链上，直接读
 * `LocalLegadoThemeColors` 只能拿到全局色板。因此必须在宿主侧取值，再交给
 * [ProvidePopupThemeOverride] 在内容侧重新提供。
 *
 * 用 `State` 而不是 `Color` / `ColorScheme` 值：阅读背景、夜间模式以及
 * `BookInfoScreen` 的逐帧动画色板都会变化，直接捕获值会重演「首帧固化」。
 */
@Composable
fun rememberPopupThemeOverride(): State<ThemeOverrideState?> =
    rememberUpdatedState(LocalThemeOverride.current)

/** 在 popup host 的独立 composition 里重新提供宿主侧捕获到的色板覆盖。 */
@Composable
fun ProvidePopupThemeOverride(
    themeOverride: State<ThemeOverrideState?>,
    content: @Composable () -> Unit,
) {
    ProvideThemeOverride(theme = themeOverride.value, content = content)
}
