package io.legado.app.ui.widget.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import ui.feice.mochi.effect.elastic.mochiPressDeform
import ui.feice.mochi.shape.MochiRoundedRectangle

/**
 * A button that displays a color swatch. Clicking opens a color picker.
 *
 * @param color The current color value (ARGB int).
 * @param onClick Called when the button is clicked.
 * @param modifier Modifier for the button.
 * @param enabled Whether the button is enabled.
 */
@Composable
fun AccentColorButton(
    color: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val useMochi = LegadoTheme.composeEngine == ComposeEngine.Mochi
    val shape = if (useMochi) MochiRoundedRectangle(12.dp) else RoundedCornerShape(32.dp)
    val label = stringResource(R.string.select_color)
    Box(
        modifier = modifier
            .size(width = 36.dp, height = 36.dp)
            .then(if (useMochi && enabled) Modifier.mochiPressDeform() else Modifier)
            .clip(shape)
            .background(Color(color))
            .border(
                width = 1.dp,
                color = LegadoTheme.colorScheme.outlineVariant,
                shape = shape
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
    )
}
