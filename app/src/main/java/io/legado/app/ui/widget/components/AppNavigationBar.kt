package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem

@Composable
fun AppNavigationBar(
    modifier: Modifier = Modifier,
    miuixMode: NavigationBarDisplayMode = NavigationBarDisplayMode.IconAndText,
    content: @Composable RowScope.() -> Unit
) {
    val isMiuix = ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)
    val opacity = (ThemeConfig.bottomBarOpacity.coerceIn(0, 100)) / 100f

    if (isMiuix) {
        val baseColor = GlassDefaults.glassColor(
            noBlurColor = MiuixTheme.colorScheme.surface,
            blurAlpha = GlassDefaults.DefaultBlurAlpha
        )
        val finalColor = baseColor.copy(alpha = (baseColor.alpha * opacity).coerceIn(0f, 1f))

        MiuixNavigationBar(
            modifier = modifier,
            color = finalColor,
            mode = miuixMode,
            content = content
        )
    } else {
        val baseColor = GlassDefaults.glassColor(
            noBlurColor = BottomAppBarDefaults.containerColor,
            blurAlpha = GlassDefaults.DefaultBlurAlpha
        )
        val finalColor = baseColor.copy(alpha = (baseColor.alpha * opacity).coerceIn(0f, 1f))

        NavigationBar(
            modifier = modifier,
            containerColor = finalColor,
            content = content
        )
    }
}

@Composable
fun RowScope.AppNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    labelString: String,
    miuixIcon: ImageVector,
    m3Icon: @Composable () -> Unit,
    m3IndicatorColor: Color,
    m3ShowLabel: Boolean,
    m3AlwaysShowLabel: Boolean,
) {
    val isMiuix = ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)

    if (isMiuix) {
        MiuixNavigationBarItem(
            selected = selected,
            onClick = onClick,
            icon = miuixIcon,
            label = labelString,
            modifier = modifier
        )
    } else {
        NavigationBarItem(
            selected = selected,
            onClick = onClick,
            modifier = modifier,
            icon = m3Icon,
            colors = NavigationBarItemDefaults.colors(indicatorColor = m3IndicatorColor),
            label = if (m3ShowLabel) {
                { Text(labelString) }
            } else null,
            alwaysShowLabel = m3AlwaysShowLabel
        )
    }
}