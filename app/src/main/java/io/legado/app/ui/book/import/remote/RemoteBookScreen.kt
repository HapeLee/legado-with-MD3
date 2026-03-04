package io.legado.app.ui.book.import.remote

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.Server
import io.legado.app.help.config.AppConfig
import io.legado.app.model.remote.RemoteBook
import io.legado.app.ui.widget.components.ActionItem
import io.legado.app.ui.widget.components.EmptyMessageView
import io.legado.app.ui.widget.components.SelectionActions
import io.legado.app.ui.widget.components.button.SmallIconButton
import io.legado.app.ui.widget.components.button.SmallTonalIconButton
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.list.ListScaffold
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.GlassModalBottomSheet
import io.legado.app.utils.ArchiveUtils
import io.legado.app.utils.ConvertUtils
import io.legado.app.utils.FileDoc
import io.legado.app.utils.find
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.koin.androidx.compose.koinViewModel

sealed class RemoteBookDialog {
    data class DownloadArchive(val remoteBook: RemoteBook) : RemoteBookDialog()
    data class ReImport(val remoteBook: RemoteBook) : RemoteBookDialog()
}

sealed class RemoteBookSheet {
    data object Servers : RemoteBookSheet()
    data class ServerConfig(val server: Server?) : RemoteBookSheet()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RemoteBookScreen(
    viewModel: RemoteBookViewModel = koinViewModel(),
    onBackClick: () -> Unit,
    startReadBook: (Book) -> Unit,
    onArchiveFileClick: (FileDoc) -> Unit,
    selectBookFolder: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var dialogState by remember { mutableStateOf<RemoteBookDialog?>(null) }
    var showSheet by remember { mutableStateOf<RemoteBookSheet?>(null) }

    val startRead: (RemoteBook) -> Unit =
        remember(viewModel, startReadBook, onArchiveFileClick, selectBookFolder) {
            { remoteBook ->
                scope.launch {
                    val downloadFileName = remoteBook.filename
                    if (!ArchiveUtils.isArchive(downloadFileName)) {
                        viewModel.getLocalBook(downloadFileName)?.let {
                            startReadBook(it)
                        }
                    } else {
                        val bookTreeUri = AppConfig.defaultBookTreeUri
                        if (bookTreeUri == null) {
                            selectBookFolder()
                        } else {
                            val downloadArchiveFileDoc = withContext(Dispatchers.IO) {
                                FileDoc.fromUri(bookTreeUri.toUri(), true)
                                    .find(downloadFileName)
                            }
                            if (downloadArchiveFileDoc == null) {
                                dialogState = RemoteBookDialog.DownloadArchive(remoteBook)
                            } else {
                                onArchiveFileClick(downloadArchiveFileDoc)
                            }
                        }
                    }
                }
            }
        }

    dialogState?.let { state ->
        when (state) {
            is RemoteBookDialog.DownloadArchive -> {
                AlertDialog(
                    onDismissRequest = { dialogState = null },
                    title = { Text(stringResource(R.string.draw)) },
                    text = { Text(stringResource(R.string.archive_not_found)) },
                    confirmButton = {
                        OutlinedButton(onClick = {
                            scope.launch { viewModel.addToBookshelf(setOf(state.remoteBook)) }
                            dialogState = null
                        }) {
                            Text(stringResource(android.R.string.ok))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { dialogState = null }) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    }
                )
            }

            is RemoteBookDialog.ReImport -> {
                AlertDialog(
                    onDismissRequest = { dialogState = null },
                    title = { Text("是否重新加入书架？") },
                    text = { Text("将会覆盖书籍") },
                    confirmButton = {
                        OutlinedButton(onClick = {
                            scope.launch { viewModel.addToBookshelf(setOf(state.remoteBook)) }
                            dialogState = null
                        }) {
                            Text(stringResource(android.R.string.ok))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { dialogState = null }) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    }
                )
            }
        }
    }

    if (showSheet != null) {
        GlassModalBottomSheet(
            onDismissRequest = { showSheet = null }
        ) {
            when (val state = showSheet) {
                is RemoteBookSheet.Servers -> {
                    ServersSheetContent(
                        servers = uiState.servers,
                        selectedServerId = uiState.selectedServerId,
                        onSelect = {
                            viewModel.selectServer(it)
                            showSheet = null
                        },
                        onEdit = { showSheet = RemoteBookSheet.ServerConfig(it) },
                        onDelete = { viewModel.deleteServer(it) },
                        onAdd = { showSheet = RemoteBookSheet.ServerConfig(null) },
                        onDefault = {
                            viewModel.selectServer(AppConst.DEFAULT_WEBDAV_ID)
                            showSheet = null
                        }
                    )
                }

                is RemoteBookSheet.ServerConfig -> {
                    ServerConfigSheetContent(
                        server = state.server,
                        onSave = {
                            viewModel.saveServer(it)
                            showSheet = RemoteBookSheet.Servers
                        },
                        onCancel = { showSheet = RemoteBookSheet.Servers }
                    )
                }

                else -> {}
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.initData { viewModel.loadRemoteBookList() }
    }

    LaunchedEffect(viewModel) {
        viewModel.permissionDenialEvent.collect { selectBookFolder() }
    }

    ListScaffold(
        title = "远程书籍",
        state = uiState,
        onBackClick = onBackClick,
        onSearchToggle = { viewModel.setSearchMode(it) },
        onSearchQueryChange = { viewModel.setSearchKey(it) },
        searchPlaceholder = "搜索",
        topBarActions = {
            IconButton(onClick = { showSheet = RemoteBookSheet.Servers }) {
                Icon(Icons.Default.Storage, contentDescription = "配置服务器")
            }
        },
        dropDownMenuContent = { dismiss ->
            RoundDropdownMenuItem(
                text = { Text("按名称排序") },
                onClick = {
                    viewModel.toggleSort(RemoteBookSort.Name)
                    dismiss()
                },
                trailingIcon = {
                    if (uiState.sortKey == RemoteBookSort.Name) {
                        Icon(Icons.Default.Check, null)
                    }
                }
            )
            RoundDropdownMenuItem(
                text = { Text("按时间排序") },
                onClick = {
                    viewModel.toggleSort(RemoteBookSort.Default)
                    dismiss()
                },
                trailingIcon = {
                    if (uiState.sortKey == RemoteBookSort.Default) {
                        Icon(Icons.Default.Check, null)
                    }
                }
            )
        },
        bottomContent = {
            PathNavigationBar(
                pathNames = uiState.pathNames,
                canGoBack = uiState.canGoBack,
                onNavigateBack = { viewModel.navigateBack() },
                onNavigateToLevel = { viewModel.navigateToLevel(it) }
            )
        },
        selectionActions = SelectionActions(
            onSelectAll = { viewModel.selectAllCheckable() },
            onSelectInvert = { viewModel.invertSelection() },
            primaryAction = ActionItem(
                text = "添加至书架",
                icon = { Icon(Icons.Default.CloudDownload, null) },
                onClick = {
                    val selectedBooks = uiState.items
                        .filter { it.id in uiState.selectedIds }
                        .map { it.remoteBook }
                        .toSet()
                    scope.launch { viewModel.addToBookshelf(selectedBooks) }
                }
            ),
            secondaryActions = emptyList()
        ),
        onAddClick = null,
    ) { paddingValues ->
        val pullToRefreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            isRefreshing = uiState.isLoading,
            state = pullToRefreshState,
            onRefresh = { viewModel.refreshData() }
        ) {
            if (uiState.items.isEmpty()) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    EmptyMessageView(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        message = "没有内容"
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.items, key = { it.id }) { itemUi ->
                        val book = itemUi.remoteBook
                        RemoteBookItem(
                            modifier = Modifier.animateItem(),
                            book = book,
                            isSelected = itemUi.id in uiState.selectedIds,
                            onClick = {
                                when {
                                    book.isDir -> viewModel.navigateToDir(book)
                                    book.isOnBookShelf -> startRead(book)
                                    else -> viewModel.toggleSelection(itemUi.id)
                                }
                            },
                            onAddClick = { remoteBook ->
                                scope.launch { viewModel.addToBookshelf(setOf(remoteBook)) }
                            },
                            onUpdateClick = { remoteBook ->
                                dialogState = RemoteBookDialog.ReImport(remoteBook)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServersSheetContent(
    servers: List<Server>,
    selectedServerId: Long,
    onSelect: (Long) -> Unit,
    onEdit: (Server) -> Unit,
    onDelete: (Server) -> Unit,
    onAdd: () -> Unit,
    onDefault: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.server_config),
                style = MaterialTheme.typography.titleLarge
            )
            IconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "添加")
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                ServerItem(
                    name = "默认",
                    url = "应用备份的 WebDav 配置",
                    isSelected = selectedServerId == AppConst.DEFAULT_WEBDAV_ID,
                    onClick = onDefault
                )
            }
            items(servers, key = { it.id }) { server ->
                ServerItem(
                    name = server.name,
                    url = server.getConfigJsonObject()?.optString("url"),
                    isSelected = server.id == selectedServerId,
                    onClick = { onSelect(server.id) },
                    onEdit = { onEdit(server) },
                    onDelete = { onDelete(server) }
                )
            }
        }
    }
}

@Composable
private fun ServerItem(
    name: String,
    url: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        )
    ) {
        ListItem(
            modifier = Modifier.animateContentSize(),
            headlineContent = {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingContent = {
                RadioButton(
                    selected = isSelected,
                    onClick = null
                )
            },
            supportingContent = url?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            trailingContent = if (onEdit != null || onDelete != null) {
                {
                    Row {
                        onEdit?.let {
                            IconButton(onClick = it) {
                                Icon(Icons.Default.Edit, contentDescription = "编辑")
                            }
                        }
                        onDelete?.let {
                            IconButton(onClick = it) {
                                Icon(Icons.Default.Delete, contentDescription = "删除")
                            }
                        }
                    }
                }
            } else null,
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun ServerConfigSheetContent(
    server: Server?,
    onSave: (Server) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(server?.name ?: "") }
    val configJson = remember { server?.getConfigJsonObject() ?: JSONObject() }
    var url by remember { mutableStateOf(configJson.optString("url")) }
    var username by remember { mutableStateOf(configJson.optString("username")) }
    var password by remember { mutableStateOf(configJson.optString("password")) }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = if (server == null) "添加服务器" else "编辑服务器",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("名称") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("用户名") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(android.R.string.cancel))
            }
            Button(
                onClick = {
                    val newServer = server?.copy(name = name) ?: Server(name = name)
                    val newConfig = JSONObject().apply {
                        put("url", url)
                        put("username", username)
                        put("password", password)
                    }
                    newServer.config = newConfig.toString()
                    onSave(newServer)
                },
                modifier = Modifier.weight(1f),
                enabled = name.isNotBlank() && url.isNotBlank()
            ) {
                Text("保存")
            }
        }
    }
}

