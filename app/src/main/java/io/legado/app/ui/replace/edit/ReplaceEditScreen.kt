package io.legado.app.ui.replace.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel

@Composable
fun keyboardAsState(): State<Boolean> {
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    return rememberUpdatedState(isImeVisible)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReplaceEditScreen(
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    onShowHelp: (String) -> Unit,
    viewModel: ReplaceEditViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showMenu by remember { mutableStateOf(false) }
    val isKeyboardVisible by keyboardAsState()
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                MediumFlexibleTopAppBar(
                    title = { Text(if (state.id > 0) "编辑替换规则" else "新增替换规则") },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    },
                    actions = {

                        IconButton(onClick = { onShowHelp("regexHelp") }) {
                            Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "帮助")
                        }

                        IconButton(onClick = {
                            viewModel.save(onSaveSuccess)
                        }) {
                            Icon(Icons.Default.Save, contentDescription = "保存")
                        }

                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {

                            DropdownMenuItem(
                                text = { Text("复制规则") },
                                onClick = {
                                    showMenu = false
                                    viewModel.copyRule()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("粘贴规则") },
                                onClick = {
                                    showMenu = false
                                    viewModel.pasteRule(onSuccess = {})
                                }
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("规则名称") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            if (it.isFocused) viewModel.activeField =
                                ReplaceEditViewModel.ActiveField.Name
                        },
                    singleLine = true
                )

                GroupSelector(
                    currentGroup = state.group,
                    allGroups = state.allGroups,
                    onGroupChange = viewModel::onGroupChange,
                    onManageClick = { viewModel.toggleGroupDialog(true) }
                )

                OutlinedTextField(
                    value = state.pattern,
                    onValueChange = viewModel::onPatternChange,
                    label = { Text("匹配规则") },
                    placeholder = { Text("输入正则表达式或关键字") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            if (it.isFocused) viewModel.activeField =
                                ReplaceEditViewModel.ActiveField.Pattern
                        }
                )

                OutlinedTextField(
                    value = state.replacement,
                    onValueChange = viewModel::onReplacementChange,
                    label = { Text("替换为") },
                    placeholder = { Text("输入替换内容或捕获组") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            if (it.isFocused) viewModel.activeField =
                                ReplaceEditViewModel.ActiveField.Replacement
                        }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    FilterChip(
                        selected = state.scopeTitle,
                        onClick = { viewModel.onScopeTitleChange(!state.scopeTitle) },
                        label = { Text("标题") },
                        leadingIcon = if (state.scopeTitle) {
                            { Icon(Icons.Default.Check, contentDescription = "已选", Modifier.size(FilterChipDefaults.IconSize)) }
                        } else null
                    )

                    Spacer(Modifier.width(8.dp))

                    FilterChip(
                        selected = state.scopeContent,
                        onClick = { viewModel.onScopeContentChange(!state.scopeContent) },
                        label = { Text("内容") },
                        leadingIcon = if (state.scopeContent) {
                            { Icon(Icons.Default.Check, contentDescription = "已选", Modifier.size(FilterChipDefaults.IconSize)) }
                        } else null
                    )

                    Spacer(Modifier.weight(1f))

                    FilterChip(
                        selected = state.isRegex,
                        onClick = { viewModel.onRegexChange(!state.isRegex) },
                        label = { Text("使用正则") },
                        leadingIcon = if (state.isRegex) {
                            { Icon(Icons.Default.Check, contentDescription = "正则已启用", Modifier.size(FilterChipDefaults.IconSize)) }
                        } else null
                    )

                }

                OutlinedTextField(
                    value = state.scope,
                    onValueChange = viewModel::onScopeChange,
                    label = { Text("特定范围") },
                    placeholder = { Text("指定规则适用的范围") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            if (it.isFocused) viewModel.activeField =
                                ReplaceEditViewModel.ActiveField.Scope
                        }
                )

                OutlinedTextField(
                    value = state.excludeScope,
                    onValueChange = viewModel::onExcludeScopeChange,
                    label = { Text("排除范围") },
                    placeholder = { Text("指定规则不适用的范围") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            if (it.isFocused) viewModel.activeField =
                                ReplaceEditViewModel.ActiveField.Exclude
                        }
                )

                OutlinedTextField(
                    value = state.timeout,
                    onValueChange = viewModel::onTimeoutChange,
                    label = { Text("超时 (ms)") },
                    placeholder = { Text("3000") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

            }
        }

        AnimatedVisibility(
            visible = isKeyboardVisible,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
            ),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
        ) {
            QuickInputBar(
                onInsert = { text -> viewModel.insertTextAtCursor(text) }
            )
        }


        if (state.showGroupDialog) {
            ManageGroupDialog(
                groups = state.allGroups.filter { it != "默认" },
                onDismiss = { viewModel.toggleGroupDialog(false) },
                onDelete = { viewModel.deleteGroups(it) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSelector(
    currentGroup: String,
    allGroups: List<String>,
    onGroupChange: (String) -> Unit,
    onManageClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = currentGroup,
                onValueChange = onGroupChange,
                label = { Text("分组") },
                placeholder = { Text("默认") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(
                        ExposedDropdownMenuAnchorType.PrimaryEditable,
                        true
                    )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                allGroups.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = { Text(selectionOption) },
                        onClick = {
                            onGroupChange(selectionOption)
                            expanded = false
                        }
                    )
                }
            }
        }
        IconButton(onClick = onManageClick) {
            Icon(Icons.Default.Settings, "Manage")
        }
    }
}

@Composable
fun ManageGroupDialog(
    groups: List<String>,
    onDismiss: () -> Unit,
    onDelete: (List<String>) -> Unit
) {

    val selected = remember { mutableStateMapOf<String, Boolean>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分组管理") },
        text = {
            if (groups.isEmpty()) Text("暂无其他分组")
            else Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groups.forEach { group ->
                    val isSelected = selected[group] ?: false
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                shape = MaterialTheme.shapes.small
                            )
                            .clickable { selected[group] = !isSelected }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isSelected, onCheckedChange = null)
                        Text(group, Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = {
                    val toDelete = selected.filter { it.value }.keys.toList()
                    onDelete(toDelete)
                },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                    containerColor = Color.Transparent,
                ),
            ) {
                Text("删除选中")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
fun QuickInputBar(
    onInsert: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val symbols = listOf(".*", "\\d+", "\\w+", "[]", "()", "^", "$", "|", "{}", "<>")

    BottomAppBar(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        symbols.forEach { symbol ->
            AssistChip(
                onClick = { onInsert(symbol) },
                label = { Text(symbol) },
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}