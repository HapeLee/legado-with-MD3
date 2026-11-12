package io.legado.app.ui.widget.components.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.itos.coui.icons.CouiIcons
import io.legado.app.ui.main.MainDestination
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.ContactsBook
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Unpin
import top.yukonga.miuix.kmp.icon.extended.WorldClock

/**
 * 三大引擎（Material / Miuix / COUI）的图标出口，Mochi 复用 Material 图标。
 *
 * COUI 图标库只收录系统设置、云服务与相册一类图形，覆盖面小于 Material Icons：
 * 没有编辑、删除、勾选、历史、刷新、信息等图形。这里只做语义确实等价的映射，
 * 缺项一律落到 Material 图标，保证调用方无需关心引擎差异。
 */
object AppIcons {

    private val engine: ComposeEngine
        @Composable
        get() = LegadoTheme.composeEngine

    /**
     * 行首图标（菜单项、设置项）。
     *
     * COUI 的原生菜单与设置列表不带行首图标，与 Miuix 设置项（`ArrowPreference` /
     * `SwitchPreference` 不接收图标）保持同一取向，因此 Coui 返回 null 由组件跳过整块图标；
     * Material/Mochi 与 Miuix 菜单（仍要保留图标）原样返回。
     */
    @Composable
    fun leadingOrNull(icon: ImageVector): ImageVector? = when (engine) {
        ComposeEngine.Coui -> null
        else -> icon
    }

    val Search: ImageVector
        @Composable
        get() = when (engine) {
            ComposeEngine.Miuix -> MiuixIcons.Search
            ComposeEngine.Coui -> CouiIcons.Outlined.Search
            else -> Icons.Default.Search
        }

    val MoreVert: ImageVector
        @Composable
        get() = when (engine) {
            ComposeEngine.Miuix -> MiuixIcons.More
            ComposeEngine.Coui -> CouiIcons.Filled.More
            else -> Icons.Default.MoreVert
        }

    // COUI 没有编辑图标，与 Material/Mochi 共用 Material 图标
    val Edit: ImageVector
        @Composable
        get() = when (engine) {
            ComposeEngine.Miuix -> MiuixIcons.Edit
            else -> Icons.Default.Edit
        }

    // COUI 没有删除图标
    val Delete: ImageVector
        @Composable
        get() = when (engine) {
            ComposeEngine.Miuix -> MiuixIcons.Delete
            else -> Icons.Default.Delete
        }

    val Close: ImageVector
        @Composable
        get() = when (engine) {
            ComposeEngine.Miuix -> MiuixIcons.Close
            ComposeEngine.Coui -> CouiIcons.Filled.Close
            else -> Icons.Default.Clear
        }

    val Back: ImageVector
        @Composable
        get() = when (engine) {
            ComposeEngine.Miuix -> MiuixIcons.ChevronBackward
            ComposeEngine.Coui -> CouiIcons.Filled.Back
            else -> Icons.AutoMirrored.Filled.ArrowBack
        }

    val Filter: ImageVector
        @Composable
        get() = when (engine) {
            ComposeEngine.Miuix -> MiuixIcons.Filter
            ComposeEngine.Coui -> CouiIcons.Outlined.ListFilter
            else -> Icons.Outlined.FilterAlt
        }

    val Settings: ImageVector
        @Composable
        get() = when (engine) {
            ComposeEngine.Miuix -> MiuixIcons.Settings
            ComposeEngine.Coui -> CouiIcons.Outlined.Setting
            else -> Icons.Default.Settings
        }

    // COUI 没有缺陷报告图标
    val BugReport: ImageVector
        @Composable
        get() = Icons.Default.BugReport

    // 精确/非精确搜索是一对开关图标，COUI 只有定位图钉、没有对应的「未定位」图形，
    // 两态都保持 Material 图标，避免切换时视觉不成对
    val PrecisionSearch: ImageVector
        @Composable
        get() = when (engine) {
            ComposeEngine.Miuix -> MiuixIcons.Pin
            else -> Icons.Default.MyLocation
        }

    val UnPrecisionSearch: ImageVector
        @Composable
        get() = when (engine) {
            ComposeEngine.Miuix -> MiuixIcons.Unpin
            else -> Icons.Default.LocationSearching
        }

