package io.legado.app.ui.book.read.sheet

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.model.ReadBook
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.settingItem.TinyDropdownSettingItem

@Composable
fun PageAnimConfigSheet(
    onDismissRequest: () -> Unit,
    onAnimChanged: () -> Unit,
) {
    val items = listOf(
        R.string.btn_default_s,
        R.string.page_anim_cover,
        R.string.page_anim_slide,
        R.string.page_anim_simulation,
        R.string.page_anim_scroll,
        R.string.page_anim_none,
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = LegadoTheme.colorScheme.surfaceContainer,
        title = { Text(stringResource(R.string.page_anim)) },
        text = {
            TinyDropdownSettingItem(
                title = stringResource(R.string.page_anim),
                selectedValue = ReadBook.book?.getPageAnim()?.toString() ?: "-1",
                displayEntries = items.map { stringResource(it) }.toTypedArray(),
                entryValues = items.indices.map { (it - 1).toString() }.toTypedArray(),
                onValueChange = {
                    ReadBook.book?.setPageAnim(it.toInt())
                    onAnimChanged()
                    onDismissRequest()
                },
            )
        },
        confirmButton = {},
    )
}
