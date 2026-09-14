package io.legado.app.feature.replacerules.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.legado.app.feature.replacerules.res.Res
import io.legado.app.feature.replacerules.res.action_save
import io.legado.app.feature.replacerules.res.add_replace_rule
import io.legado.app.feature.replacerules.res.close
import io.legado.app.feature.replacerules.res.content
import io.legado.app.feature.replacerules.res.copy_rule
import io.legado.app.feature.replacerules.res.delete_selected
import io.legado.app.feature.replacerules.res.edit_replace_rule
import io.legado.app.feature.replacerules.res.exclude_scope
import io.legado.app.feature.replacerules.res.exclude_scope_hint
import io.legado.app.feature.replacerules.res.group
import io.legado.app.feature.replacerules.res.group_management
import io.legado.app.feature.replacerules.res.input_regex_or_keyword
import io.legado.app.feature.replacerules.res.input_replacement_or_group
import io.legado.app.feature.replacerules.res.match_pattern
import io.legado.app.feature.replacerules.res.more_actions
import io.legado.app.feature.replacerules.res.no_other_groups
import io.legado.app.feature.replacerules.res.paste_rule
import io.legado.app.feature.replacerules.res.regex_enabled
import io.legado.app.feature.replacerules.res.replace_with
import io.legado.app.feature.replacerules.res.rule_name
import io.legado.app.feature.replacerules.res.scope_hint
import io.legado.app.feature.replacerules.res.specific_scope
import io.legado.app.feature.replacerules.res.timeout_ms
import io.legado.app.feature.replacerules.res.title
import io.legado.app.feature.replacerules.res.use_regex
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppFloatingActionButton
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.ToggleChip
import io.legado.app.ui.widget.components.button.series.MediumPlainButton
import io.legado.app.ui.widget.components.checkBox.CheckboxItem
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import org.jetbrains.compose.resources.stringResource

