package io.legado.app.ui.theme

import android.content.Context

/**
 * 自定义配色未指定 seed 时的回退主色来源。
 *
 * 这个值在 app 侧来自主题偏好存储（`lib/theme/ThemeStore`：先读 `primary_color` 偏好，
 * 缺失时回落到 `?attr/colorPrimary`）。它是**应用层状态**，不是共享 UI 概念，所以
 * `:core:ui` 不能直接依赖 `ThemeStore`——那会把一套遗留主题存储拖进共享模块。
 *
 * 因此沿用本仓库既有的「接口 + provider 注入」手法（同 `BigDataStore`、
 * `SourceRuntime`）：由宿主在 `Application.onCreate` 里 [install]，取用时未注入则
 * **直接抛异常**，不做静默兜底，避免共享层悄悄换了一套主色语义。
 */
fun interface ThemeSeedColorProvider {
    fun primaryColor(context: Context): Int
}

object ThemeSeedColors {

    private var provider: ThemeSeedColorProvider? = null

    fun install(provider: ThemeSeedColorProvider) {
        this.provider = provider
    }

    fun primaryColor(context: Context): Int =
        requireNotNull(provider) {
            "ThemeSeedColorProvider 未注入：宿主必须在 Application.onCreate 中调用 " +
                "ThemeSeedColors.install(...)，否则自定义配色的回退主色无从确定"
        }.primaryColor(context)
}
