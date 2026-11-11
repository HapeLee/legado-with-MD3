package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.model.ReadBook
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.menu.AppChoiceDropdown
import io.legado.app.ui.widget.components.menu.AppChoiceOption
import kotlinx.collections.immutable.toImmutableList

@Composable
fun CharsetConfigSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
) {
    var charset by remember { mutableStateOf(ReadBook.book?.charset ?: "UTF-8") }
    val options = remember {
        AppConst.charsets.map { entry -> AppChoiceOption(id = entry, label = entry) }
            .toImmutableList()
    }
    val title = stringResource(R.string.set_charset)

    AppAlertDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title,
        content = {
            AppTextField(
                value = charset,
                onValueChange = { charset = it },
                modifier = Modifier.fillMaxWidth(),
                label = title,
                singleLine = true,
                trailingIcon = {
                    // compact 形态让四套引擎各自渲染按钮与整组条目；trigger 只提供纯展示图标。
                    AppChoiceDropdown(
                        options = options,
                        selectedId = charset,
                        onSelect = { charset = it },
                        title = title,
                        compact = true,
                    ) {
                        AppIcon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = title,
                            modifier = this.modifier,
                        )
                    }
                },
            )
        },
        confirmText = stringResource(R.string.ok),
        onConfirm = {
            ReadBook.setCharset(charset)
            onDismissRequest()
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = onDismissRequest,
    )
}
