package io.legado.app.ui.main

import androidx.annotation.StringRes
import io.legado.app.R
import kotlinx.collections.immutable.persistentListOf

sealed class MainDestination(
    val route: String,
    @StringRes val labelId: Int
) {
    object Home : MainDestination(
        route = "home",
        labelId = R.string.home
    )

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
