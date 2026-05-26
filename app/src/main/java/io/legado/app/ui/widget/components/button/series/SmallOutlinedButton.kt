package io.legado.app.ui.widget.components.button.series

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SmallOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    text: String? = null,
    contentDescription: String? = null
) {
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        if (icon != null && text == null) {
            MiuixIconButton(
                onClick = onClick,
                modifier = modifier.size(SmallMiuixButtonSize),
                enabled = enabled,
                backgroundColor = LegadoTheme.colorScheme.surfaceContainer
            ) {
                MiuixIcon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(SmallMiuixIconSize)
                )
            }
        } else {
            Card(
                onClick = onClick,
                modifier = modifier,
                showIndication = true,
                colors = CardDefaults.defaultColors(
                    color = LegadoTheme.colorScheme.surfaceContainer,
                    contentColor = LegadoTheme.colorScheme.onSurfaceVariant
                )
            ) {
                SmallButtonContent(icon, text, contentDescription)
            }
        }
    } else {
        SmallNoMinTouchTarget {
            when {
                icon != null && text == null -> {
                    OutlinedIconButton(
                        onClick = onClick,
                        modifier = modifier.size(smallContainerSize()),
                        enabled = enabled,
                        shapes = IconButtonDefaults.shapes(),
                        border = ButtonDefaults.outlinedButtonBorder()
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = contentDescription,
                            modifier = Modifier.size(smallIconSize)
                        )
                    }
                }

                else -> {
                    OutlinedButton(
                        onClick = onClick,
                        modifier = modifier,
                        enabled = enabled,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        border = ButtonDefaults.outlinedButtonBorder()
                    ) {
                        SmallButtonContent(icon, text, contentDescription)
                    }
                }
            }
        }
    }
}
