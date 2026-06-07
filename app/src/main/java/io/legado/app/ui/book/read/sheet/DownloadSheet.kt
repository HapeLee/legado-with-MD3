package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.model.ReadBook
import io.legado.app.ui.theme.LegadoTheme

@Composable
fun DownloadSheet(
    onDismissRequest: () -> Unit,
    onDownload: (start: Int, end: Int) -> Unit,
) {
    val book = ReadBook.book
    var startChapter by remember { mutableStateOf(((book?.durChapterIndex ?: 0) + 1).toString()) }
    var endChapter by remember { mutableStateOf((book?.totalChapterNum ?: 0).toString()) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = LegadoTheme.colorScheme.surfaceContainer,
        title = { Text(stringResource(R.string.offline_cache)) },
        text = {
            androidx.compose.foundation.layout.Column {
                OutlinedTextField(
                    value = startChapter,
                    onValueChange = { startChapter = it },
                    label = { Text(stringResource(R.string.start_chapter)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = endChapter,
                    onValueChange = { endChapter = it },
                    label = { Text(stringResource(R.string.end_chapter)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val start = startChapter.toIntOrNull() ?: return@TextButton
                    val end = endChapter.toIntOrNull() ?: return@TextButton
                    if (start <= end) {
                        onDownload(start, end)
                        onDismissRequest()
                    }
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