@Composable
private fun PathNavigationBar(
    pathNames: List<String>,
    canGoBack: Boolean,
    onNavigateBack: () -> Unit,
    onNavigateToLevel: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(pathNames) { index, name ->
                    val isLast = index == pathNames.lastIndex

                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isLast)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .then(
                                if (!isLast)
                                    Modifier.clickable { onNavigateToLevel(index) }
                                else Modifier
                            )
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    )

                    if (!isLast) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        if (canGoBack) {
            SmallTonalIconButton(
                onClick = onNavigateBack,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回上级"
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RemoteBookItem(
    modifier: Modifier = Modifier,
    book: RemoteBook,
    isSelected: Boolean,
    onClick: () -> Unit,
    onAddClick: (RemoteBook) -> Unit,
    onUpdateClick: (RemoteBook) -> Unit
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                imageVector = when {
                    book.isDir -> Icons.Default.Folder
                    book.isOnBookShelf -> Icons.Outlined.Book
                    else -> Icons.Outlined.Description
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (book.isDir) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = book.filename.substringBeforeLast("."),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!book.isDir) {

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        if (book.contentType.isNotEmpty()) {
                            TextCard(
                                text = book.contentType.uppercase(),
                                textStyle = MaterialTheme.typography.labelSmall,
                                horizontalPadding = 4.dp,
                                verticalPadding = 2.dp,
                                cornerRadius = 4.dp,
                                icon = null,
                                backgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )

                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        Text(
                            text = "${ConvertUtils.formatFileSize(book.size)} • ${
                                AppConst.dateFormat.format(book.lastModify)
                            }",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!book.isDir) {
                Spacer(modifier = Modifier.width(8.dp))

                SmallIconButton(
                    onClick = {
                        if (book.isOnBookShelf) {
                            onUpdateClick(book)
                        } else {
                            onAddClick(book)
                        }
                    },
                    icon = if (book.isOnBookShelf)
                        Icons.Outlined.CloudSync
                    else
                        Icons.Outlined.AddCircleOutline,
                    contentDescription = if (book.isOnBookShelf) "更新" else "加入",
                )
            }
        }
    }
}