package io.legado.app.ui.widget.components.button.series

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.text.AppText
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText

internal val SmallMiuixButtonSize = 32.dp
internal val SmallMiuixIconSize = 18.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun smallContainerSize() = IconButtonDefaults.extraSmallContainerSize(
    IconButtonDefaults.IconButtonWidthOption.Uniform
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal val smallIconSize: Dp
    get() = IconButtonDefaults.extraSmallIconSize

@Composable
internal fun SmallNoMinTouchTarget(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        content()
    }
}

@Composable
internal fun SmallButtonContent(
    icon: ImageVector?,
    text: String?,
    contentDescription: String?
) {
    val isMiuix = ThemeResolver.isMiuixEngine(composeEngine)
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            if (isMiuix) {
                MiuixIcon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        if (text != null) {
            if (isMiuix) {
                MiuixText(
                    text = text,
                    style = LegadoTheme.typography.labelMedium
                )
            } else {
                AppText(
                    text = text,
                    style = LegadoTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
fun SmallPlainButton(
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
                modifier = modifier,
                enabled = enabled
            ) {
                MiuixIcon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(SmallMiuixIconSize),
                )
            }
        } else {
            Card(
                onClick = onClick,
                modifier = modifier,
                showIndication = true,
                colors = CardDefaults.defaultColors(
                    color = LegadoTheme.colorScheme.surfaceVariant,
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
                    IconButton(
                        onClick = onClick,
                        modifier = modifier.size(smallContainerSize()),
                        enabled = enabled,
                        shape = IconButtonDefaults.extraSmallRoundShape,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = contentDescription,
                            modifier = Modifier.size(smallIconSize),
                        )
                    }
                }

                else -> {
                    TextButton(
                        onClick = onClick,
                        modifier = modifier,
                        enabled = enabled,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = MaterialTheme.shapes.small
                    ) {
                        SmallButtonContent(icon, text, contentDescription)
                    }
                }
            }
        }
    }
}
