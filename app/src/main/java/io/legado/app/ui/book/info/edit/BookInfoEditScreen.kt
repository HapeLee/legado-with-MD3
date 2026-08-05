package io.legado.app.ui.book.info.edit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.fadingEdge
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.MediumOutlinedButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.image.cover.CoilBookCover
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.reorderAccessibility
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.text.AnimatedTextLine
import io.legado.app.ui.widget.components.text.AppText
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
    onSave: () -> Unit,
    onOpenCharacterList: (String) -> Unit,
    onOpenCharacterNetwork: (String) -> Unit,
    onOpenKnowledgeList: (String) -> Unit,
    onOpenEventList: (String) -> Unit,
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
                    viewModel = viewModel,
                    onOpenCharacterList = onOpenCharacterList,
                    onOpenCharacterNetwork = onOpenCharacterNetwork,
                    onOpenKnowledgeList = onOpenKnowledgeList,
                    onOpenEventList = onOpenEventList,
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
    viewModel: BookInfoEditViewModel,
    onOpenCharacterList: (String) -> Unit,
    onOpenCharacterNetwork: (String) -> Unit,
    onOpenKnowledgeList: (String) -> Unit,
    onOpenEventList: (String) -> Unit,
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
                    MediumOutlinedButton(
                        onClick = {
                            (context as? BookInfoEditActivity)?.showDialogFragment(
                                ChangeCoverDialog(
                                    uiState.name,
                                    uiState.author
                                )
                            )
                        },
                        icon = Icons.Default.ImageSearch,
                        contentDescription = stringResource(R.string.refresh_cover)
                    )
                    MediumOutlinedButton(
                        onClick = { selectCover.launch() },
                        icon = Icons.Default.FolderOpen,
                        contentDescription = stringResource(R.string.select_folder)
                    )
                    MediumOutlinedButton(
                        onClick = { viewModel.resetCover() },
                        icon = Icons.Default.Replay,
                        contentDescription = stringResource(R.string.cover_reset)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                BookTypeDropdown(
                    bookTypes = listOf(
                        BookInfoEditType.TEXT to stringResource(R.string.book_type_text),
                        BookInfoEditType.AUDIO to stringResource(R.string.book_type_audio),
                        BookInfoEditType.IMAGE to stringResource(R.string.book_type_image)
                    ),
                    selectedType = uiState.selectedType,
                    onTypeSelected = { viewModel.onBookTypeChange(it) }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        BookKnowledgeEditCard(
            bookUrl = uiState.book?.bookUrl.orEmpty(),
            onOpenCharacterList = onOpenCharacterList,
            onOpenCharacterNetwork = onOpenCharacterNetwork,
            onOpenKnowledgeList = onOpenKnowledgeList,
            onOpenEventList = onOpenEventList,
        )
        Spacer(modifier = Modifier.height(16.dp))
        SwitchSettingItem(
            title = stringResource(R.string.fixed_book_type),
            description = stringResource(R.string.fixed_book_type_summary),
            checked = uiState.fixedType,
            onCheckedChange = { viewModel.onFixedTypeChange(it) }
        )
        Spacer(modifier = Modifier.height(16.dp))
        AppTextField(
            value = uiState.name,
            onValueChange = { viewModel.onNameChange(it) },
            label = stringResource(R.string.book_name),
            backgroundColor = LegadoTheme.colorScheme.surfaceInput,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        AppTextField(
            value = uiState.author,
            onValueChange = { viewModel.onAuthorChange(it) },
            label = stringResource(R.string.author),
            backgroundColor = LegadoTheme.colorScheme.surfaceInput,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        AppTextField(
            value = uiState.coverUrl ?: "",
            onValueChange = { viewModel.onCoverUrlChange(it) },
            label = stringResource(R.string.cover_url),
            backgroundColor = LegadoTheme.colorScheme.surfaceInput,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        AppTextField(
            value = uiState.sourceKindList.joinToString(", "),
            onValueChange = {},
            readOnly = true,
            label = stringResource(R.string.source_categories),
            backgroundColor = LegadoTheme.colorScheme.surfaceInput,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        KindEditor(
            kindList = uiState.kindList,
            onKindListChange = { viewModel.onKindListChange(it) },
            onReset = { viewModel.resetKinds() },
            backgroundColor = LegadoTheme.colorScheme.surfaceInput,
        )
        Spacer(modifier = Modifier.height(8.dp))
        AppTextField(
            value = uiState.intro ?: "",
            onValueChange = { viewModel.onIntroChange(it) },
            label = stringResource(R.string.book_intro),
            backgroundColor = LegadoTheme.colorScheme.surfaceInput,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        AppTextField(
            value = uiState.remark ?: "",
            onValueChange = { viewModel.onRemarkChange(it) },
            label = stringResource(R.string.book_remark),
            backgroundColor = LegadoTheme.colorScheme.surfaceInput,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun BookKnowledgeEditCard(
    bookUrl: String,
    onOpenCharacterList: (String) -> Unit,
    onOpenCharacterNetwork: (String) -> Unit,
    onOpenKnowledgeList: (String) -> Unit,
    onOpenEventList: (String) -> Unit,
) {
    NormalCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppText(
                text = stringResource(R.string.book_info_knowledge),
                style = LegadoTheme.typography.titleMedium,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    MediumOutlinedButton(
                        onClick = { onOpenCharacterList(bookUrl) },
                        enabled = bookUrl.isNotEmpty(),
                        icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
                        text = stringResource(R.string.book_characters),
                    )
                }
                item {
                    MediumOutlinedButton(
                        onClick = { onOpenCharacterNetwork(bookUrl) },
                        enabled = bookUrl.isNotEmpty(),
                        icon = Icons.Default.Group,
                        text = stringResource(R.string.character_network),
                    )
                }
                item {
                    MediumOutlinedButton(
                        onClick = { onOpenKnowledgeList(bookUrl) },
                        enabled = bookUrl.isNotEmpty(),
                        icon = Icons.Default.Book,
                        text = stringResource(R.string.book_knowledge),
                    )
                }
                item {
                    MediumOutlinedButton(
                        onClick = { onOpenEventList(bookUrl) },
                        enabled = bookUrl.isNotEmpty(),
                        icon = Icons.Default.Timeline,
                        text = stringResource(R.string.plot_events),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookTypeDropdown(
    bookTypes: List<Pair<BookInfoEditType, String>>,
    selectedType: BookInfoEditType,
    onTypeSelected: (BookInfoEditType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedTypeLabel = bookTypes.firstOrNull { it.first == selectedType }?.second.orEmpty()

    val textFieldState = rememberTextFieldState(
        initialText = selectedTypeLabel
    )

    LaunchedEffect(selectedTypeLabel) {
        textFieldState.setTextAndPlaceCursorAtEnd(selectedTypeLabel)
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
            label = stringResource(R.string.book_type_label),
            backgroundColor = LegadoTheme.colorScheme.surfaceInput,
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
            bookTypes.forEach { (type, label) ->
                RoundDropdownMenuItem(
                    text = label,
                    onClick = {
                        onTypeSelected(type)
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
    fun moveKind(from: Int, to: Int) {
        val mutable = kindList.toMutableList()
        mutable.add(to, mutable.removeAt(from))
        onKindListChange(mutable)
    }
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        moveKind(from.index, to.index)
    }

    Column(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AppText(
            text = stringResource(R.string.my_tags)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fadingEdge(listState),
                contentPadding = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(kindList.size, key = { kindList[it] }) { index ->
                    val kind = kindList[index]
                    ReorderableItem(
                        state = reorderableState,
                        key = kind
                    ) { isDragging ->
                        KindChip(
                            text = kind,
                            isDragging = isDragging,
                            onClick = {
                                editingIndex = index
                                editText = kind
                            },
                            modifier = Modifier
                                .reorderAccessibility(
                                    index = index,
                                    itemCount = kindList.size,
                                    onMove = ::moveKind,
                                )
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
            MediumOutlinedButton(
                onClick = {
                    onReset()
                },
                icon = Icons.Default.Replay,
                contentDescription = stringResource(R.string.reset)
            )
            Spacer(modifier = Modifier.width(8.dp))
            MediumOutlinedButton(
                onClick = {
                    editingIndex = -1
                    editText = ""
                },
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.add)
            )
        }
    }


    if (editingIndex != null) {
        val isAdding = editingIndex == -1
        AppAlertDialog(
            show = editingIndex != null,
            onDismissRequest = { editingIndex = null },
            title = if (isAdding) {
                stringResource(R.string.add_tag)
            } else {
                stringResource(R.string.edit_tag)
            },
            content = {
                AppTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    label = stringResource(R.string.tag),
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
    NormalCard(
        cornerRadius = 8.dp,
        containerColor = LegadoTheme.colorScheme.surfaceContainer,
        contentColor = LegadoTheme.colorScheme.onSurface,
        elevation = if (isDragging) 2.dp else 0.dp,
        modifier = modifier
            .padding(2.dp)
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
