package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.TopAppBar
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
    if (ThemeConfig.useFlexibleTopAppBar) {
        MediumFlexibleTopAppBar(
            modifier = modifier,
            title = title,
            subtitle = subtitle,
            navigationIcon = navigationIcon,
            actions = actions,
            scrollBehavior = scrollBehavior,
            colors = colors
        )
    } else {
        TopAppBar(
            modifier = modifier,
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            scrollBehavior = scrollBehavior,
            colors = colors
        )
    }
}

object GlassTopAppBarDefaults {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun defaultScrollBehavior(): TopAppBarScrollBehavior {
        return if (ThemeConfig.useFlexibleTopAppBar) {
            TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        } else {
            TopAppBarDefaults.pinnedScrollBehavior()
        }
    }

    @Composable
    fun glassColors(): TopAppBarColors {
        val opacity = ThemeConfig.containerOpacity / 100f

        val containerColor = GlassDefaults.glassColor(
            noBlurColor = MaterialTheme.colorScheme.surface.copy(alpha = opacity),
            blurAlpha = GlassDefaults.TransparentAlpha
        )

        val scrolledContainerColor = if (ThemeConfig.enableBlur) {
            MaterialTheme.colorScheme.surface.copy(alpha = GlassDefaults.TransparentAlpha)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }

        return TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = scrolledContainerColor
        )
    }

    @Composable
    fun containerColor(): Color {
        val opacity = ThemeConfig.containerOpacity / 100f
        return GlassDefaults.glassColor(
            noBlurColor = MaterialTheme.colorScheme.surface.copy(alpha = opacity),
            blurAlpha = GlassDefaults.TransparentAlpha
        )
    }

    @Composable
    fun controlContainerColor(): Color {
        return GlassDefaults.glassColor(
            noBlurColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            blurAlpha = GlassDefaults.DefaultBlurAlpha
        )
    }
}
