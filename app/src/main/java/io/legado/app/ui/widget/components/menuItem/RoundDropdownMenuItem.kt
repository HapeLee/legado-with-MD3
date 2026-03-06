package io.legado.app.ui.widget.components.menuItem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.rememberOpaqueColorScheme

@Composable
fun RoundDropdownMenuItem(
    modifier: Modifier = Modifier,
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val colorScheme = rememberOpaqueColorScheme()

    DropdownMenuItem(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .clip(MaterialTheme.shapes.small)
            .background(colorScheme.surface),
        text = text,
        trailingIcon = trailingIcon,
        onClick = onClick
    )
}