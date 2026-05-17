package io.legado.app.ui.main

import androidx.annotation.StringRes
import io.legado.app.R
import io.legado.app.ui.config.themeConfig.ThemeConfig
import kotlinx.collections.immutable.persistentListOf

sealed class MainDestination(
    val route: String,
    @StringRes val labelId: Int
) {
    object Bookshelf : MainDestination(
        route = "bookshelf",
        labelId = R.string.bookshelf
    )

    object Explore : MainDestination(
        route = "explore",
        labelId = R.string.discovery
    )

    object Rss : MainDestination(
        route = "rss",
        labelId = R.string.rss
    )

    object My : MainDestination(
        route = "my",
        labelId = R.string.my
    )

    companion object {
        val mainDestinations = persistentListOf<MainDestination>(Bookshelf, Explore, Rss, My)
    }
}

val MainDestination.customIconPath: String
    get() = when (this) {
        MainDestination.Bookshelf -> ThemeConfig.navIconBookshelf
        MainDestination.Explore -> ThemeConfig.navIconExplore
        MainDestination.Rss -> ThemeConfig.navIconRss
        MainDestination.My -> ThemeConfig.navIconMy
    }
