package io.legado.app.ui.widget.components.button

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
                    IconButtonDefaults.IconButtonWidthOption.Narrow
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