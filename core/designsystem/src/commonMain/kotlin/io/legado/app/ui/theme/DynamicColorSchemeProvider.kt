package io.legado.app.ui.theme

import androidx.compose.material3.ColorScheme

/**
 * `AppThemeMode.Dynamic`（Material You 动态取色）的平台来源。
 *
 * Android 侧走 `dynamicLightColorScheme` / `dynamicDarkColorScheme`（需要 API 31+ 与
 * `android.content.Context`）——两条都是 Android 独有 API，所以这里只留一个窄契约，
 * 实现由宿主注入；[ThemeEngine] 因此能进 `commonMain`。
 *
 * ⚠️ 与 [ThemeSeedColors] 的失败语义**刻意不同**：未注入时返回 `null` 而不是抛异常。
 * 「没有动态取色」在 desktop / iOS 上是**正常状态**而非配置错误——那些平台本来就没有
 * 系统调色板。`ThemeEngine` 收到 `null` 会回落到预定义配色（`GRColorScheme`），
 * 与 Android 12 以下是同一条路径。要求所有非 Android 宿主都在启动时注入一个「永不命中」
 * 的实现才肯编译，是仪式而不是约束。
 */
fun interface DynamicColorSchemeProvider {
    /** 宿主没有动态取色能力时返回 `null`。 */
    fun colorScheme(darkTheme: Boolean): ColorScheme?
}

object DynamicColorSchemes {

    private var provider: DynamicColorSchemeProvider? = null

    fun install(provider: DynamicColorSchemeProvider) {
        this.provider = provider
    }

    fun colorScheme(darkTheme: Boolean): ColorScheme? = provider?.colorScheme(darkTheme)
}
