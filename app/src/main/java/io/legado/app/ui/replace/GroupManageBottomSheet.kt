package io.legado.app.ui.replace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.components.modalBottomSheet.GlobalModalBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupManageBottomSheet(
    groups: List<String>,
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    viewModel: ReplaceRuleViewModel
) {
    var editingGroup by remember { mutableStateOf<String?>(null) }
    var updatedGroupName by remember { mutableStateOf("") }

    GlobalModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                modifier = Modifier.padding(bottom = 16.dp),
                text = stringResource(R.string.group_manage),
                style = MaterialTheme.typography.titleMedium
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(groups) { group ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        if (editingGroup == group) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                OutlinedTextField(
                                    value = updatedGroupName,
                                    onValueChange = { updatedGroupName = it },
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = {
                                    viewModel.upGroup(group, updatedGroupName)
                                    editingGroup = null
                                }) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = stringResource(id = R.string.ok)
                                    )
                                }
                            }
                        } else {
                            ListItem(
                                headlineContent = { Text(group) },
                                trailingContent = {
                                    Row {
                                        IconButton(onClick = {
                                            editingGroup = group
                                            updatedGroupName = group
                                        }) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = stringResource(id = R.string.edit)
                                            )
                                        }
                                        IconButton(onClick = { viewModel.delGroup(group) }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = stringResource(id = R.string.delete)
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}