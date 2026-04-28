package io.legado.app.ui.widget.components.button

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay

@Composable
internal fun AnimatedActionButtonCore(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconChecked: ImageVector,
    iconUnchecked: ImageVector,
    activeText: String,
    inactiveText: String,
    modifier: Modifier = Modifier,
    iconSize: Dp,
    textStyle: TextStyle,
    textStartPadding: Dp,
    contentColor: Color? = null,
    button: @Composable (
        modifier: Modifier,
        onToggle: (Boolean) -> Unit,
        content: @Composable RowScope.() -> Unit
    ) -> Unit,
    icon: @Composable (imageVector: ImageVector, modifier: Modifier, tint: Color?) -> Unit,
    text: @Composable (text: String, modifier: Modifier, style: TextStyle, color: Color?) -> Unit
) {
    var showText by remember { mutableStateOf(false) }
    var lastCheckedState by remember { mutableStateOf(checked) }

    LaunchedEffect(showText) {
        if (showText) {
            delay(1000)
            showText = false
        }
    }

    button(
        modifier,
        { nextChecked ->
            lastCheckedState = nextChecked
            onCheckedChange(nextChecked)
            showText = true
        }
    ) {
        icon(
            if (checked) iconChecked else iconUnchecked,
            Modifier.size(iconSize),
            contentColor
        )

        AnimatedVisibility(visible = showText) {
            text(
                if (lastCheckedState) activeText else inactiveText,
                Modifier.padding(start = textStartPadding),
                textStyle,
                contentColor
            )
        }
    }
}
