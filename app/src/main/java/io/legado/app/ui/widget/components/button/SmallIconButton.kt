package io.legado.app.ui.widget.components.button

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButtonShapes
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.OutlinedIconToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SmallIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String? = null
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(
                IconButtonDefaults.extraSmallContainerSize(
                    IconButtonDefaults.IconButtonWidthOption.Uniform
                )
            ),
            shape = IconButtonDefaults.extraSmallRoundShape,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(IconButtonDefaults.extraSmallIconSize),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SmallOutlinedIconToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val defaultShape = IconButtonDefaults.extraSmallRoundShape
    val pressedShape = IconButtonDefaults.extraSmallPressedShape
    val checkedShape = IconButtonDefaults.extraSmallSelectedRoundShape

    val toggleShapes = remember(defaultShape, checkedShape) {
        IconToggleButtonShapes(
            shape = defaultShape,
            pressedShape = pressedShape,
            checkedShape = checkedShape
        )
    }

    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        OutlinedIconToggleButton(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier.size(
                IconButtonDefaults.extraSmallContainerSize(
                    IconButtonDefaults.IconButtonWidthOption.Uniform
                )
            ),
            shapes = toggleShapes
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(IconButtonDefaults.extraSmallIconSize),
            )
        }
    }
}