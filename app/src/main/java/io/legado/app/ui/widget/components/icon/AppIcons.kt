package io.legado.app.ui.widget.components.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import io.legado.app.ui.main.MainDestination
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.ContactsBook
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings

object AppIcons {

    private val isMiuix: Boolean
        @Composable
        get() = ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)

    val Search: ImageVector
        @Composable
        get() = if (isMiuix) MiuixIcons.Basic.Search else Icons.Default.Search

    val Close: ImageVector
        @Composable
        get() = if (isMiuix) MiuixIcons.Close else Icons.Default.Clear

    val Back: ImageVector
        @Composable
        get() = if (isMiuix) MiuixIcons.Back else Icons.AutoMirrored.Filled.ArrowBack

    val Replay: ImageVector
        @Composable
        get() = if (isMiuix) MiuixIcons.Refresh ?: Icons.Default.Replay else Icons.Default.Replay

    @Composable
    fun mainDestination(destination: MainDestination, selected: Boolean): ImageVector {
        return when (destination) {
            MainDestination.Bookshelf -> if (isMiuix) {
                if (selected) MiuixIcons.Heavy.ContactsBook else MiuixIcons.Regular.ContactsBook
            } else {
                if (selected) Icons.AutoMirrored.Filled.LibraryBooks else Icons.AutoMirrored.Outlined.LibraryBooks
            }

            MainDestination.Explore -> if (isMiuix) {
                if (selected) MiuixIcons.Heavy.Album else MiuixIcons.Regular.Album
            } else {
                if (selected) Icons.Default.Explore else Icons.Outlined.Explore
            }

            MainDestination.Rss -> if (isMiuix) {
                if (selected) MiuixIcons.Heavy.Favorites else MiuixIcons.Regular.Favorites
            } else {
                if (selected) Icons.Default.RssFeed else Icons.Outlined.RssFeed
            }

            MainDestination.My -> if (isMiuix) {
                if (selected) MiuixIcons.Heavy.Settings else MiuixIcons.Regular.Settings
            } else {
                if (selected) Icons.Default.Person else Icons.Outlined.Person
            }
        }
    }
}
