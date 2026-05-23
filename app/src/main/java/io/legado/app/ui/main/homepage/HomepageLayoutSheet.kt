package io.legado.app.ui.main.homepage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem

@Composable
fun HomepageLayoutSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    layoutMode: Int,
    onLayoutModeChange: (Int) -> Unit,
) {
    HomepageLayoutSheet(
        data = if (show) Unit else null,
        onDismissRequest = onDismissRequest,
        layoutMode = layoutMode,
        onLayoutModeChange = onLayoutModeChange,
    )
}

@Composable
fun <T> HomepageLayoutSheet(
    data: T?,
    onDismissRequest: () -> Unit,
    layoutMode: Int,
    onLayoutModeChange: (Int) -> Unit,
) {
    AppModalBottomSheet(
        data = data,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.homepage_layout_settings),
    ) {
        Column {
            DropdownListSettingItem(
                title = stringResource(R.string.homepage_layout_mode),
                selectedValue = layoutMode.toString(),
                displayEntries = arrayOf(
                    stringResource(R.string.homepage_layout_mixed),
                    stringResource(R.string.homepage_layout_tabs)
                ),
                entryValues = arrayOf("0", "1"),
                onValueChange = { onLayoutModeChange(it.toInt()) }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
