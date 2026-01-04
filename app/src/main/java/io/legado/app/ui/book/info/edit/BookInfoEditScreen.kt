package io.legado.app.ui.book.info.edit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import io.legado.app.ui.widget.components.Cover
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(text = stringResource(id = R.string.book_info_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    FilledTonalIconButton(
                        onClick = { viewModel.save(onSave) },
                        shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = stringResource(id = R.string.action_save)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        content = { paddingValues ->
            uiState.book?.let {
                BookInfoEditContent(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize()
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
            Cover(
                path = uiState.coverUrl,
                modifier = Modifier
                    .width(110.dp)
                    .height(154.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            (context as? BookInfoEditActivity)?.showDialogFragment(
                                ChangeCoverDialog(
                                    uiState.name,
                                    uiState.author
                                )
                            )
                        },
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Icon(
                            Icons.Default.ImageSearch,
                            contentDescription = stringResource(id = R.string.default_cover)
                        )
                    }
                    OutlinedButton(
                        onClick = { selectCover.launch() },
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = stringResource(id = R.string.default_cover)
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.resetCover() },
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Icon(
                            Icons.Default.Replay,
                            contentDescription = stringResource(id = R.string.default_cover)
                        )
                    }
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
        OutlinedTextField(
            value = uiState.name,
            onValueChange = { viewModel.onNameChange(it) },
            label = { Text("书名") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.author,
            onValueChange = { viewModel.onAuthorChange(it) },
            label = { Text("作者") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.coverUrl ?: "",
            onValueChange = { viewModel.onCoverUrlChange(it) },
            label = { Text("封面链接") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.intro ?: "",
            onValueChange = { viewModel.onIntroChange(it) },
            label = { Text("简介") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 5
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.remark ?: "",
            onValueChange = { viewModel.onRemarkChange(it) },
            label = { Text("备注") },
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
        OutlinedTextField(
            state = textFieldState,
            readOnly = true,
            lineLimits = TextFieldLineLimits.SingleLine,
            label = { Text("书籍类型") },
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

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            bookTypes.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onTypeSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

