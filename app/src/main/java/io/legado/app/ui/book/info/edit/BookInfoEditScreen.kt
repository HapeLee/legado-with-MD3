package io.legado.app.ui.book.info.edit

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.ui.book.changecover.ChangeCoverDialog
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.button.MediumOutlinedIconButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.ui.widget.components.cover.CoilBookCover
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.utils.SelectImageContract
import io.legado.app.utils.launch
import io.legado.app.utils.showDialogFragment

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
        AppTextField(
            value = uiState.name,
            onValueChange = { viewModel.onNameChange(it) },
            label = "书名",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        AppTextField(
            value = uiState.author,
            onValueChange = { viewModel.onAuthorChange(it) },
            label = "作者",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        AppTextField(
            value = uiState.coverUrl ?: "",
            onValueChange = { viewModel.onCoverUrlChange(it) },
            label = "封面链接",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        AppTextField(
            value = uiState.intro ?: "",
            onValueChange = { viewModel.onIntroChange(it) },
            label = "简介",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        AppTextField(
            value = uiState.remark ?: "",
            onValueChange = { viewModel.onRemarkChange(it) },
            label = "备注",
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
