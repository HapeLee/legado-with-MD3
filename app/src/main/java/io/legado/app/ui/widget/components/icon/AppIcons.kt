package io.legado.app.ui.widget.components.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Refresh

/**
 * 应用全局图标映射层
 */
object AppIcons {

    private val isMiuix: Boolean
        @Composable
        get() = ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)

    val Search: ImageVector
        @Composable
        get() = if (isMiuix) {
            MiuixIcons.Basic.Search
        } else {
            Icons.Default.Search
        }

    val Close: ImageVector
        @Composable
        get() = if (isMiuix) {
            MiuixIcons.Close
        } else {
            Icons.Default.Clear
        }

    val Back: ImageVector
        @Composable
        get() = if (isMiuix) {
            MiuixIcons.Back
        } else {
            Icons.AutoMirrored.Filled.ArrowBack
        }

    val Replay: ImageVector
        @Composable
        get() = if (isMiuix) MiuixIcons.Refresh ?: Icons.Default.Replay else Icons.Default.Replay
}