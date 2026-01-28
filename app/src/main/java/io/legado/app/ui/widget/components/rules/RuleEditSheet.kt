package io.legado.app.ui.widget.components.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.components.modalBottomSheet.GlassModalBottomSheet
import kotlinx.coroutines.launch

/**
 * 通用编辑数据包装，用于适配不同的规则实体
 */
data class RuleEditFields(
    val name: String = "",
    val rule1: String = "",
    val rule2: String = "",
    val extra: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> RuleEditSheet(
    rule: T?,
    title: String,
    label1: String,
    label2: String,
    onDismissRequest: () -> Unit,
    onSave: (T) -> Unit,
    onCopy: (T) -> Unit,
    onPaste: () -> T?,
    toFields: (T?) -> RuleEditFields,
    fromFields: (RuleEditFields, T?) -> T
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val initialFields = remember(rule) { toFields(rule) }
    var name by remember(initialFields) { mutableStateOf(initialFields.name) }
    var rule1 by remember(initialFields) { mutableStateOf(initialFields.rule1) }
    var rule2 by remember(initialFields) { mutableStateOf(initialFields.rule2) }

    var showMenu by remember { mutableStateOf(false) }

    fun getCurrentEntity() = fromFields(RuleEditFields(name, rule1, rule2), rule)

    GlassModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                CenterAlignedTopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onDismissRequest) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.cancel)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.copy_rule)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, null) },
                                onClick = {
                                    onCopy(getCurrentEntity())
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.paste_rule)) },
                                leadingIcon = { Icon(Icons.Default.ContentPaste, null) },
                                onClick = {
                                    scope.launch {
                                        onPaste()?.let { pasted ->
                                            val fields = toFields(pasted)
                                            name = fields.name
                                            rule1 = fields.rule1
                                            rule2 = fields.rule2
                                        }
                                    }
                                    showMenu = false
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )

                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.name)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = rule1,
                        onValueChange = { rule1 = it },
                        label = { Text(label1) }
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = rule2,
                        onValueChange = { rule2 = it },
                        label = { Text(label2) },
                        minLines = 3
                    )
                }
            }

            FloatingActionButton(
                onClick = { onSave(getCurrentEntity()) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.Save, contentDescription = "Save")
            }
        }
    }
}