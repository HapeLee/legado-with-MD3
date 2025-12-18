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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumTopAppBar
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
import io.legado.app.data.entities.Book
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isImage
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
    val book by viewModel.bookData.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
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
                    FilledTonalButton(onClick = onSave) {
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
            book?.let {
                BookInfoEditContent(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    book = it,
                    viewModel = viewModel
                )
            }
        }
    )
}

@Composable
fun BookInfoEditContent(
    modifier: Modifier = Modifier,
    book: Book,
    viewModel: BookInfoEditViewModel
) {
    val context = LocalContext.current
    var name by remember(book.name) { mutableStateOf(book.name) }
    var author by remember(book.author) { mutableStateOf(book.author) }
    var coverUrl by remember(book.customCoverUrl) { mutableStateOf(book.getDisplayCover()) }
    var intro by remember(book.customIntro) { mutableStateOf(book.getDisplayIntro()) }
    var remark by remember(book.remark) { mutableStateOf(book.remark) }

    val bookTypes = arrayOf("文本", "音频", "图片")
    val selectedTypeIndex = when {
        book.isImage -> 2
        book.isAudio -> 1
        else -> 0
    }
    var selectedType by remember { mutableStateOf(bookTypes[selectedTypeIndex]) }

    val selectCover = rememberLauncherForActivityResult(SelectImageContract()) {
        it.uri?.let { uri ->
            viewModel.coverChangeTo(context, uri) { newCoverUrl ->
                coverUrl = newCoverUrl
            }
        }
    }

    LaunchedEffect(coverUrl) {
        viewModel.book?.customCoverUrl = coverUrl
    }

    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Cover(
            path = coverUrl,
            modifier = Modifier
                .fillMaxWidth(0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = {
                (context as? BookInfoEditActivity)?.showDialogFragment(ChangeCoverDialog(book.name, book.author))
            }) {
                Text("网络搜索")
            }
            FilledTonalButton(onClick = { selectCover.launch() }) {
                Text("本地选择")
            }
            FilledTonalButton(onClick = { coverUrl = book.coverUrl ?: "" }) {
                Icon(
                    Icons.Default.Replay,
                    contentDescription = stringResource(id = R.string.default_cover)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("书名") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = author,
            onValueChange = { author = it },
            label = { Text("作者") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        BookTypeDropdown(
            bookTypes = bookTypes,
            selectedType = selectedType,
            onTypeSelected = { selectedType = it }
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = coverUrl ?: "",
            onValueChange = { coverUrl = it },
            label = { Text("封面链接") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = intro ?: "",
            onValueChange = { intro = it },
            label = { Text("简介") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 5
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = remark ?: "",
            onValueChange = { remark = it },
            label = { Text("备注") },
            modifier = Modifier.fillMaxWidth()
        )
    }

    LaunchedEffect(name, author, selectedType, coverUrl, intro, remark) {
        viewModel.updateBookState(name, author, selectedType, bookTypes, coverUrl, intro, remark)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookTypeDropdown(
    bookTypes: Array<String>,
    selectedType: String,
    onTypeSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth().menuAnchor(
                ExposedDropdownMenuAnchorType.PrimaryEditable,
                true
            ),
            readOnly = true,
            value = selectedType,
            onValueChange = {},
            label = { Text("书籍类型") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            bookTypes.forEach { selectionOption ->
                DropdownMenuItem(
                    text = { Text(selectionOption) },
                    onClick = {
                        onTypeSelected(selectionOption)
                        expanded = false
                    }
                )
            }
        }
    }
}
