package io.legado.app.ui.book.info.edit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.ui.book.changecover.ChangeCoverDialog
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.fadingEdge
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.MediumOutlinedIconButton
import io.legado.app.ui.widget.components.cover.CoilBookCover
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.text.AnimatedTextLine
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.SelectImageContract
import io.legado.app.utils.launch
import io.legado.app.utils.showDialogFragment
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BookInfoEditScreen(
    viewModel: BookInfoEditViewModel,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(id = R.string.book_info_edit),
                navigationIcon = {
                    TopBarNavigationButton(
                        onClick = onBack
                    )
                },
                actions = {
                    TopBarNavigationButton(
                        onClick = { viewModel.save(onSave) },
                        imageVector = Icons.Default.Save
                    )
                },
                scrollBehavior = scrollBehavior
            )
        },
        content = { paddingValues ->
            uiState.book?.let {
                BookInfoEditContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .consumeWindowInsets(paddingValues)
                        .imePadding()
                        .verticalScroll(rememberScrollState()),
                    uiState = uiState,
                    viewModel = viewModel
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BookInfoEditContent(
    modifier: Modifier = Modifier,
    uiState: BookInfoEditUiState,
    viewModel: BookInfoEditViewModel
) {
    val context = LocalContext.current

    val selectCover = rememberLauncherForActivityResult(SelectImageContract()) {
        it.uri?.let { uri ->
            viewModel.coverChangeTo(context, uri)
        }
    }

    Column(
        modifier = modifier.padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoilBookCover(
                name = uiState.name,
                author = uiState.author,
                path = uiState.coverUrl,
                modifier = Modifier
                    .width(110.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MediumOutlinedIconButton(
                        onClick = {
                            (context as? BookInfoEditActivity)?.showDialogFragment(
                                ChangeCoverDialog(
                                    uiState.name,
                                    uiState.author
                                )
                            )
                        },
                        imageVector = Icons.Default.ImageSearch
                    )
                    MediumOutlinedIconButton(
                        onClick = { selectCover.launch() },
                        imageVector = Icons.Default.FolderOpen
                    )
                    MediumOutlinedIconButton(
                        onClick = { viewModel.resetCover() },
                        imageVector = Icons.Default.Replay
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                BookTypeDropdown(
                    bookTypes = uiState.bookTypes,
                    selectedType = uiState.selectedType,
                    onTypeSelected = { viewModel.onBookTypeChange(it) }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        SwitchSettingItem(
            title = "固定书籍类型",
            description = "书籍更新后不覆盖书籍类型",
            checked = uiState.fixedType,
            onCheckedChange = { viewModel.onFixedTypeChange(it) }
        )
        Spacer(modifier = Modifier.height(16.dp))
        val bookInfoInputColor = ThemeConfig.bookInfoInputColor
        val inputBackgroundColor = if (bookInfoInputColor != 0) {
            Color(bookInfoInputColor)
        } else {
            Color.Unspecified
        }
        AppTextField(
            value = uiState.name,
            onValueChange = { viewModel.onNameChange(it) },
            label = "书名",
            backgroundColor = inputBackgroundColor,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        AppTextField(
            value = uiState.author,
            onValueChange = { viewModel.onAuthorChange(it) },
            label = "作者",
            backgroundColor = inputBackgroundColor,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        AppTextField(
            value = uiState.coverUrl ?: "",
            onValueChange = { viewModel.onCoverUrlChange(it) },
            label = "封面链接",
            backgroundColor = inputBackgroundColor,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        KindEditor(
            kindList = uiState.kindList,
            onKindListChange = { viewModel.onKindListChange(it) },
            onReset = { viewModel.resetKinds() },
            backgroundColor = inputBackgroundColor
        )
        Spacer(modifier = Modifier.height(8.dp))
        AppTextField(
            value = uiState.intro ?: "",
            onValueChange = { viewModel.onIntroChange(it) },
            label = "简介",
            backgroundColor = inputBackgroundColor,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        AppTextField(
            value = uiState.remark ?: "",
            onValueChange = { viewModel.onRemarkChange(it) },
            label = "备注",
            backgroundColor = inputBackgroundColor,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookTypeDropdown(
    bookTypes: List<String>,
    selectedType: String,
    onTypeSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val textFieldState = rememberTextFieldState(
        initialText = selectedType
    )

    LaunchedEffect(selectedType) {
        textFieldState.setTextAndPlaceCursorAtEnd(selectedType)
    }

    val bookInfoInputColor = ThemeConfig.bookInfoInputColor
    val inputBackgroundColor = if (bookInfoInputColor != 0) {
        Color(bookInfoInputColor)
    } else {
        Color.Unspecified
    }

    ExposedDropdownMenuBox(
        modifier = Modifier.padding(horizontal = 8.dp),
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        AppTextField(
            state = textFieldState,
            readOnly = true,
            lineLimits = TextFieldLineLimits.SingleLine,
            label = "书籍类型",
            backgroundColor = inputBackgroundColor,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(
                    ExposedDropdownMenuAnchorType.PrimaryEditable,
                    enabled = true
                ),
        )

        RoundDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            bookTypes.forEach { option ->
                RoundDropdownMenuItem(
                    text = option,
                    onClick = {
                        onTypeSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KindEditor(
    kindList: List<String>,
    onKindListChange: (List<String>) -> Unit,
    onReset: () -> Unit,
    backgroundColor: Color
) {
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var editText by remember { mutableStateOf("") }
    val hapticFeedback = LocalHapticFeedback.current

    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        val mutable = kindList.toMutableList()
        mutable.add(to.index, mutable.removeAt(from.index))
        onKindListChange(mutable)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fadingEdge(listState),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(kindList.size, key = { kindList[it] }) { index ->
                val kind = kindList[index]
                ReorderableItem(reorderableState, key = kind) { isDragging ->
                    KindChip(
                        text = kind,
                        isDragging = isDragging,
                        onClick = {
                            editingIndex = index
                            editText = kind
                        },
                        modifier = Modifier
                            .longPressDraggableHandle(
                                onDragStarted = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                },
                                onDragStopped = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                }
                            )
                            .animateItem()
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        MediumOutlinedIconButton(
            onClick = {
                onReset()
            },
            imageVector = Icons.Default.Replay
        )
        Spacer(modifier = Modifier.width(8.dp))
        MediumOutlinedIconButton(
            onClick = {
                editingIndex = -1
                editText = ""
            },
            imageVector = Icons.Default.Add
        )
    }

    if (editingIndex != null) {
        val isAdding = editingIndex == -1
        AppAlertDialog(
            show = true,
            onDismissRequest = { editingIndex = null },
            title = if (isAdding) "添加标签" else "编辑标签",
            content = {
                AppTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    label = "标签",
                    backgroundColor = backgroundColor,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmText = stringResource(android.R.string.ok),
            onConfirm = {
                val trimmed = editText.trim()
                if (trimmed.isNotBlank()) {
                    val mutable = kindList.toMutableList()
                    if (isAdding) {
                        if (trimmed !in kindList) mutable.add(trimmed)
                    } else {
                        mutable[editingIndex!!] = trimmed
                    }
                    onKindListChange(mutable)
                }
                editingIndex = null
            },
            dismissText = if (isAdding) stringResource(android.R.string.cancel) else stringResource(
                R.string.delete
            ),
            onDismiss = if (isAdding) {
                { editingIndex = null }
            } else {
                {
                    onKindListChange(kindList.toMutableList().apply { removeAt(editingIndex!!) })
                    editingIndex = null
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun KindChip(
    text: String,
    isDragging: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = LegadoTheme.colorScheme.surfaceContainer,
        contentColor = LegadoTheme.colorScheme.onSurface,
        shadowElevation = if (isDragging) 8.dp else 0.dp,
        modifier = modifier
            .zIndex(if (isDragging) 1f else 0f)
            .clickable(onClick = onClick)
    ) {
        AnimatedTextLine(
            text = text,
            style = LegadoTheme.typography.labelLargeEmphasized,
            color = LegadoTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
