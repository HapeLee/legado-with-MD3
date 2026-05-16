package io.legado.app.ui.widget.components.topbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalHazeState
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.responsiveHazeEffect
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val hazeState = LocalHazeState.current
    val composeEngine = LegadoTheme.composeEngine
    val isMiuix = ThemeResolver.isMiuixEngine(composeEngine)

    val containerColor = if (!isMiuix) {
        GlassTopAppBarDefaults.containerColor()
    } else {
        GlassTopAppBarDefaults.getMiuixAppBarColor()
    }

    val finalModifier = if (hazeState != null) {
        modifier
            .background(color = containerColor)
            .responsiveHazeEffect(state = hazeState)
    } else {
        modifier.background(color = containerColor)
    }

    Column(modifier = finalModifier) {
        if (isMiuix) {
            MiuixTopAppBar(
                title = title,
                navigationIcon = navigationIcon,
                actions = actions,
                color = Color.Transparent
            )
        } else {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = navigationIcon,
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    }
}
