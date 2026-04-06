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
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.text.AppText

/**
 * 基础版：由 Boolean 控制显示/隐藏
 */
@Composable
fun TextListInputDialog(
    show: Boolean,
    title: String,
    hint: String,
    initialValue: String = "",
    suggestions: List<String> = emptyList(),
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(show) { mutableStateOf(initialValue) }

    AppAlertDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title,
        content = {
            Column {
                AppTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = hint,
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
        confirmText = stringResource(android.R.string.ok),
        onConfirm = { onConfirm(text) },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = onDismissRequest
    )
}

@Composable
fun <T> TextListInputDialog(
    data: T?,
    title: String,
    hint: String,
    initialValue: (T) -> String,
    suggestions: List<String> = emptyList(),
    onDismissRequest: () -> Unit,
    onConfirm: (T, String) -> Unit
) {
    var cachedData by remember { mutableStateOf(data) }
    if (data != null) cachedData = data

    val currentData = cachedData
    if (currentData != null) {
        TextListInputDialog(
            show = data != null,
            title = title,
            hint = hint,
            initialValue = initialValue(currentData),
            suggestions = suggestions,
            onDismissRequest = onDismissRequest,
            onConfirm = { text -> onConfirm(currentData, text) }
        )
    }
}