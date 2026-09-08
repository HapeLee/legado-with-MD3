package io.legado.app.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.ContactsBook
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Settings

/**
 * 主导航目的地 → 图标。
 *
 * 这段映射属于 app 的导航语义（`MainDestination` 定义在宿主侧），因此留在 `:app`：
 * `AppIcons` 只保留与导航无关的通用图标，才能下沉到 `:core:ui` 而不反向依赖宿主。
 */
@Composable
fun mainDestinationIcon(destination: MainDestination, selected: Boolean): ImageVector {
    val isMiuix = ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)
    return when (destination) {
        MainDestination.Home -> if (isMiuix) {
            MiuixIcons.Regular.ContactsBook
        } else {
            if (selected) Icons.Default.Home else Icons.Outlined.Home
        }

        MainDestination.Bookshelf -> if (isMiuix) {
            MiuixIcons.Regular.Notes
        } else {
            if (selected) {
                Icons.AutoMirrored.Filled.LibraryBooks
            } else {
                Icons.AutoMirrored.Outlined.LibraryBooks
            }
        }

        MainDestination.Explore -> if (isMiuix) {
            MiuixIcons.Regular.Album
        } else {
            if (selected) Icons.Default.Explore else Icons.Outlined.Explore
        }

        MainDestination.Rss -> if (isMiuix) {
            MiuixIcons.Regular.Favorites
        } else {
            if (selected) Icons.Default.RssFeed else Icons.Outlined.RssFeed
        }

        MainDestination.My -> if (isMiuix) {
            MiuixIcons.Regular.Settings
        } else {
            if (selected) Icons.Default.Person else Icons.Outlined.Person
        }
    }
}
