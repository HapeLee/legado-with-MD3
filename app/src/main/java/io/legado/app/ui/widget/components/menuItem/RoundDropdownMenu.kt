package io.legado.app.ui.widget.components.menuItem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun RoundDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    shadowElevation: Dp = 4.dp,
    verticalSpacing: Dp = 8.dp,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = shape,
        shadowElevation = shadowElevation
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(verticalSpacing)
        ) {
            content(onDismissRequest)
        }
    }
}
