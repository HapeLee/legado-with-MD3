package io.legado.app.ui.widget.components.button

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.ui.widget.components.GlassTopAppBarDefaults
import kotlinx.coroutines.delay

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
                AnimatedIcon(
                    modifier = Modifier.size(20.dp),
                    imageVector = imageVector,
                    contentDescription = contentDescription
                )
            }
        }

        TopBarButtonVariant.Outlined -> {
            OutlinedIconButton(
                onClick = onClick,
                modifier = commonModifier,
                border = ButtonDefaults.outlinedButtonBorder()
            ) {
                AnimatedIcon(
                    modifier = Modifier.size(20.dp),
                    imageVector = imageVector,
                    contentDescription = contentDescription
                )
            }
        }

        TopBarButtonVariant.Icon -> {
            IconButton(
                onClick = onClick,
                modifier = commonModifier
            ) {
                AnimatedIcon(
                    modifier = Modifier.size(20.dp),
                    imageVector = imageVector,
                    contentDescription = contentDescription
                )
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
        modifier = modifier.padding(horizontal = 12.dp),
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TopBarAnimatedActionButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconChecked: ImageVector,
    iconUnchecked: ImageVector,
    activeText: String,
    inactiveText: String,
    modifier: Modifier = Modifier
) {
    var showText by remember { mutableStateOf(false) }
    var lastCheckedState by remember { mutableStateOf(checked) }

    LaunchedEffect(showText) {
        if (showText) {
            delay(1000)
            showText = false
        }
    }

    ToggleButton(
        modifier = modifier.height(36.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        checked = checked,
        onCheckedChange = {
            lastCheckedState = it
            onCheckedChange(it)
            showText = true
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {

            AnimatedContent(
                targetState = checked,
                label = "IconAnimation"
            ) { targetChecked ->
                AnimatedIcon(
                    modifier = Modifier.size(20.dp),
                    imageVector = if (targetChecked) iconChecked else iconUnchecked,
                    contentDescription = null
                )
            }

            AnimatedVisibility(
                visible = showText
            ) {
                Text(
                    text = if (lastCheckedState) activeText else inactiveText,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 8.dp),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun AnimatedIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
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
            modifier = modifier
        )
    }
}