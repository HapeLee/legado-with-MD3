package io.legado.app.ui.main

import kotlinx.collections.immutable.persistentListOf

/**
 * M5-19c-pre：从 `:app` 的 `ui/main/MainDestination.kt` 搬进 `:core:designsystem/commonMain`。
 *
 * **包名不变**（`io.legado.app.ui.main`）⇒ 6 处消费方（`MainContract` / `MainScreen` /
 * `MainViewModel` / `ThemeConfigViewModel` / `MainNavigationSettingsSheet` / `NavIconManageSheet`）
 * 的 import **零改动**。触发条件是共享层出现了消费者：`themeConfig` 的两个 sheet 要用它。
 *
 * ⚠️ **不是零改动搬运** —— 只有一处改写：
 * 迁移前是 `@StringRes val labelId: Int` + 每个目的地一个 `R.string.*`。共享层拿不到
 * `androidx.annotation.StringRes` 与 `R` ⇒ 换成**语义枚举** [MainNavLabel]，
 * 由宿主映射回自己的文案（`:app` 的 `ui/main/MainNavLabelText.kt`）。
 * 这与 M5-16a 的 `ThemeManageText`、M5-19b 的 `ThemeConfigToast` 是同一形态：
 * **共享层承载语义，宿主承载文案**。
 *
 * 其余（`route`、5 个目的地对象、`mainDestinations`、`ordered(order)` 的算法）**逐字保留**。
 */
sealed class MainDestination(
    val route: String,
    val label: MainNavLabel,
) {
    object Home : MainDestination(
        route = "home",
        label = MainNavLabel.Home,
    )

    object Bookshelf : MainDestination(
        route = "bookshelf",
        label = MainNavLabel.Bookshelf,
    )

    object Explore : MainDestination(
        route = "explore",
        label = MainNavLabel.Explore,
    )

    object Rss : MainDestination(
        route = "rss",
        label = MainNavLabel.Rss,
    )

    object My : MainDestination(
        route = "my",
        label = MainNavLabel.My,
    )

    companion object {
        val mainDestinations = persistentListOf<MainDestination>(Home, Bookshelf, Explore, Rss, My)

        fun ordered(order: String): List<MainDestination> {
            val byRoute = mainDestinations.associateBy { it.route }
            val ordered = order
                .split(',')
                .map(String::trim)
                .distinct()
                .mapNotNull(byRoute::get)
            return ordered + mainDestinations.filterNot { it in ordered }
        }
    }
}

/**
 * 主导航目的地的**语义**标签（对应 `:app` 的 `R.string.home` / `bookshelf` /
 * `discovery` / `rss` / `my`）。宿主侧 [io.legado.app.ui.main.MainNavLabelText] 把它映射回文案。
 */
enum class MainNavLabel {
    Home,
    Bookshelf,
    Explore,
    Rss,
    My,
}
