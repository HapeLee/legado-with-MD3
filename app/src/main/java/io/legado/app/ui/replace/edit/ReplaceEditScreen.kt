package io.legado.app.ui.replace.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import io.legado.app.ui.widget.components.AppFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.animateFloatingActionButton
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
import androidx.compose.ui.zIndex
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.MediumIconButton
import io.legado.app.ui.widget.components.button.ToggleChip
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.ui.widget.components.checkBox.CheckboxItem
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
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
    viewModel: ReplaceEditViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    var showMenu by remember { mutableStateOf(false) }
    val isKeyboardVisible by keyboardAsState()

    AppScaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = if (state.id > 0) "Edit Replace Rule" else "New Replace Rule",
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBack)
                },
                actions = {
                    AnimatedVisibility(
                        visible = isKeyboardVisible,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        TopBarActionButton(
                            onClick = {
                                viewModel.save(onSaveSuccess)
                            },
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save"
                        )
                    }
                    TopBarActionButton(
                        onClick = { showMenu = true },
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Options"
                    )
                    RoundDropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        RoundDropdownMenuItem(
                            text = "Copy Rule",
                            onClick = {
                                showMenu = false
                                viewModel.copyRule()
                            }
                        )
                        RoundDropdownMenuItem(
                            text = "Paste Rule",
                            onClick = {
                                showMenu = false
                                viewModel.pasteRule(onSuccess = {})
                            }
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            AppFloatingActionButton(
                modifier = Modifier
                    .navigationBarsPadding()
                    .animateFloatingActionButton(
                        visible = !isKeyboardVisible,
                        alignment = Alignment.BottomEnd,
                    ),
                onClick = { viewModel.save(onSaveSuccess) },
                tooltipText = "Save"
            ) {
                AppIcon(Icons.Default.Save, contentDescription = "Save")
            }
        }, contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
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
                    .zIndex(1f)
            ) {
                QuickInputBar(
                    onInsert = { text -> viewModel.insertTextAtCursor(text) }
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                AppTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = "Rule Name",
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

                AppTextField(
                    value = state.pattern,
                    onValueChange = viewModel::onPatternChange,
                    label = "Pattern",
                    placeholder = { AppText("Enter regex or keyword") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            if (it.isFocused) viewModel.activeField =
                                ReplaceEditViewModel.ActiveField.Pattern
                        }
                )

                AppTextField(
                    value = state.replacement,
                    onValueChange = viewModel::onReplacementChange,
                    label = "Replacement",
                    placeholder = { AppText("Enter replacement text or capture group") },
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
                    ToggleChip(
                        label = "Title",
                        selected = state.scopeTitle,
                        checkedContentDescription = "Selected",
                        onToggle = { viewModel.onScopeTitleChange(!state.scopeTitle) }
                    )

                    Spacer(Modifier.width(8.dp))

                    ToggleChip(
                        label = "Content",
                        selected = state.scopeContent,
                        checkedContentDescription = "Selected",
                        onToggle = { viewModel.onScopeContentChange(!state.scopeContent) }
                    )

                    Spacer(Modifier.weight(1f))

                    ToggleChip(
                        label = "Use Regex",
                        selected = state.isRegex,
                        checkedContentDescription = "Regex Enabled",
                        onToggle = { viewModel.onRegexChange(!state.isRegex) }
                    )

                }

                AppTextField(
                    value = state.scope,
                    onValueChange = viewModel::onScopeChange,
                    label = "Scope",
                    placeholder = { AppText("Specify the applicable scope of the rule") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            if (it.isFocused) viewModel.activeField =
                                ReplaceEditViewModel.ActiveField.Scope
                        }
                )

                AppTextField(
                    value = state.excludeScope,
                    onValueChange = viewModel::onExcludeScopeChange,
                    label = "Exclude Scope",
                    placeholder = { AppText("Specify the scope where the rule does not apply") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            if (it.isFocused) viewModel.activeField =
                                ReplaceEditViewModel.ActiveField.Exclude
                        }
                )

                AppTextField(
                    value = state.timeout,
                    onValueChange = viewModel::onTimeoutChange,
                    label = "Timeout (ms)",
                    placeholder = { AppText("3000") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(120.dp))

            }

            ManageGroupDialog(
                show = state.showGroupDialog,
                groups = state.allGroups.filter { it != "Default" },
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
            AppTextField(
                value = currentGroup,
                onValueChange = onGroupChange,
                label = "Group",
                placeholder = { AppText("Default") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(
                        ExposedDropdownMenuAnchorType.PrimaryEditable,
                        true
                    )
            )
            RoundDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                allGroups.forEach { selectionOption ->
                    RoundDropdownMenuItem(
                        text = selectionOption,
                        onClick = {
                            onGroupChange(selectionOption)
                            expanded = false
                        }
                    )
                }
            }
        }
        MediumIconButton(
            onClick = onManageClick,
            imageVector = Icons.Default.Settings
        )
    }
}

@Composable
fun ManageGroupDialog(
    show: Boolean,
    groups: List<String>,
    onDismiss: () -> Unit,
    onDelete: (List<String>) -> Unit
) {
    var selectedGroups by remember(show) { mutableStateOf(emptySet<String>()) }

    AppAlertDialog(
        show = show,
        onDismissRequest = onDismiss,
        title = "Manage Groups",
        content = {
            if (groups.isEmpty()) {
                AppText("No other groups")
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    groups.forEach { group ->
                        val isSelected = selectedGroups.contains(group)

                        CheckboxItem(
                            title = group,
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                selectedGroups = if (checked) {
                                    selectedGroups + group
                                } else {
                                    selectedGroups - group
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmText = "Delete Selected",
        onConfirm = {
            onDelete(selectedGroups.toList())
        },
        dismissText = "Close",
        onDismiss = onDismiss
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
                label = { AppText(symbol) },
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}
