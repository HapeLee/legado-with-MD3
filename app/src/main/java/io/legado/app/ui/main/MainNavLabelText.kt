package io.legado.app.ui.main

import androidx.annotation.StringRes
import io.legado.app.R

/**
 * M5-19c-pre：把共享层的语义枚举 [MainNavLabel] 映射回宿主自己的文案。
 *
 * `MainDestination` 搬进 `:core:designsystem` 后，`labelId: Int`（`@StringRes`）换成了枚举
 * —— 共享层拿不到 `androidx.annotation.StringRes` 与 `R`。这与 `themeManage`(M5-16a) 的
 * `ThemeManageText.toStringRes()`、`themeConfig`(M5-19b) 的 `ThemeConfigToast.toStringRes()`
 * 是同一判据：**共享层承载语义，宿主承载文案**。
 *
 * 放在 `:app` 的 `ui/main/` 而不是某个页面文件里：它同时被 `MainScreen`（6 处）与
 * `themeConfig` 的 `MainNavigationSettingsSheet`（3 处）使用。
 */
@StringRes
fun MainNavLabel.toRes(): Int = when (this) {
    MainNavLabel.Home -> R.string.home
    MainNavLabel.Bookshelf -> R.string.bookshelf
    MainNavLabel.Explore -> R.string.discovery
    MainNavLabel.Rss -> R.string.rss
    MainNavLabel.My -> R.string.my
}
