package io.legado.app.ui.widget.components.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.text.AppText

@Composable
fun TextListInputDialog(
    title: String,
    hint: String,
    initialValue: String = "",
    suggestions: List<String> = emptyList(),
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { AppText(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { AppText(hint) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (suggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AppText(
                        text = "建议:",
                        style = LegadoTheme.typography.labelSmall,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(suggestions) { suggestion ->
                            AssistChip(
                                onClick = { text = suggestion },
                                label = { AppText(suggestion) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) }
            ) {
                AppText(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                AppText(stringResource(android.R.string.cancel))
            }
        }
    )
}