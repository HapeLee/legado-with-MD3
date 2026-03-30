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
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.ContactsBook
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Settings

sealed class MainDestination(
    val route: String,
    @StringRes val labelId: Int,
    val m3Icon: ImageVector,
    val m3SelectedIcon: ImageVector,
    val miuixIcon: ImageVector,
    val miuixSelectedIcon: ImageVector
) {
    object Bookshelf : MainDestination(
        route = "bookshelf",
        labelId = R.string.bookshelf,
        m3Icon = Icons.AutoMirrored.Outlined.LibraryBooks,
        m3SelectedIcon = Icons.AutoMirrored.Filled.LibraryBooks,
        miuixIcon = MiuixIcons.Regular.ContactsBook, // 替换为实际的 MIUIX 线框图标
        miuixSelectedIcon = MiuixIcons.Heavy.ContactsBook // 替换为实际的 MIUIX 填充图标
    )

    object Explore : MainDestination(
        route = "explore",
        labelId = R.string.discovery,
        m3Icon = Icons.Outlined.Explore,
        m3SelectedIcon = Icons.Default.Explore,
        miuixIcon = MiuixIcons.Regular.Album,
        miuixSelectedIcon = MiuixIcons.Heavy.Album
    )

    object Rss : MainDestination(
        route = "rss",
        labelId = R.string.rss,
        m3Icon = Icons.Outlined.RssFeed,
        m3SelectedIcon = Icons.Default.RssFeed,
        miuixIcon = MiuixIcons.Regular.Favorites,
        miuixSelectedIcon = MiuixIcons.Heavy.Favorites
    )

    object My : MainDestination(
        route = "my",
        labelId = R.string.my,
        m3Icon = Icons.Outlined.Person,
        m3SelectedIcon = Icons.Default.Person,
        miuixIcon = MiuixIcons.Regular.Settings,
        miuixSelectedIcon = MiuixIcons.Heavy.Settings
    )

    companion object {
        val mainDestinations = listOf(Bookshelf, Explore, Rss, My)
    }
}