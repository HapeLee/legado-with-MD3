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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.ui.widget.components.GlassTopAppBarDefaults

enum class TopBarButtonVariant {
    Filled, Outlined, Icon
}

@Composable
fun TopBarButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageVector: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    contentDescription: String? = null,
    style: TopBarButtonVariant = TopBarButtonVariant.Filled
) {

    val commonModifier = modifier
        .size(36.dp)

    when (style) {
        TopBarButtonVariant.Filled -> {
            FilledTonalIconButton(
                onClick = onClick,
                modifier = commonModifier,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = GlassTopAppBarDefaults.controlContainerColor(),
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
fun TopbarNavigationButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageVector: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    contentDescription: String? = "返回",
    style: TopBarButtonVariant = TopBarButtonVariant.Filled
) {
    TopBarButton(
        onClick = onClick,
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier.padding(start = 12.dp, end = 4.dp),
        style = style
    )
}

@Composable
fun TopBarActionButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val enableProgressive = ThemeConfig.enableProgressiveBlur

    if (enableProgressive) {
        TopBarButton(
            onClick = onClick,
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = modifier.padding(end = 12.dp),
            style = TopBarButtonVariant.Filled
        )
    } else {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription
            )
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