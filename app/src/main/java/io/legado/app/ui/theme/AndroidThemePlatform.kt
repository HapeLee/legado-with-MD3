package io.legado.app.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import io.legado.app.lib.theme.primaryColor

/**
 * 注入 `ThemeEngine` / `rememberOpaqueColorScheme` 需要的两项平台能力（M1-3l）。
 *
 * 配色引擎（`ThemeEngine`、`OpaqueColorScheme`、`BaseColorScheme`、12 个预定义配色）已从
 * `:core:ui` 搬进 `:core:designsystem` 的 `commonMain`——搬动时剥掉了其中仅有的两处
 * `android.*`：动态取色（`dynamicLight/DarkColorScheme(context)` + `Build.VERSION.SDK_INT`）
 * 与回退主色（`Context.primaryColor`）。它们在这里以窄契约实现注入。
 *
 * **为什么实现落在 `app` 而不是 `:core:ui`**：回退主色来自 `lib.theme.ThemeStore`，
 * 那是 app 模块的遗留主题存储；`:core:ui` 依赖不到 `app`。
 *
 * **为什么 `context` 是入参而不是取全局 `appCtx`**：本仓 G4 门禁
 * （`checkLegacyArchitecture`）把「新区域新增全局 Context 直连」判为 blocking——
 * `appCtx` 是隐藏的全局耦合，新代码里应由调用方显式传入。启动时调用点只有
 * `App.onCreate`，那里 `this` 就是 Application。
 *
 * ### 与原实现的两点差异（均为上下文来源，不是算法）
 *
 * 1. 动态取色原先用调用点的 `LocalContext`（通常是 Activity），现在用 Application。
 *    系统调色板取自 `context.resources` 的 `android.R.color.system_*` 与 DeviceConfig，
 *    与 Activity 的 theme 无关 ⇒ **无可观察差异**。
 * 2. 回退主色原先用调用点 `Context` 解析 `?attr/colorPrimary`，现在同样用 Application。
 *    该回退**当前是死代码**（见 `ThemeSeedColorProvider` 的注释：三个调用点都传非空
 *    `customSeedColor`，`OpaqueColorScheme` 又走不到 `Custom` 分支）⇒ **无可观察差异**。
 */
fun installAndroidThemePlatform(context: Context) {
    ThemeSeedColors.install { context.primaryColor }
    DynamicColorSchemes.install { darkTheme ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            // Android 12 以下没有动态取色：返回 null，由 ThemeEngine 回落预定义配色。
            null
        } else if (darkTheme) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
    }
}
