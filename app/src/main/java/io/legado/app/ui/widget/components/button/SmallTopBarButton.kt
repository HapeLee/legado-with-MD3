package io.legado.app.ui.widget.components.button

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.ThemeState

enum class TopBarButtonVariant {
    Filled, Outlined, Icon
}

@Composable
fun SmallTopBarButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageVector: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    contentDescription: String? = null,
    style: TopBarButtonVariant = TopBarButtonVariant.Filled
) {
    val themeOpacity by ThemeState.containerOpacity.collectAsState()
    val baseColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val glassColor = baseColor.copy(alpha = (themeOpacity / 100f).coerceAtLeast(0.6f))

    val commonModifier = modifier
        .padding(horizontal = 12.dp)
        .size(36.dp)

    when (style) {
        TopBarButtonVariant.Filled -> {
            FilledTonalIconButton(
                onClick = onClick,
                modifier = commonModifier,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = glassColor,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                SmallIcon(imageVector, contentDescription)
            }
        }

        TopBarButtonVariant.Outlined -> {
            OutlinedIconButton(
                onClick = onClick,
                modifier = commonModifier,
                border = ButtonDefaults.outlinedButtonBorder()
            ) {
                SmallIcon(imageVector, contentDescription)
            }
        }

        TopBarButtonVariant.Icon -> {
            IconButton(
                onClick = onClick,
                modifier = commonModifier
            ) {
                SmallIcon(imageVector, contentDescription)
            }
        }
    }
}

@Composable
private fun SmallIcon(imageVector: ImageVector, contentDescription: String?) {
    AnimatedContent(
        targetState = imageVector,
        transitionSpec = {
            (fadeIn() + scaleIn(initialScale = 0.8f))
                .togetherWith(fadeOut())
        },
        label = "IconTransition"
    ) { targetIcon ->
        Icon(
            imageVector = targetIcon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp)
        )
    }
}