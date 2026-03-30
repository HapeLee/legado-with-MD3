package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.SmallTextButton
import io.legado.app.ui.widget.components.modalBottomSheet.GlassModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.SettingItem
import io.legado.app.ui.widget.components.text.AppText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupManageBottomSheet(
    groups: List<String>,
    onDismissRequest: () -> Unit,
    onUpdateGroup: (oldGroup: String, newGroup: String) -> Unit,
    onDeleteGroup: (group: String) -> Unit
) {
    GlassModalBottomSheet(
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppText(
                modifier = Modifier.padding(bottom = 16.dp),
                text = stringResource(R.string.group_manage),
                style = LegadoTheme.typography.titleMedium
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(groups, key = { it }) { group ->
                    GroupItem(
                        group = group,
                        onUpdateGroup = onUpdateGroup,
                        onDeleteGroup = onDeleteGroup
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupItem(
    group: String,
    onUpdateGroup: (oldGroup: String, newGroup: String) -> Unit,
    onDeleteGroup: (group: String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val state = rememberTextFieldState(initialText = group)

    LaunchedEffect(expanded) {
        if (expanded) {
            state.edit {
                replace(0, length, group)
            }
        }
    }

    SettingItem(
        title = group,
        expanded = expanded,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        onExpandChange = { expanded = it },
        trailingContent = {
            Row {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(id = R.string.edit)
                    )
                }
                IconButton(onClick = { onDeleteGroup(group) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(id = R.string.delete)
                    )
                }
            }
        },
        expandContent = {
            TextField(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                label = { AppText(stringResource(R.string.edit)) },
                contentPadding = PaddingValues(
                    top = 4.dp,
                    bottom = 4.dp,
                    start = 12.dp,
                    end = 12.dp
                ),
                onKeyboardAction = {
                    onUpdateGroup(group, state.text.toString())
                    expanded = false
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                SmallTextButton(
                    text = stringResource(id = R.string.ok),
                    icon = Icons.Default.Check,
                    onClick = {
                        onUpdateGroup(group, state.text.toString())
                        expanded = false
                    }
                )
            }
        }
    )
}
