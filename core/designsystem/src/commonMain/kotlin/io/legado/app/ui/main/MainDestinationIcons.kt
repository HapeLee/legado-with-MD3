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
 * M5-19c-pre：从 `:app` 的 `ui/main/MainDestinationIcons.kt` 搬进
 * `:core:designsystem/commonMain`，**包名不变 ⇒ 两处消费方 import 零改动**，
 * 内容**逐字保留**（纯 Compose + designsystem + `miuix-icons` —— 后者 designsystem 早已依赖）。
 *
 * ⚠️ 迁移前这里写着「这段映射属于 app 的导航语义（`MainDestination` 定义在宿主侧），因此留在
 * `:app`」—— 那个前提**正是被同片的 `MainDestination` 上提消掉的**（见其 KDoc）。
 * 保留这段说明是因为它记录了当初的判据：图标映射本身没有平台依赖，是**被数据类型的住所**
 * 牵连在宿主的。
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
