package io.legado.app.ui.widget.components.card

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.AnimatedTextLine
import io.legado.app.ui.widget.components.AppIcon
import top.yukonga.miuix.kmp.theme.MiuixTheme


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TextCard(
    modifier: Modifier = Modifier,
    text: String,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    backgroundColor: Color? = null,
    contentColor: Color? = null,
    cornerRadius: Dp = 8.dp,
    horizontalPadding: Dp = 8.dp,
    verticalPadding: Dp = 2.dp,
    iconSize: Dp = 14.dp,
    spacing: Dp = 4.dp,
    textStyle: TextStyle = LegadoTheme.typography.labelSmallEmphasized
) {
    val isMiuix = ThemeResolver.isMiuixEngine(composeEngine)

    val defaultBackground = if (isMiuix)
        MiuixTheme.colorScheme.surfaceContainer
    else
        MaterialTheme.colorScheme.primaryContainer

    val defaultContent = if (isMiuix)
        MiuixTheme.colorScheme.onSurface
    else
        MaterialTheme.colorScheme.primary

    val finalBackgroundColor = backgroundColor ?: defaultBackground
    val finalContentColor = contentColor ?: defaultContent

    NormalCard(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        containerColor = finalBackgroundColor,
        contentColor = finalContentColor,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = horizontalPadding,
                vertical = verticalPadding
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            if (icon != null) {
                AppIcon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = finalContentColor,
                    modifier = Modifier.size(iconSize)
                )
                Spacer(modifier = Modifier.width(spacing))
            }

            AnimatedTextLine(
                text = text,
                style = textStyle,
                color = finalContentColor
            )
        }
    }
}

