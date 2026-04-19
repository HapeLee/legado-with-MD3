package io.legado.app.ui.book.import.remote

import android.app.Application
import androidx.core.net.toUri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.Server
import io.legado.app.data.repository.RemoteBookRepository
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.AppWebDav
import io.legado.app.help.config.AppConfig
import io.legado.app.model.analyzeRule.CustomUrl
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.remote.RemoteBook
import io.legado.app.model.remote.RemoteBookWebDav
import io.legado.app.ui.config.importBookConfig.ImportBookConfig
import io.legado.app.ui.widget.components.list.InteractionState
import io.legado.app.ui.widget.components.list.ListUiState
import io.legado.app.ui.widget.components.list.SelectableItem
import io.legado.app.utils.AlphanumComparator
import io.legado.app.utils.ArchiveUtils
import io.legado.app.utils.ConvertUtils
import io.legado.app.utils.FileDoc
import io.legado.app.utils.find
import io.legado.app.utils.takePersistablePermissionSafely
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class NavigationState(
    val pathNames: List<String> = emptyList(),
    val canGoBack: Boolean = false
)

@Immutable
data class RemoteBookItemUi(
    val remoteBook: RemoteBook,
    val nameWithoutExtension: String,
    val formattedSize: String,
    val formattedDate: String,
    override val id: String = remoteBook.path
) : SelectableItem<String>

@Immutable
data class RemoteBookUiState(
    override val items: List<RemoteBookItemUi> = emptyList(),
    override val selectedIds: Set<Any> = emptySet(),
    override val searchKey: String = "",
    val interaction: InteractionState = InteractionState(isLoading = true),
    val navigation: NavigationState = NavigationState(),
    val sortKey: RemoteBookSort = RemoteBookSort.Default,
    val sortAscending: Boolean = false,
    val servers: List<Server> = emptyList(),
    val selectedServerId: Long = ImportBookConfig.remoteServerId
) : ListUiState<RemoteBookItemUi> {
    override val isSearch: Boolean get() = interaction.isSearchMode
    override val isLoading: Boolean get() = interaction.isLoading
    val pathNames: List<String> get() = navigation.pathNames
    val canGoBack: Boolean get() = navigation.canGoBack
}

sealed interface RemoteBookIntent {
    data object Initialize : RemoteBookIntent
    data object Refresh : RemoteBookIntent
    data class SearchToggle(val enabled: Boolean) : RemoteBookIntent
    data class SearchChange(val query: String) : RemoteBookIntent
    data class SortToggle(val sort: RemoteBookSort) : RemoteBookIntent
    data object SelectAll : RemoteBookIntent
    data object SelectInvert : RemoteBookIntent
    data class ToggleSelection(val id: String) : RemoteBookIntent
    data class OpenItem(val book: RemoteBook) : RemoteBookIntent
    data object NavigateBack : RemoteBookIntent
    data class NavigateToLevel(val level: Int) : RemoteBookIntent
    data class NavigateToDir(val book: RemoteBook) : RemoteBookIntent
    data class AddBooks(val books: Set<RemoteBook>) : RemoteBookIntent
    data class SelectServer(val serverId: Long) : RemoteBookIntent
    data class BookFolderPicked(val uri: android.net.Uri?) : RemoteBookIntent
    data class ArchiveEntrySelected(val fileDoc: FileDoc, val fileName: String) : RemoteBookIntent
    data class ImportArchiveConfirmed(val fileDoc: FileDoc, val fileName: String) : RemoteBookIntent
}

sealed interface RemoteBookEffect {
    data class RequestBookFolderPicker(val initialUri: android.net.Uri? = null) : RemoteBookEffect
    data class OpenBook(val book: Book) : RemoteBookEffect
    data class ShowArchiveEntries(val fileDoc: FileDoc, val fileNames: List<String>) : RemoteBookEffect
    data class ShowImportArchiveDialog(val fileDoc: FileDoc, val fileName: String) : RemoteBookEffect
    data class ShowDownloadArchiveDialog(val remoteBook: RemoteBook) : RemoteBookEffect
    data class ShowToast(val message: String) : RemoteBookEffect
}

