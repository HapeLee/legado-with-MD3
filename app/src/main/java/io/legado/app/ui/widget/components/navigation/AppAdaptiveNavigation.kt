package io.legado.app.ui.widget.components.navigation

import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.IntSize
import androidx.window.core.layout.WindowSizeClass

/**
 * `tabletInterface` 的取值，与 `pref_config_theme.xml` 引用的
 * `array_values.xml#tabletInterface_value` 保持一致。
 */
@Stable
enum class TabletInterfaceMode(val value: String) {
    Auto("auto"),
    Always("always"),
    Landscape("landscape"),
    Off("off");

    companion object {
        /** 未知 / 遗留取值沿用迁移前的兜底语义：不开侧栏。 */
        fun parse(raw: String?): TabletInterfaceMode =
            entries.firstOrNull { it.value == raw } ?: Off
    }
}

/**
 * 主页导航是否走侧栏（M3 `WideNavigationRail` / Miuix `NavigationRail`）。
 *
 * 判据必须来自**当前窗口**而不是设备：Android 16（targetSdk 36+）起，系统不再执行
 * orientation / aspectRatio / resizability 限制，同一个平板可以在自由窗口里被缩到手机宽度，
 * 此时 `Configuration.smallestScreenWidthDp` 仍是设备值，会把侧栏塞进一条放不下的窄窗口。
 * 因此这里用 WindowSizeClass 的宽度断点替代设备最小宽度，用真实窗口像素尺寸判定横竖。
 */
fun shouldUseRail(
    tabletInterface: String,
    windowSizeClass: WindowSizeClass,
    windowSize: IntSize,
): Boolean {
    // User preference cannot make a sidebar usable in a tiny or short app window.
    val railFits = windowSizeClass.isWidthAtLeastBreakpoint(456) &&
            windowSizeClass.isHeightAtLeastBreakpoint(480)
    if (!railFits) return false
    val isLandscape = windowSize.width > windowSize.height
    return when (TabletInterfaceMode.parse(tabletInterface)) {
        TabletInterfaceMode.Always -> true
        TabletInterfaceMode.Landscape -> isLandscape
        // 600dp 是 WindowSizeClass 的 MEDIUM 下界，与旧的 `smallestScreenWidthDp >= 600`
        // 阈值一致，但按当前窗口宽度计算。
        TabletInterfaceMode.Auto ->
            windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

        TabletInterfaceMode.Off -> false
    }
}

@Stable
enum class MultiPaneMode(val value: String) {
    Auto("auto"),
    Landscape("landscape"),
    Always("always"),
    Off("off");

    companion object {
        fun parse(raw: String?): MultiPaneMode = entries.firstOrNull { it.value == raw } ?: Auto
    }
}

/**
 * 自动和横屏模式使用 840dp 断点；始终开启允许较小窗口，但保留
 * 360dp 左栏 + 24dp 间隔 + 360dp 右栏的最低空间。方向按当前窗口判断。
 */
fun shouldUseListDetail(
    multiPaneMode: String,
    windowSizeClass: WindowSizeClass,
    windowSize: IntSize,
): Boolean = when (MultiPaneMode.parse(multiPaneMode)) {
    MultiPaneMode.Auto -> windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND
    )

    MultiPaneMode.Landscape -> windowSize.width > windowSize.height &&
            windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

    MultiPaneMode.Always -> windowSizeClass.isWidthAtLeastBreakpoint(744)
    MultiPaneMode.Off -> false
}

