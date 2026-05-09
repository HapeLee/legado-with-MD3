package io.legado.app.ui.widget.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalHazeState
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.regularHazeEffect
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
    val hazeState = LocalHazeState.current
    val hazeModifier = if (hazeState != null) {
        Modifier.regularHazeEffect(hazeState)
    } else {
        Modifier
    }

    if (isMiuix) {
        val baseColor = if (ThemeConfig.enableDeepPersonalization && ThemeConfig.secondaryThemeColor != 0) {
            Color(ThemeConfig.secondaryThemeColor)
        } else {
            GlassDefaults.glassColor(
                noBlurColor = MiuixTheme.colorScheme.surface,
                blurAlpha = GlassDefaults.TransparentAlpha
            )
        }
        val finalColor = baseColor.copy(alpha = (baseColor.alpha * opacity).coerceIn(0f, 1f))

        MiuixNavigationBar(
            modifier = modifier.then(hazeModifier),
            color = finalColor,
            mode = miuixMode,
            content = content
        )
    } else {
        val baseColor = if (ThemeConfig.enableDeepPersonalization && ThemeConfig.secondaryThemeColor != 0) {
            Color(ThemeConfig.secondaryThemeColor)
        } else {
            GlassDefaults.glassColor(
                noBlurColor = BottomAppBarDefaults.containerColor,
                blurAlpha = GlassDefaults.TransparentAlpha
            )
        }
        val finalColor = baseColor.copy(alpha = (baseColor.alpha * opacity).coerceIn(0f, 1f))

        NavigationBar(
            modifier = modifier.then(hazeModifier),
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
    iconVector: ImageVector,
    m3Icon: @Composable () -> Unit,
    m3IndicatorColor: Color,
    m3ShowLabel: Boolean,
    m3AlwaysShowLabel: Boolean,
    useCustomIcon: Boolean = false,
) {
    val isMiuix = ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)
    val useCustomIconBox = useCustomIcon && ThemeConfig.useFloatingBottomBar

    if (useCustomIconBox) {
        Box(
            modifier = modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 32.dp),
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(m3IndicatorColor)
                )
            }
            m3Icon()
        }
    } else if (isMiuix) {
        MiuixNavigationBarItem(
            selected = selected,
            onClick = onClick,
            icon = iconVector,
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
