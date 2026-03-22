package io.legado.app.ui.widget.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeState
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.ui.theme.responsiveHazeEffect
import io.legado.app.ui.theme.responsiveHazeSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    subtitle: (@Composable () -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = GlassTopAppBarDefaults.glassColors(),
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    contentColor: Color = contentColorFor(MaterialTheme.colorScheme.background),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val hasImageBg = ThemeConfig.hasImageBg(isDark)
    val hazeState = remember { HazeState() }

    val containerColor = if (hasImageBg) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.background
    }

    val finalTopBar = @Composable {
        Box(modifier = Modifier.responsiveHazeEffect(state = hazeState)) {
            if (title != null) {
                GlassMediumFlexibleTopAppBar(
                    title = title,
                    subtitle = subtitle,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = navigationIcon,
                    actions = actions,
                    colors = colors
                )
            } else {
                topBar()
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = finalTopBar,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        containerColor = containerColor,
        contentColor = contentColor,
        contentWindowInsets = contentWindowInsets
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .responsiveHazeSource(hazeState)
        ) {
            content(paddingValues)
        }
    }
}