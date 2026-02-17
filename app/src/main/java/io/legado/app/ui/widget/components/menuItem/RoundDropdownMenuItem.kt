package io.legado.app.ui.widget.components.menuItem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun RoundDropdownMenuItem(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    DropdownMenuItem(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface),
        text = { Text(text) },
        trailingIcon = trailingIcon,
        onClick = onClick,
    )
}