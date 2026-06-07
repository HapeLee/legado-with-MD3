package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.model.ReadBook
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.settingItem.TinyDropdownSettingItem

@Composable
fun CharsetConfigSheet(
    onDismissRequest: () -> Unit,
) {
    var charset by remember { mutableStateOf(ReadBook.book?.charset ?: "UTF-8") }
    val charsetEntries = remember { AppConst.charsets.toTypedArray() }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = LegadoTheme.colorScheme.surfaceContainer,
        title = { Text(stringResource(R.string.set_charset)) },
        text = {
            Column {
                OutlinedTextField(
                    value = charset,
                    onValueChange = { charset = it },
                    label = { Text(stringResource(R.string.set_charset)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                TinyDropdownSettingItem(
                    title = stringResource(R.string.set_charset),
                    selectedValue = charset,
                    displayEntries = charsetEntries,
                    entryValues = charsetEntries,
                    onValueChange = { charset = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    ReadBook.setCharset(charset)
                    onDismissRequest()
                },
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
