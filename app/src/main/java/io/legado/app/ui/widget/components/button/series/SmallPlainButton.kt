package io.legado.app.ui.widget.components.button.series

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme

internal val SmallMiuixButtonSize = 32.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun smallContainerSize() = IconButtonDefaults.extraSmallContainerSize(
    IconButtonDefaults.IconButtonWidthOption.Uniform
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal val smallIconSize: Dp
    get() = IconButtonDefaults.extraSmallIconSize

internal val SmallMiuixIconSize: Dp
    get() = smallIconSize

@Composable
internal fun SmallNoMinTouchTarget(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        content()
    }
}

@Composable
fun SmallPlainButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    icon: ImageVector? = null,
    text: String? = null,
    contentDescription: String? = null
) {
    SmallNoMinTouchTarget {
        SeriesButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            selected = selected,
            onLongClick = onLongClick,
            size = if (text == null) smallContainerSize() else null,
            contentColor = LegadoTheme.colorScheme.onSurfaceVariant
        ) { contentColor ->
            SeriesButtonContent(
                icon = icon,
                text = text,
                contentDescription = contentDescription,
                iconSize = smallIconSize,
                textStyle = LegadoTheme.typography.labelMedium,
                contentColor = contentColor,
                padding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                spacing = 4.dp
            )
        }
    }
}
