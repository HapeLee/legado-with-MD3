package io.legado.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * 应用设置里的字体缩放（10 = 1.0 倍），超出 0.8~1.6 时回落到 [systemFontScale]。
 *
 * [systemFontScale] 由调用方传入而非本模块自行读取：`:core:ui` 拿不到 app 的
 * `appCtx`，系统缩放属于平台侧输入。
 */
fun resolveAppFontScale(fontScaleSetting: Int, systemFontScale: Float): Float =
    (fontScaleSetting / 10f).takeIf { it in 0.8f..1.6f } ?: systemFontScale

/**
 * 以平台像素密度 + 应用字体缩放构造 [Density]。
 *
 * 系统缩放取 [LocalConfiguration] 而非 app 的 `appCtx.resources`：所有 Activity 基类
 * （`BaseActivity` / `BaseComposeActivity`）都会通过 `AppContextWrapper.applyFont` 把
 * `resolveAppFontScale(setting)` 写进 Activity 的 resources 配置，因此这里的
 * `LocalConfiguration.current.fontScale` 与该表达式等值——设置值在 0.8~1.6 区间内时为
 * `setting / 10`，越界时同样是回落后的系统缩放。
 */
@Composable
fun rememberAppDensity(): Density {
    val platformDensity = LocalDensity.current
    val fontScale = resolveAppFontScale(
        fontScaleSetting = LocalAppUiConfiguration.current.appShell.fontScale,
        systemFontScale = LocalConfiguration.current.fontScale,
    )
    return remember(platformDensity.density, fontScale) {
        Density(platformDensity.density, fontScale)
    }
}

/**
 * 在弹窗内部重新提供 [LocalDensity]。
 *
 * Popup/Dialog/ModalBottomSheet 各自持有独立窗口与 AndroidComposeView，子组合会用
 * `Density(context)` 覆盖 [AppTheme] 提供的 [LocalDensity]，从而丢弃应用内的字体缩放设置：
 * 窗口尺寸变化（小窗/全屏切换）后 Activity 的 resources 配置被系统重置，弹窗字号回到 1.0；
 * 修改设置时也要等进程重启才生效。弹窗内容外层套一层本组件即可跟随设置实时生效。
 */
@Composable
fun ProvideAppDensity(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDensity provides rememberAppDensity(), content = content)
}