class RemoteBookViewModel(
    application: Application,
    @Suppress("UNUSED_PARAMETER")
    private val savedStateHandle: SavedStateHandle,
    private val repository: RemoteBookRepository
) : BaseViewModel(application) {

    private data class InternalState(
        val remoteBooks: List<RemoteBook> = emptyList(),
        val sortKey: RemoteBookSort = RemoteBookSort.Default,
        val sortAscending: Boolean = false,
        val dirList: List<RemoteBook> = emptyList(),
        val interaction: InteractionState = InteractionState(isLoading = true),
        val searchKey: String = "",
        val selectedIds: Set<String> = emptySet(),
        val remoteBookWebDav: RemoteBookWebDav? = null,
        val isDefaultWebdav: Boolean = false,
        val selectedServerId: Long = ImportBookConfig.remoteServerId
    )

    private val _state = MutableStateFlow(InternalState())
    private val _effects = MutableSharedFlow<RemoteBookEffect>(extraBufferCapacity = 1)
    val effects = _effects.asSharedFlow()

    fun dispatch(intent: RemoteBookIntent) {
        when (intent) {
            RemoteBookIntent.Initialize -> initData { loadRemoteBookList() }
            RemoteBookIntent.Refresh -> refreshData()
            is RemoteBookIntent.SearchToggle -> setSearchMode(intent.enabled)
            is RemoteBookIntent.SearchChange -> setSearchKey(intent.query)
            is RemoteBookIntent.SortToggle -> toggleSort(intent.sort)
            RemoteBookIntent.SelectAll -> selectAllCheckable()
            RemoteBookIntent.SelectInvert -> invertSelection()
            is RemoteBookIntent.ToggleSelection -> toggleSelection(intent.id)
            is RemoteBookIntent.OpenItem -> onOpenItem(intent.book)
            RemoteBookIntent.NavigateBack -> navigateBack()
            is RemoteBookIntent.NavigateToLevel -> navigateToLevel(intent.level)
            is RemoteBookIntent.NavigateToDir -> navigateToDir(intent.book)
            is RemoteBookIntent.AddBooks -> addBooksToShelf(intent.books)
            is RemoteBookIntent.SelectServer -> selectServer(intent.serverId)
            is RemoteBookIntent.BookFolderPicked -> onBookFolderPicked(intent.uri)
            is RemoteBookIntent.ArchiveEntrySelected -> onArchiveEntrySelected(
                intent.fileDoc,
                intent.fileName
            )

            is RemoteBookIntent.ImportArchiveConfirmed -> addArchiveToBookShelf(
                intent.fileDoc,
                intent.fileName
            )
        }
    }

    private fun onBookFolderPicked(uri: android.net.Uri?) {
        uri ?: return
        uri.takePersistablePermissionSafely(context)
        AppConfig.defaultBookTreeUri = uri.toString()
    }

    val uiState: StateFlow<RemoteBookUiState> = combine(
        _state,
        repository.flowLocalBooks(),
        appDb.serverDao.observeAll()
    ) { state, localBooks, servers ->
        val localFileNames = localBooks.map { it.originName }.toSet()

        val sortedBooks = sortBooks(state.remoteBooks, state.sortKey, state.sortAscending)
        val filteredBooks = if (state.searchKey.isBlank()) {
            sortedBooks
        } else {
            sortedBooks.filter { it.filename.contains(state.searchKey, ignoreCase = true) }
        }

        RemoteBookUiState(
            items = filteredBooks.map { book ->
                val isOnShelf = if (book.isDir) false else localFileNames.contains(book.filename)
                val updatedBook =
                    if (book.isOnBookShelf != isOnShelf) book.copy(isOnBookShelf = isOnShelf) else book
                RemoteBookItemUi(
                    remoteBook = updatedBook,
                    nameWithoutExtension = book.filename.substringBeforeLast("."),
                    formattedSize = ConvertUtils.formatFileSize(book.size),
                    formattedDate = AppConst.dateFormat.format(book.lastModify)
                )
            },
            selectedIds = state.selectedIds,
            searchKey = state.searchKey,
            interaction = state.interaction,
            navigation = NavigationState(
                pathNames = listOf(if (state.isDefaultWebdav) "books" else "/").plus(state.dirList.map { it.filename }),
                canGoBack = state.dirList.isNotEmpty()
            ),
            sortKey = state.sortKey,
            sortAscending = state.sortAscending,
            servers = servers,
            selectedServerId = state.selectedServerId
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = RemoteBookUiState()
        )

    fun initData(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val webDav = repository.createWebDav(ImportBookConfig.remoteServerId)
                if (webDav != null) {
                    _state.update {
                        it.copy(
                            remoteBookWebDav = webDav,
                            isDefaultWebdav = false,
                            selectedServerId = ImportBookConfig.remoteServerId
                        )
                    }
                    onSuccess()
                } else {
                    val defaultWebDav = AppWebDav.defaultBookWebDav
                        ?: throw NoStackTraceException("webDav没有配置")
                    _state.update {
                        it.copy(
                            remoteBookWebDav = defaultWebDav,
                            isDefaultWebdav = true,
                            selectedServerId = AppConst.DEFAULT_WEBDAV_ID
                        )
                    }
                    onSuccess()
                }
            } catch (e: Exception) {
                context.toastOnUi("初始化webDav出错:${e.localizedMessage}")
                _state.update { it.copy(interaction = it.interaction.copy(isLoading = false)) }
            }
        }
    }

    fun loadRemoteBookList(path: String? = null) {
        _state.update { it.copy(interaction = it.interaction.copy(isLoading = true)) }
        viewModelScope.launch {
            try {
                val webDav = _state.value.remoteBookWebDav
                    ?: throw NoStackTraceException("没有配置webDav")
                val bookList = withContext(Dispatchers.IO) {
                    repository.loadBooks(webDav, path)
                }
                _state.update { it.copy(remoteBooks = bookList) }
            } catch (e: Exception) {
                AppLog.put("获取webDav书籍出错\n${e.localizedMessage}", e)
                context.toastOnUi("获取webDav书籍出错\n${e.localizedMessage}")
            } finally {
                _state.update { it.copy(interaction = it.interaction.copy(isLoading = false)) }
            }
        }
    }

    suspend fun addToBookshelf(remoteBooks: Set<RemoteBook>): Result<Unit> {
        _state.update { it.copy(interaction = it.interaction.copy(isUploading = true)) }
        return try {
            val webDav = _state.value.remoteBookWebDav
                ?: throw NoStackTraceException("没有配置webDav")

            withContext(Dispatchers.IO) {
                remoteBooks.forEach { remoteBook ->
                    val downloadBookUri = repository.downloadBook(webDav, remoteBook)
                    LocalBook.importFiles(downloadBookUri).forEach { book ->
                        book.origin = BookType.webDavTag + CustomUrl(remoteBook.path)
                            .putAttribute("serverID", webDav.serverID)
                            .toString()
                        book.save()
                    }
                }
            }

            _state.update { it.copy(selectedIds = emptySet()) }
            Result.success(Unit)
        } catch (e: SecurityException) {
            _effects.emit(RemoteBookEffect.RequestBookFolderPicker())
            Result.failure(e)
        } catch (e: Exception) {
            AppLog.put("导入出错\n${e.localizedMessage}", e)
            context.toastOnUi("导入出错\n${e.localizedMessage}")
            Result.failure(e)
        } finally {
            _state.update { it.copy(interaction = it.interaction.copy(isUploading = false)) }
        }
    }

    private fun addBooksToShelf(remoteBooks: Set<RemoteBook>) {
        viewModelScope.launch {
            addToBookshelf(remoteBooks)
        }
    }

    suspend fun getLocalBook(fileName: String): Book? {
        return withContext(Dispatchers.IO) {
            appDb.bookDao.getBookByFileName(fileName)
        }
    }

    fun navigateToDir(book: RemoteBook) {
        _state.update { it.copy(dirList = it.dirList + book, selectedIds = emptySet()) }
        loadRemoteBookList(book.path)
    }

    fun navigateBack() {
        val lastPath = _state.value.dirList.let {
            if (it.size > 1) it[it.size - 2].path else null
        }
        _state.update {
            if (it.dirList.isNotEmpty()) {
                it.copy(dirList = it.dirList.dropLast(1), selectedIds = emptySet())
            } else it
        }
        loadRemoteBookList(lastPath)
    }

    fun navigateToLevel(index: Int) {
        if (index < 0) return
        val currentDirList = _state.value.dirList
        if (index > currentDirList.size) return

        if (index == 0) {
            _state.update { it.copy(dirList = emptyList(), selectedIds = emptySet()) }
            loadRemoteBookList(null)
        } else {
            val newDirList = currentDirList.take(index)
            val targetPath = newDirList.last().path
            _state.update { it.copy(dirList = newDirList, selectedIds = emptySet()) }
            loadRemoteBookList(targetPath)
        }
    }

    fun setSearchMode(isSearch: Boolean) {
        _state.update {
            it.copy(
                interaction = it.interaction.copy(isSearchMode = isSearch),
                searchKey = if (isSearch) it.searchKey else ""
            )
        }
    }

    fun setSearchKey(key: String) {
        _state.update { it.copy(searchKey = key) }
    }

    fun updateSort(sortKey: RemoteBookSort, ascending: Boolean) {
        _state.update { it.copy(sortKey = sortKey, sortAscending = ascending) }
    }

    fun toggleSort(sortKey: RemoteBookSort) {
        _state.update { state ->
            if (state.sortKey == sortKey) {
                state.copy(sortAscending = !state.sortAscending)
            } else {
                state.copy(sortKey = sortKey, sortAscending = true)
            }
        }
    }

    fun toggleSelection(id: String) {
        _state.update {
            val newSelected = if (id in it.selectedIds) it.selectedIds - id else it.selectedIds + id
            it.copy(selectedIds = newSelected)
        }
    }

    fun selectAllCheckable() {
        _state.update { state ->
            val checkableIds = state.remoteBooks
                .filter { !it.isDir && !it.isOnBookShelf }
                .map { it.path }
                .toSet()
            state.copy(selectedIds = checkableIds)
        }
    }

    fun invertSelection() {
        _state.update { state ->
            val checkableIds = state.remoteBooks
                .filter { !it.isDir && !it.isOnBookShelf }
                .map { it.path }
                .toSet()
            state.copy(selectedIds = checkableIds - state.selectedIds)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedIds = emptySet()) }
    }

    fun refreshData() {
        val currentPath = _state.value.dirList.lastOrNull()?.path
        loadRemoteBookList(currentPath)
    }

    private fun onOpenItem(remoteBook: RemoteBook) {
        if (remoteBook.isDir) {
            navigateToDir(remoteBook)
            return
        }
        if (!remoteBook.isOnBookShelf) {
            toggleSelection(remoteBook.path)
            return
        }
        viewModelScope.launch {
            val downloadFileName = remoteBook.filename
            if (!ArchiveUtils.isArchive(downloadFileName)) {
                getLocalBook(downloadFileName)?.let {
                    _effects.emit(RemoteBookEffect.OpenBook(it))
                }
                return@launch
            }

            val bookTreeUri = AppConfig.defaultBookTreeUri
            if (bookTreeUri == null) {
                _effects.emit(RemoteBookEffect.RequestBookFolderPicker())
                return@launch
            }

            val downloadArchiveFileDoc = withContext(Dispatchers.IO) {
                FileDoc.fromUri(bookTreeUri.toUri(), true)
                    .find(downloadFileName)
            }
            if (downloadArchiveFileDoc == null) {
                _effects.emit(RemoteBookEffect.ShowDownloadArchiveDialog(remoteBook))
            } else {
                onArchiveFileClick(downloadArchiveFileDoc)
            }
        }
    }

    private suspend fun onArchiveFileClick(fileDoc: FileDoc) {
        val fileNames = ArchiveUtils.getArchiveFilesName(fileDoc) {
            it.matches(io.legado.app.constant.AppPattern.bookFileRegex)
        }
        when {
            fileNames.isEmpty() -> _effects.emit(
                RemoteBookEffect.ShowToast(context.getString(io.legado.app.R.string.unsupport_archivefile_entry))
            )

            fileNames.size == 1 -> onArchiveEntrySelected(fileDoc, fileNames.first())
            else -> _effects.emit(RemoteBookEffect.ShowArchiveEntries(fileDoc, fileNames))
        }
    }

    private fun onArchiveEntrySelected(fileDoc: FileDoc, fileName: String) {
        appDb.bookDao.getBookByFileName(fileName)?.let { book ->
            viewModelScope.launch { _effects.emit(RemoteBookEffect.OpenBook(book)) }
        } ?: viewModelScope.launch {
            _effects.emit(RemoteBookEffect.ShowImportArchiveDialog(fileDoc, fileName))
        }
    }

    private fun addArchiveToBookShelf(fileDoc: FileDoc, fileName: String) {
        execute {
            LocalBook.importArchiveFile(fileDoc.uri, fileName) { it.contains(fileName) }
                .firstOrNull()
        }.onSuccess { book ->
            if (book != null) {
                viewModelScope.launch { _effects.emit(RemoteBookEffect.OpenBook(book)) }
            } else {
                viewModelScope.launch {
                    _effects.emit(RemoteBookEffect.ShowToast(context.getString(io.legado.app.R.string.error)))
                }
            }
        }.onError {
            viewModelScope.launch {
                _effects.emit(RemoteBookEffect.ShowToast(context.getString(io.legado.app.R.string.error)))
            }
        }
    }

    private fun sortBooks(
        books: List<RemoteBook>,
        sortKey: RemoteBookSort,
        ascending: Boolean
    ): List<RemoteBook> {
        return books.sortedWith { o1, o2 ->
            val dirCompare = compareValues(o1.isDir, o2.isDir)
            if (dirCompare != 0) return@sortedWith -dirCompare

            val result = when (sortKey) {
                RemoteBookSort.Name -> AlphanumComparator.compare(o1.filename, o2.filename)
                else -> compareValues(o1.lastModify, o2.lastModify)
            }
            if (ascending) result else -result
        }
    }

    fun saveServer(server: Server) {
        execute {
            appDb.serverDao.insert(server)
        }.onSuccess {
            if (ImportBookConfig.remoteServerId == server.id) {
                initData { loadRemoteBookList() }
            }
        }
    }

    fun deleteServer(server: Server) {
        execute {
            appDb.serverDao.delete(server)
        }
    }

    fun selectServer(serverId: Long) {
        ImportBookConfig.remoteServerId = serverId
        _state.update { it.copy(selectedServerId = serverId, dirList = emptyList()) }
        initData { loadRemoteBookList() }
    }
}
