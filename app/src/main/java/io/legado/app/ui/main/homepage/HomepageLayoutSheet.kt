package io.legado.app.ui.main.homepage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
        title = "布局设置",
    ) {
        Column {
            DropdownListSettingItem(
                title = "首页布局模式",
                selectedValue = layoutMode.toString(),
                displayEntries = arrayOf("混合列表", "分源Tab"),
                entryValues = arrayOf("0", "1"),
                onValueChange = { onLayoutModeChange(it.toInt()) }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
