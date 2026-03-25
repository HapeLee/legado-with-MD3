package io.legado.app.ui.rss.subscription

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.R
import io.legado.app.data.entities.RuleSub
import io.legado.app.data.entities.RuleSubType
import io.legado.app.ui.association.ImportBookSourceDialog
import io.legado.app.ui.association.ImportReplaceRuleDialog
import io.legado.app.ui.association.ImportRssSourceDialog
import io.legado.app.ui.widget.components.EmptyMessageView
import io.legado.app.ui.widget.components.card.SelectionItemCard
import io.legado.app.ui.widget.components.checkBox.CheckboxGroupContainer
import io.legado.app.ui.widget.components.checkBox.CheckboxItem
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.rules.RuleListScaffold
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RuleSubScreen(
    onBackClick: () -> Unit,
    viewModel: RuleSubViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showEditDialog by remember { mutableStateOf<RuleSub?>(null) }

    RuleListScaffold(
        title = stringResource(R.string.rule_subscription),
        state = state,
        onBackClick = onBackClick,
        onSearchToggle = viewModel::onSearchToggle,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onClearSelection = viewModel::clearSelection,
        onSelectAll = viewModel::selectAll,
        onSelectInvert = viewModel::selectInvert,
        onDeleteSelected = { viewModel.deleteSelected() },
        snackbarHostState = snackbarHostState,
        selectionSecondaryActions = emptyList(),
        topBarActions = {},
        dropDownMenuContent = { dismiss ->
            RoundDropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.Sort, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(12.dp))
                        Text(stringResource(R.string.sort))
                    }
                },
                onClick = {
                    viewModel.resetOrder()
                    dismiss()
                }
            )
        },
        floatingActionButton = {
            TooltipBox(
                positionProvider =
                    TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text("Localized description") } },
                state = rememberTooltipState(),
            ) {
                FloatingActionButton(
                    modifier = Modifier
                        .animateFloatingActionButton(
                            visible = true,
                            alignment = Alignment.BottomEnd,
                        ),
                    onClick = {
                        showEditDialog = RuleSub(customOrder = state.items.size + 1)
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Rule")
                }
            }
        }
    ) { paddingValues ->
        if (state.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                EmptyMessageView(
                    message = stringResource(R.string.rule_sub_empty_msg),
                    isLoading = state.isLoading
                )
            }
        } else {
            val typeArray = stringArrayResource(R.array.rule_type)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = paddingValues,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.items, key = { it.id }) { ruleSub ->
                    val isSelected = state.selectedIds.contains(ruleSub.id)
                    SelectionItemCard(
                        title = ruleSub.name,
                        subtitle = "${typeArray.getOrElse(ruleSub.type) { "" }}\n${ruleSub.url}",
                        isSelected = isSelected,
                        inSelectionMode = state.selectedIds.isNotEmpty(),
                        onToggleSelection = {
                            if (state.selectedIds.isNotEmpty()) {
                                viewModel.toggleSelection(ruleSub)
                            } else {
                                when (ruleSub.type) {
                                    RuleSubType.BOOK_SOURCE -> (context as? AppCompatActivity)?.showDialogFragment(
                                        ImportBookSourceDialog(ruleSub.url)
                                    )

                                    RuleSubType.RSS_SOURCE -> (context as? AppCompatActivity)?.showDialogFragment(
                                        ImportRssSourceDialog(ruleSub.url)
                                    )

                                    RuleSubType.REPLACE_RULE -> (context as? AppCompatActivity)?.showDialogFragment(
                                        ImportReplaceRuleDialog(ruleSub.url)
                                    )

                                    RuleSubType.AUTO -> {
                                        val encodedUrl = java.net.URLEncoder.encode(ruleSub.url, "UTF-8")
                                        val intent = android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse("legado://import/importonline?src=$encodedUrl")
                                        )
                                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    }
                                }
                            }
                        },
                        trailingAction = {
                            IconButton(onClick = { showEditDialog = ruleSub }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                        },
                        dropdownContent = { dismiss ->
                            RoundDropdownMenuItem(
                                text = { Text(stringResource(R.string.delete)) },
                                onClick = {
                                    viewModel.delete(ruleSub)
                                    dismiss()
                                }
                            )
                        }
                    )
                }
            }
        }
    }

    showEditDialog?.let { ruleSub ->
        RuleSubEditDialog(
            ruleSub = ruleSub,
            onDismiss = { showEditDialog = null },
            onConfirm = { updatedRuleSub ->
                viewModel.save(
                    updatedRuleSub,
                    onSuccess = { showEditDialog = null },
                    onError = { context.toastOnUi(it) }
                )
            }
        )
    }
}

@Composable
fun RuleSubEditDialog(
    ruleSub: RuleSub,
    onDismiss: () -> Unit,
    onConfirm: (RuleSub) -> Unit
) {
    var name by remember { mutableStateOf(ruleSub.name) }
    var url by remember { mutableStateOf(ruleSub.url) }
    var type by remember { mutableIntStateOf(ruleSub.type) }
    val typeArray = stringArrayResource(R.array.rule_type)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rule_subscription)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "订阅类型",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
                CheckboxGroupContainer(columns = 2) {
                    typeArray.forEachIndexed { index, text ->
                        item {
                            CheckboxItem(
                                title = text,
                                checked = (index == type),
                                onCheckedChange = {
                                    if (it) type = index
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(ruleSub.copy(name = name, url = url, type = type))
            }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
