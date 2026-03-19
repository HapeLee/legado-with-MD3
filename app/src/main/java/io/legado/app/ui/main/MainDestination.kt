package io.legado.app.ui.main

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.ui.graphics.vector.ImageVector
import io.legado.app.R

sealed class MainDestination(
    val route: String,
    @StringRes val labelId: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    object Bookshelf : MainDestination(
        "bookshelf",
        R.string.bookshelf,
        Icons.AutoMirrored.Outlined.LibraryBooks,
        Icons.AutoMirrored.Filled.LibraryBooks
    )

    object Explore : MainDestination(
        "explore",
        R.string.discovery,
        Icons.Outlined.Explore,
        Icons.Default.Explore
    )

    object Rss : MainDestination(
        "rss",
        R.string.rss,
        Icons.Outlined.RssFeed,
        Icons.Default.RssFeed
    )

    object My : MainDestination(
        "my",
        R.string.my,
        Icons.Outlined.Person,
        Icons.Default.Person
    )

    companion object {
        val mainDestinations = listOf(Bookshelf, Explore, Rss, My)
    }
}
