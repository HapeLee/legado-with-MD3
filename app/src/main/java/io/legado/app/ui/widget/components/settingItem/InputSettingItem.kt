package io.legado.app.ui.widget.components.settingItem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.components.button.SmallTextButton

@Composable
fun InputSettingItem(
    title: String,
    value: String,
    defaultValue: String? = "",
    description: String? = null,
    onConfirm: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val state = rememberTextFieldState(initialText = value)

    LaunchedEffect(expanded) {
        if (expanded) {
            state.edit {
                replace(0, length, value)
            }
        }
    }

    SettingItem(
        title = title,
        description = description,
        option = value,
        expanded = expanded,
        onExpandChange = { expanded = it },
        expandContent = {
            TextField(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                label = { Text(stringResource(R.string.edit)) },
                contentPadding = PaddingValues(
                    top = 4.dp,
                    bottom = 4.dp,
                    start = 12.dp,
                    end = 12.dp
                ),
                onKeyboardAction = {
                    onConfirm(state.text.toString())
                    expanded = false
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                SmallTextButton(
                    text = "默认",
                    icon = Icons.Default.Replay,
                    onClick = {
                        state.edit { replace(0, length, defaultValue.toString()) }
                    }
                )
                SmallTextButton(
                    text = "确认",
                    icon = Icons.Default.Check,
                    onClick = {
                        onConfirm(state.text.toString())
                        expanded = false
                    }
                )
            }
        }
    )
}