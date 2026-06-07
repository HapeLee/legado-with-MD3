package io.legado.app.ui.widget.components.button.series

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import kotlinx.coroutines.delay

@Composable
fun MediumAnimatedButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    iconChecked: ImageVector? = null,
    text: String? = null,
    contentDescription: String? = null
) {
    var showText by remember { mutableStateOf(false) }
    val currentIcon = if (checked) (iconChecked ?: icon)!! else icon!!

    LaunchedEffect(showText) {
        if (showText) {
            delay(1000)
            showText = false
        }
    }

    SeriesButton(
        onClick = {
            onCheckedChange(!checked)
            showText = true
        },
        modifier = if (text == null) modifier else modifier.height(36.dp),
        enabled = enabled,
        selected = checked,
        onLongClick = onLongClick,
        size = if (text == null) MediumSeriesIconButtonSize else null,
        style = SeriesIconButtonStyle.Tonal
    ) { contentColor ->
        SeriesAnimatedButtonContent(
            icon = currentIcon,
            text = text,
            contentDescription = if (text == null) contentDescription else null,
            showText = showText,
            iconSize = MediumSeriesIconSize,
            textStyle = LegadoTheme.typography.labelMedium,
            contentColor = contentColor,
            padding = PaddingValues(horizontal = 8.dp),
            spacing = 8.dp
        )
    }
}
