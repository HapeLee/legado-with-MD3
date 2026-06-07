package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.ReadBookUiState
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.utils.sendToClip

@Composable
fun ContentEditSheet(
    show: Boolean,
    state: ReadBookUiState,
    onIntent: (ReadBookIntent) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current

    LaunchedEffect(show) {
        if (!show) return@LaunchedEffect
        onIntent(ReadBookIntent.LoadContentEdit)
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = {
            if (state.contentEditTitle.isNotEmpty()) {
                onIntent(ReadBookIntent.SaveContentEdit(state.contentEditText, state.contentEditSaveToSource))
            }
            onDismissRequest()
        },
        title = state.contentEditTitle,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = {
                    onIntent(ReadBookIntent.SaveContentEdit(state.contentEditText, state.contentEditSaveToSource))
                    onDismissRequest()
                }) {
                    Text(stringResource(R.string.action_save))
                }
                TextButton(onClick = {
                    onIntent(ReadBookIntent.ResetContentEdit)
                }) {
                    Text(stringResource(R.string.reset))
                }
                TextButton(onClick = {
                    context.sendToClip("${state.contentEditTitle}\n${state.contentEditText}")
                }) {
                    Text(stringResource(R.string.copy_all))
                }
            }

            Spacer(Modifier.height(8.dp))

            if (state.contentEditLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                OutlinedTextField(
                    value = state.contentEditText,
                    onValueChange = { onIntent(ReadBookIntent.SetContentEditText(it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .height(400.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
            }

            if (state.contentEditIsLocalTxt) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.contentEditSaveToSource,
                        onCheckedChange = { onIntent(ReadBookIntent.SetContentEditSaveToSource(it)) },
                    )
                    Text(
                        text = stringResource(R.string.save_to_source),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
