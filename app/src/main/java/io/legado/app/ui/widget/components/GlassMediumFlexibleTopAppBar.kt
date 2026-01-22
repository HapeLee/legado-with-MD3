package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.legado.app.ui.theme.ThemeState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GlassMediumFlexibleTopAppBar(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    subtitle: (@Composable () -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    val opacityValue by ThemeState.containerOpacity.collectAsState()
    val enableBlur by ThemeState.enableBlur.collectAsState()

    val alpha = opacityValue / 100f

    val containerColor = if (enableBlur) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = alpha)
    }

    val scrolledContainerColor = if (enableBlur) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = alpha)
    }

    val appBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = containerColor,
        scrolledContainerColor = scrolledContainerColor
    )

    MediumFlexibleTopAppBar(
        modifier = modifier,
        title = title,
        subtitle = subtitle,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = appBarColors
    )

}