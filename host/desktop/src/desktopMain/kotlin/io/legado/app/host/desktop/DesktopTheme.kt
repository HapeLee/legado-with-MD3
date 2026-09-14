package io.legado.app.host.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import io.legado.app.ui.theme.LocalLegadoColorScheme
import io.legado.app.ui.theme.LocalLegadoTypography
import io.legado.app.ui.theme.toLegadoColorScheme
import io.legado.app.ui.theme.toLegadoTypography
import io.legado.app.ui.theme.withFont

/**
 * desktop host 的最小主题：把 Material3 的 `ColorScheme` / `Typography` 映射成
 * designsystem 组件真正读取的那两个 CompositionLocal。
 *
 * **为什么必须自己搭一层**：`:core:designsystem` 里的组件（`RoundDropdownMenuItem`、
 * `AppModalBottomSheet`、`FilePickerSheet`…）读的是 `LegadoTheme.colorScheme`，背后是
 * `LocalLegadoColorScheme`，而它的默认值是 `error("No ColorScheme provided")`。
 * Android 侧由 `:core:ui` 的 `AppTheme` 提供（还带 Miuix、动态取色、自定义字体），
 * 但 `:core:ui` 是 **Android-only** ⇒ desktop 拿不到，一渲染就抛 `IllegalStateException`。
 *
 * 所以这里用 `MaterialTheme` 打底，再用 M1-4 上提到 `commonMain` 的两个纯映射函数
 * （`toLegadoColorScheme` / `toLegadoTypography`）补上那两个 Local。
 *
 * ⚠️ 这是**最小可用**主题，不是 `AppTheme` 的等价物：没有自定义字体（[withFont] 传 null）、
 * 没有 Miuix 引擎切换、没有动态取色。desktop 上界面观感与 Android 不同是正常的；
 * 把 `AppTheme` 完整搬进共享层是独立的切片，不在 M1-4 范围。
 */
@Composable
fun DesktopTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme {
        CompositionLocalProvider(
            LocalLegadoColorScheme provides MaterialTheme.colorScheme.toLegadoColorScheme(),
            LocalLegadoTypography provides
                MaterialTheme.typography.toLegadoTypography().withFont(null),
        ) {
            content()
        }
    }
}
