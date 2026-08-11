package io.legado.app.ui.widget.components.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppSlider

@Composable
fun ReaderMenuDismissLayer(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss,
                )
        )
    }
}

@Composable
fun BoxScope.ReaderMenuAnimatedTop(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter),
        content = { content() },
    )
}

@Composable
fun BoxScope.ReaderMenuAnimatedBottom(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter),
        content = { content() },
    )
}

@Composable
fun DefaultReaderMenuTopSurface(content: @Composable () -> Unit) {
    Surface(
        color = LegadoTheme.colorScheme.surfaceContainerHigh,
        content = content,
    )
}

@Composable
fun DefaultReaderMenuBottomSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        shape = RoundedCornerShape(32.dp),
        color = LegadoTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            LegadoTheme.colorScheme.outlineVariant,
        ),
        content = content,
    )
}

@Stable
data class ReaderMenuAction(
    val icon: ImageVector,
    val description: String,
    val onLongClick: (() -> Unit)? = null,
    val onClick: () -> Unit,
)

@Composable
fun ReaderMenuToolRow(
    actions: List<ReaderMenuAction>,
    modifier: Modifier = Modifier,
    columns: Int = 5,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        actions.take(columns).forEach { action ->
            ReaderMenuIconButton(
                icon = action.icon,
                description = action.description,
                onClick = action.onClick,
                onLongClick = action.onLongClick,
            )
        }
        repeat((columns - actions.size).coerceAtLeast(0)) {
            Spacer(Modifier.size(48.dp))
        }
    }
}

@Composable
fun ReaderMenuIconButton(
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    if (onLongClick == null) {
        IconButton(onClick = onClick, modifier = modifier.size(48.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = LegadoTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }
    } else {
        Box(
            modifier = modifier
                .size(48.dp)
                .clip(CircleShape)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    role = Role.Button,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = LegadoTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
fun ReaderMenuSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    accessibilityLabel: String? = null,
    accessibilityValue: String? = null,
) {
    AppSlider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.padding(horizontal = 5.dp),
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        accessibilityLabel = accessibilityLabel,
        accessibilityValue = accessibilityValue,
    )
}