@Composable
fun keyboardAsState(): State<Boolean> {
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    return rememberUpdatedState(isImeVisible)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReplaceEditScreen(
    state: ReplaceEditUiState,
    onIntent: (ReplaceEditIntent) -> Unit,
    onBack: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    var showMenu by remember { mutableStateOf(false) }
    val isKeyboardVisible by keyboardAsState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    DisposableEffect(Unit) {
        onDispose {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }
    val onSave = {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        onIntent(ReplaceEditIntent.Save)
    }

    AppScaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(if (state.id > 0) Res.string.edit_replace_rule else Res.string.add_replace_rule),
                navigationIcon = {
                    TopBarNavigationButton(
                        onClick = {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                            onBack()
                        }
                    )
                },
                actions = {
                    AnimatedVisibility(
                        visible = isKeyboardVisible,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        TopBarActionButton(
                            onClick = onSave,
                            imageVector = Icons.Default.Save,
                            contentDescription = stringResource(Res.string.action_save)
                        )
                    }
                    TopBarActionButton(
                        onClick = { showMenu = true },
                        imageVector = AppIcons.MoreVert,
                        contentDescription = stringResource(Res.string.more_actions)
                    )
                    RoundDropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        RoundDropdownMenuItem(
                            text = stringResource(Res.string.copy_rule),
                            onClick = {
                                showMenu = false
                                onIntent(ReplaceEditIntent.CopyRule)
                            }
                        )
                        RoundDropdownMenuItem(
                            text = stringResource(Res.string.paste_rule),
                            onClick = {
                                showMenu = false
                                onIntent(ReplaceEditIntent.PasteRule)
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
                onClick = onSave,
                tooltipText = stringResource(Res.string.action_save),
                icon = Icons.Default.Save
            )
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
                    onInsert = { text -> onIntent(ReplaceEditIntent.InsertTextAtCursor(text)) }
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
                    onValueChange = { onIntent(ReplaceEditIntent.OnNameChange(it)) },
                    label = stringResource(Res.string.rule_name),
                    backgroundColor = LegadoTheme.colorScheme.surfaceInput,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            if (it.isFocused) onIntent(ReplaceEditIntent.SetActiveField(ActiveField.Name))
                        },
                    singleLine = true
                )

                GroupSelector(
                    currentGroup = state.group,
                    allGroups = state.allGroups,
                    onGroupChange = { onIntent(ReplaceEditIntent.OnGroupChange(it)) },
                    onManageClick = { onIntent(ReplaceEditIntent.ToggleGroupDialog(true)) },
                    backgroundColor = LegadoTheme.colorScheme.surfaceInput,
                )

                AppTextField(
                    value = state.pattern,
                    onValueChange = { onIntent(ReplaceEditIntent.OnPatternChange(it)) },
                    label = stringResource(Res.string.match_pattern),
                    placeholder = { AppText(stringResource(Res.string.input_regex_or_keyword)) },
                    backgroundColor = LegadoTheme.colorScheme.surfaceInput,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            if (it.isFocused) onIntent(ReplaceEditIntent.SetActiveField(ActiveField.Pattern))
                        }
                )

                AppTextField(
                    value = state.replacement,
                    onValueChange = { onIntent(ReplaceEditIntent.OnReplacementChange(it)) },
                    label = stringResource(Res.string.replace_with),
                    placeholder = { AppText(stringResource(Res.string.input_replacement_or_group)) },
                    backgroundColor = LegadoTheme.colorScheme.surfaceInput,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            if (it.isFocused) onIntent(ReplaceEditIntent.SetActiveField(ActiveField.Replacement))
                        }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    ToggleChip(
                        label = stringResource(Res.string.title),
                        selected = state.scopeTitle,
                        checkedContentDescription = stringResource(Res.string.title),
                        onToggle = { onIntent(ReplaceEditIntent.OnScopeTitleChange(!state.scopeTitle)) }
                    )

                    Spacer(Modifier.width(8.dp))

                    ToggleChip(
                        label = stringResource(Res.string.content),
                        selected = state.scopeContent,
                        checkedContentDescription = stringResource(Res.string.content),
                        onToggle = { onIntent(ReplaceEditIntent.OnScopeContentChange(!state.scopeContent)) }
                    )

                    Spacer(Modifier.weight(1f))

                    ToggleChip(
                        label = stringResource(Res.string.use_regex),
                        selected = state.isRegex,
                        checkedContentDescription = stringResource(Res.string.regex_enabled),
                        onToggle = { onIntent(ReplaceEditIntent.OnRegexChange(!state.isRegex)) }
                    )

                }

                AppTextField(
                    value = state.scope,
                    onValueChange = { onIntent(ReplaceEditIntent.OnScopeChange(it)) },
                    label = stringResource(Res.string.specific_scope),
                    placeholder = { AppText(stringResource(Res.string.scope_hint)) },
                    backgroundColor = LegadoTheme.colorScheme.surfaceInput,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            if (it.isFocused) onIntent(ReplaceEditIntent.SetActiveField(ActiveField.Scope))
                        }
                )

                AppTextField(
                    value = state.excludeScope,
                    onValueChange = { onIntent(ReplaceEditIntent.OnExcludeScopeChange(it)) },
                    label = stringResource(Res.string.exclude_scope),
                    placeholder = { AppText(stringResource(Res.string.exclude_scope_hint)) },
                    backgroundColor = LegadoTheme.colorScheme.surfaceInput,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            if (it.isFocused) onIntent(ReplaceEditIntent.SetActiveField(ActiveField.Exclude))
                        }
                )

                AppTextField(
                    value = state.timeout,
                    onValueChange = { onIntent(ReplaceEditIntent.OnTimeoutChange(it)) },
                    label = stringResource(Res.string.timeout_ms),
                    placeholder = { AppText("3000") },
                    backgroundColor = LegadoTheme.colorScheme.surfaceInput,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(120.dp))

            }

            ManageGroupDialog(
                show = state.showGroupDialog,
                groups = state.allGroups.filter { it != "默认" },
                onDismiss = { onIntent(ReplaceEditIntent.ToggleGroupDialog(false)) },
                onDelete = { onIntent(ReplaceEditIntent.DeleteGroups(it)) }
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
    onManageClick: () -> Unit,
    backgroundColor: Color = Color.Unspecified,
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
                label = stringResource(Res.string.group),
                backgroundColor = backgroundColor,
                placeholder = { AppText("默认") },
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
        MediumPlainButton(
            onClick = onManageClick,
            icon = Icons.Default.Settings,
            contentDescription = stringResource(Res.string.group_management)
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
        title = stringResource(Res.string.group_management),
        content = {
            if (groups.isEmpty()) {
                AppText(stringResource(Res.string.no_other_groups))
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
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
        confirmText = stringResource(Res.string.delete_selected),
        onConfirm = {
            onDelete(selectedGroups.toList())
        },
        dismissText = stringResource(Res.string.close),
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
