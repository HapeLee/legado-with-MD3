package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.legado.app.ui.config.themeConfig.ThemeConfig

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GlassMediumFlexibleTopAppBar(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    subtitle: (@Composable () -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = GlassTopAppBarDefaults.glassColors()
) {
    MediumFlexibleTopAppBar(
        modifier = modifier,
        title = title,
        subtitle = subtitle,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = colors
    )
}

object GlassTopAppBarDefaults {

    @Composable
    fun glassColors(): TopAppBarColors {
        val alpha = ThemeConfig.containerOpacity / 100f
        val enableBlur = ThemeConfig.enableBlur

        val containerColor = if (enableBlur) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = alpha)
        }

        val scrolledContainerColor = if (enableBlur) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }

        return TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = scrolledContainerColor
        )
    }

    @Composable
    fun controlContainerColor(): Color {
        val enableBlur = ThemeConfig.enableBlur
        val baseColor = MaterialTheme.colorScheme.surfaceContainerHigh

        return if (enableBlur) {
            val alpha = 0.6f
            baseColor.copy(alpha = alpha)
        } else {
            baseColor.copy(alpha = ThemeConfig.containerOpacity / 100f)
        }
    }

}