    // COUI 没有时钟类图标，历史记录保持 Material 图标
    val History: ImageVector
        @Composable
        get() = when (engine) {
            ComposeEngine.Miuix -> MiuixIcons.WorldClock
            else -> Icons.Default.History
        }

    // COUI 的 Next 是前进箭头而非刷新，重试保持 Material 图标
    val Replay: ImageVector
        @Composable
        get() = when (engine) {
            ComposeEngine.Miuix -> MiuixIcons.Refresh
            else -> Icons.Default.Replay
        }

    val MoreCircle: ImageVector
        @Composable
        get() = when (engine) {
            ComposeEngine.Miuix -> MiuixIcons.MoreCircle
            ComposeEngine.Coui -> CouiIcons.Filled.More
            else -> Icons.Default.MoreHoriz
        }

    // COUI 没有勾选图标
    val Check: ImageVector
        @Composable
        get() = Icons.Default.Check

    val Add: ImageVector
        @Composable
        get() = when (engine) {
            ComposeEngine.Miuix -> MiuixIcons.Add
            ComposeEngine.Coui -> CouiIcons.Filled.Add
            else -> Icons.Default.Add
        }

    // COUI 没有信息图标（SettingDetail 是设置齿轮，不能替代）
    val Info: ImageVector
        @Composable
        get() = when (engine) {
            ComposeEngine.Miuix -> MiuixIcons.Info
            else -> Icons.Outlined.Info
        }

    val Group: ImageVector
        @Composable
        get() = when (engine) {
            ComposeEngine.Miuix -> MiuixIcons.Filter
            ComposeEngine.Coui -> CouiIcons.Filled.Users
            else -> Icons.Outlined.Sell
        }

    // ————————————— 播放器专用：Miuix/COUI 没有对应图标，各引擎共用 Material 图标 —————————————

    val Previous: ImageVector
        @Composable
        get() = Icons.Default.SkipPrevious

    val Next: ImageVector
        @Composable
        get() = Icons.Default.SkipNext

    val Speed: ImageVector
        @Composable
        get() = Icons.Default.Speed

    val Episodes: ImageVector
        @Composable
        get() = Icons.AutoMirrored.Filled.List

    val Fullscreen: ImageVector
        @Composable
        get() = Icons.Default.Fullscreen

    val FullscreenExit: ImageVector
        @Composable
        get() = Icons.Default.FullscreenExit

    val PictureInPicture: ImageVector
        @Composable
        get() = Icons.Default.PictureInPictureAlt

    val MiniWindow: ImageVector
        @Composable
        get() = Icons.Default.OpenInNew

    val ExpandMore: ImageVector
        @Composable
        get() = Icons.Default.KeyboardArrowDown

    @Composable
    fun mainDestination(destination: MainDestination, selected: Boolean): ImageVector {
        return when (engine) {
            ComposeEngine.Miuix -> when (destination) {
                MainDestination.Home -> MiuixIcons.Regular.ContactsBook
                MainDestination.Bookshelf -> MiuixIcons.Regular.Notes
                MainDestination.Explore -> MiuixIcons.Regular.Album
                MainDestination.Rss -> MiuixIcons.Regular.Favorites
                MainDestination.My -> MiuixIcons.Regular.Settings
            }

            ComposeEngine.Coui -> when (destination) {
                MainDestination.Home -> CouiIcons.Filled.Grid
                MainDestination.Bookshelf -> CouiIcons.Filled.BookOpen
                MainDestination.Explore ->
                    if (selected) CouiIcons.Filled.WandSpark else CouiIcons.Outlined.WandSpark

                MainDestination.Rss ->
                    if (selected) CouiIcons.Filled.CloudLink else CouiIcons.Outlined.CloudLink

                MainDestination.My -> CouiIcons.Filled.UserCircle
            }

            else -> when (destination) {
                MainDestination.Home -> if (selected) Icons.Default.Home else Icons.Outlined.Home
                MainDestination.Bookshelf -> if (selected) {
                    Icons.AutoMirrored.Filled.LibraryBooks
                } else {
                    Icons.AutoMirrored.Outlined.LibraryBooks
                }

                MainDestination.Explore -> if (selected) Icons.Default.Explore else Icons.Outlined.Explore
                MainDestination.Rss -> if (selected) Icons.Default.RssFeed else Icons.Outlined.RssFeed
                MainDestination.My -> if (selected) Icons.Default.Person else Icons.Outlined.Person
            }
        }
    }
}
