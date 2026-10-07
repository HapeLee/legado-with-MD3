package io.legado.app.ui.book.read.sheet

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.constant.PageAnim
import io.legado.app.model.ReadBook
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ProvideAppDensity
import io.legado.app.ui.widget.components.settingItem.TinyDropdownSettingItem

@Composable
fun PageAnimConfigSheet(
    onDismissRequest: () -> Unit,
    onAnimChanged: () -> Unit,
) {
    // 每项自带 PageAnim 取值，不按「下标 - 1」推导：列表少一项就会整体错位一格
    // （最后一项写着「无动画」，落库的却是别的值）。
    val items = listOf(
        R.string.btn_default_s to -1,
        R.string.page_anim_cover to PageAnim.coverPageAnim,
        R.string.page_anim_slide to PageAnim.slidePageAnim,
        R.string.page_anim_simulation to PageAnim.simulationPageAnim,
        R.string.page_anim_scroll to PageAnim.scrollPageAnim,
        R.string.page_anim_fade to PageAnim.fadePageAnim,
        R.string.page_anim_none to PageAnim.noAnim,
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = LegadoTheme.colorScheme.surfaceContainer,
        title = { ProvideAppDensity { Text(stringResource(R.string.page_anim)) } },
        text = {
            ProvideAppDensity {
                TinyDropdownSettingItem(
                    title = stringResource(R.string.page_anim),
                    selectedValue = ReadBook.book?.getPageAnim()?.toString() ?: "-1",
                    displayEntries = items.map { stringResource(it.first) }.toTypedArray(),
                    entryValues = items.map { it.second.toString() }.toTypedArray(),
                    onValueChange = {
                        ReadBook.book?.setPageAnim(it.toInt())
                        onAnimChanged()
                        onDismissRequest()
                    },
                )
            }
        },
        confirmButton = {},
    )
}
