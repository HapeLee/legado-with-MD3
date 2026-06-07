package io.legado.app.ui.book.read.sheet

import androidx.compose.runtime.Composable
import io.legado.app.ui.book.read.ReadBookButtonConfigItem
import io.legado.app.ui.book.read.ReadBookIntent

@Composable
fun ToolButtonConfigSheet(
    show: Boolean,
    items: List<ReadBookButtonConfigItem>,
    customIcons: Map<String, String>,
    onDismissRequest: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
) {
    BottomBarIconSheet(
        show = show,
        items = items,
        customIcons = customIcons,
        onDismissRequest = onDismissRequest,
        onIntent = onIntent,
    )
}
