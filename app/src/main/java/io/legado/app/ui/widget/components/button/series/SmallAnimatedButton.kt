package io.legado.app.ui.widget.components.button.series

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SmallAnimatedButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    iconChecked: ImageVector? = null,
    text: String? = null,
    contentColor: Color = LegadoTheme.colorScheme.onSurfaceVariant,
    containerColor: Color? = null,
    selectedContainerColor: Color = LegadoTheme.colorScheme.primaryContainer,
    selectedContentColor: Color = LegadoTheme.colorScheme.onPrimaryContainer,
    contentDescription: String? = null
) {
    var showText by remember { mutableStateOf(false) }
    val currentIcon = if (checked) (iconChecked ?: icon)!! else icon!!

    LaunchedEffect(showText) {
        if (showText) {
            delay(1000.milliseconds)
            showText = false
        }
    }

    SmallNoMinTouchTarget {
        SeriesButton(
            onClick = {
                onCheckedChange(!checked)
                showText = true
            },
            modifier = if (text == null) modifier else modifier.height(36.dp),
            enabled = enabled,
            selected = checked,
            onLongClick = onLongClick,
            size = if (text == null) smallContainerSize() else null,
            style = SeriesIconButtonStyle.Tonal,
            contentColor = contentColor,
            containerColor = containerColor,
            selectedContainerColor = selectedContainerColor,
            selectedContentColor = selectedContentColor
        ) { contentColor ->
            SeriesAnimatedButtonContent(
                icon = currentIcon,
                text = text,
                contentDescription = if (text == null) contentDescription else null,
                showText = showText,
                iconSize = smallIconSize,
                textStyle = LegadoTheme.typography.labelSmall,
                contentColor = contentColor,
                padding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                spacing = 6.dp
            )
        }
    }
}
