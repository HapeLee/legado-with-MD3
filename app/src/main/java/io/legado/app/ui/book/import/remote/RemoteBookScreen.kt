package io.legado.app.ui.book.import.remote

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.data.entities.Server
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.model.remote.RemoteBook
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.ActionItem
import io.legado.app.ui.widget.components.AppRadioButton
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.SelectionActions
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.button.MediumIconButton
import io.legado.app.ui.widget.components.button.SmallIconButton
import io.legado.app.ui.widget.components.button.SmallTonalIconButton
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.SelectionItemCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.list.ListScaffold
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.ConvertUtils
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.toastOnUi
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
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var dialogState by remember { mutableStateOf<RemoteBookDialog?>(null) }
    var showSheet by remember { mutableStateOf<RemoteBookSheet?>(null) }
    val selectDocTree = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        viewModel.dispatch(RemoteBookIntent.BookFolderPicked(uri))
    }

    AppAlertDialog(
        data = dialogState as? RemoteBookDialog.DownloadArchive,
        onDismissRequest = { dialogState = null },
        title = stringResource(R.string.draw),
        content = {
            AppText(stringResource(R.string.archive_not_found))
        },
        confirmText = stringResource(android.R.string.ok),
        onConfirm = { state ->
            viewModel.dispatch(RemoteBookIntent.AddBooks(setOf(state.remoteBook)))
            dialogState = null
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { dialogState = null }
    )

    AppAlertDialog(
        data = dialogState as? RemoteBookDialog.ReImport,
        onDismissRequest = { dialogState = null },
        title = "是否重新加入书架？",
        content = {
            AppText("将会覆盖书籍")
        },
        confirmText = stringResource(android.R.string.ok),
        onConfirm = { state ->
            viewModel.dispatch(RemoteBookIntent.AddBooks(setOf(state.remoteBook)))
            dialogState = null
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { dialogState = null }
    )

    AppModalBottomSheet(
        show = showSheet != null,
        onDismissRequest = { showSheet = null },
        title = when (val state = showSheet) {
            is RemoteBookSheet.Servers -> stringResource(R.string.server_config)
            is RemoteBookSheet.ServerConfig -> {
                val actionText =
                    if (state.server == null) stringResource(R.string.add)
                    else stringResource(R.string.edit)
                "$actionText ${stringResource(R.string.server_config)}"
            }
            else -> null
        },
        endAction = if (showSheet is RemoteBookSheet.Servers) {
            {
                MediumIconButton(
                    onClick = { showSheet = RemoteBookSheet.ServerConfig(null) },
                    imageVector = Icons.Default.Add
                )
            }
        } else {
            null
        }
    ) {
        when (val state = showSheet) {
            is RemoteBookSheet.Servers -> {
                ServersSheetContent(
                    servers = uiState.servers,
                    selectedServerId = uiState.selectedServerId,
                    onSelect = {
                        viewModel.dispatch(RemoteBookIntent.SelectServer(it))
                        showSheet = null
                    },
                    onEdit = { showSheet = RemoteBookSheet.ServerConfig(it) },
                    onDelete = { viewModel.deleteServer(it) },
                    onDefault = {
                        viewModel.dispatch(RemoteBookIntent.SelectServer(AppConst.DEFAULT_WEBDAV_ID))
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

    LaunchedEffect(viewModel) {
        viewModel.dispatch(RemoteBookIntent.Initialize)
        viewModel.effects.collect { effect ->
            when (effect) {
                is RemoteBookEffect.OpenBook -> context.startActivityForBook(effect.book)
                is RemoteBookEffect.RequestBookFolderPicker -> {
                    selectDocTree.launch(effect.initialUri)
                }

                is RemoteBookEffect.ShowArchiveEntries -> {
                    context.selector(R.string.start_read, effect.fileNames) { _, name, _ ->
                        viewModel.dispatch(
                            RemoteBookIntent.ArchiveEntrySelected(effect.fileDoc, name)
                        )
                    }
                }

                is RemoteBookEffect.ShowImportArchiveDialog -> {
                    context.alert(R.string.draw, R.string.no_book_found_bookshelf) {
                        okButton {
                            viewModel.dispatch(
                                RemoteBookIntent.ImportArchiveConfirmed(
                                    effect.fileDoc,
                                    effect.fileName
                                )
                            )
                        }
                        noButton()
                    }
                }

                is RemoteBookEffect.ShowDownloadArchiveDialog -> {
                    dialogState = RemoteBookDialog.DownloadArchive(effect.remoteBook)
                }

                is RemoteBookEffect.ShowToast -> context.toastOnUi(effect.message)
            }
        }
    }

    ListScaffold(
        title = "远程书籍",
        state = uiState,
        onBackClick = onBackClick,
        onSearchToggle = { viewModel.dispatch(RemoteBookIntent.SearchToggle(it)) },
        onSearchQueryChange = { viewModel.dispatch(RemoteBookIntent.SearchChange(it)) },
        searchPlaceholder = "搜索",
        topBarActions = {
            TopBarActionButton(
                onClick = { showSheet = RemoteBookSheet.Servers },
                imageVector = Icons.Default.Storage,
                contentDescription = "服务器"
            )
        },
        dropDownMenuContent = { dismiss ->
            RoundDropdownMenuItem(
                text = "按名称排序",
                onClick = {
                    viewModel.dispatch(RemoteBookIntent.SortToggle(RemoteBookSort.Name))
                    dismiss()
                },
                trailingIcon = {
                    if (uiState.sortKey == RemoteBookSort.Name) {
                        Icon(Icons.Default.Check, null)
                    }
                }
            )
            RoundDropdownMenuItem(
                text = "按时间排序",
                onClick = {
                    viewModel.dispatch(RemoteBookIntent.SortToggle(RemoteBookSort.Default))
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
                onNavigateBack = { viewModel.dispatch(RemoteBookIntent.NavigateBack) },
                onNavigateToLevel = { viewModel.dispatch(RemoteBookIntent.NavigateToLevel(it)) }
            )
        },
        selectionActions = SelectionActions(
            onClearSelection = { viewModel.clearSelection() },
            onSelectAll = { viewModel.dispatch(RemoteBookIntent.SelectAll) },
            onSelectInvert = { viewModel.dispatch(RemoteBookIntent.SelectInvert) },
            primaryAction = ActionItem(
                text = "添加至书架",
                icon = { Icon(Icons.Default.CloudDownload, null) },
                onClick = {
                    val selectedBooks = uiState.items
                        .filter { it.id in uiState.selectedIds }
                        .map { it.remoteBook }
                        .toSet()
                    viewModel.dispatch(RemoteBookIntent.AddBooks(selectedBooks))
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
            onRefresh = { viewModel.dispatch(RemoteBookIntent.Refresh) }
        ) {
            if (uiState.items.isEmpty()) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    EmptyMessage(
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
                                viewModel.dispatch(RemoteBookIntent.OpenItem(book))
                            },
                            onAddClick = { remoteBook ->
                                viewModel.dispatch(RemoteBookIntent.AddBooks(setOf(remoteBook)))
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
    onDefault: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 8.dp)
    ) {
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
    SelectionItemCard(
        title = name,
        subtitle = url,
        isSelected = isSelected,
        onToggleSelection = onClick,
        leadingContent = {
            AppRadioButton(
                selected = isSelected,
                onClick = null
            )
        },
        trailingAction = if (onEdit != null || onDelete != null) {
            {
                Row {
                    onEdit?.let {
                        SmallIconButton(
                            onClick = it,
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit"
                        )
                    }
                    onDelete?.let {
                        SmallIconButton(
                            onClick = it,
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete"
                        )
                    }
                }
            }
        } else {
            null
        },
        containerColor = LegadoTheme.colorScheme.onSheetContent,
        selectedContainerColor = LegadoTheme.colorScheme.secondaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
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
            .verticalScroll(rememberScrollState())
    ) {
        AppTextField(
            value = name,
            onValueChange = { name = it },
            backgroundColor = LegadoTheme.colorScheme.onSheetContent,
            label = "名称",
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        AppTextField(
            value = url,
            onValueChange = { url = it },
            backgroundColor = LegadoTheme.colorScheme.onSheetContent,
            label = "URL",
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        AppTextField(
            value = username,
            onValueChange = { username = it },
            backgroundColor = LegadoTheme.colorScheme.onSheetContent,
            label = "用户名",
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        AppTextField(
            value = password,
            onValueChange = { password = it },
            backgroundColor = LegadoTheme.colorScheme.onSheetContent,
            label = "密码",
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

        ConfirmDismissButtonsRow(
            modifier = Modifier.fillMaxWidth(),
            onDismiss = onCancel,
            onConfirm = {
                val newServer = server?.copy(name = name) ?: Server(name = name)
                val newConfig = JSONObject().apply {
                    put("url", url)
                    put("username", username)
                    put("password", password)
                }
                newServer.config = newConfig.toString()
                onSave(newServer)
            },
            dismissText = stringResource(android.R.string.cancel),
            confirmText = "保存",
            confirmEnabled = name.isNotBlank() && url.isNotBlank()
        )
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
        GlassCard(
            modifier = Modifier.weight(1f),
            containerColor = LegadoTheme.colorScheme.surfaceContainer,
            cornerRadius = 12.dp,
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(pathNames) { index, name ->
                    val isLast = index == pathNames.lastIndex

                    AppText(
                        text = name,
                        style = LegadoTheme.typography.labelSmall,
                        fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isLast)
                            LegadoTheme.colorScheme.primary
                        else
                            LegadoTheme.colorScheme.onSurfaceVariant,
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
                            tint = LegadoTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        if (canGoBack) {
            SmallTonalIconButton(
                onClick = onNavigateBack,
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
        LegadoTheme.colorScheme.secondaryContainer
    } else {
        LegadoTheme.colorScheme.surfaceContainer
    }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick,
        containerColor = containerColor
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
                    LegadoTheme.colorScheme.primary
                } else {
                    LegadoTheme.colorScheme.onSurfaceVariant
                }
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {

                AppText(
                    text = book.filename.substringBeforeLast("."),
                    style = LegadoTheme.typography.bodyMedium,
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
                                textStyle = LegadoTheme.typography.labelSmall,
                                horizontalPadding = 4.dp,
                                verticalPadding = 2.dp,
                                cornerRadius = 4.dp,
                                icon = null,
                                backgroundColor = LegadoTheme.colorScheme.surfaceContainerHighest
                            )

                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        AppText(
                            text = "${ConvertUtils.formatFileSize(book.size)} • ${
                                AppConst.dateFormat.format(book.lastModify)
                            }",
                            style = LegadoTheme.typography.labelMedium,
                            color = LegadoTheme.colorScheme.onSurfaceVariant
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
                    imageVector = if (book.isOnBookShelf)
                        Icons.Outlined.CloudSync
                    else
                        Icons.Outlined.AddCircleOutline,
                    contentDescription = if (book.isOnBookShelf) "更新" else "加入",
                )
            }
        }
    }
}
