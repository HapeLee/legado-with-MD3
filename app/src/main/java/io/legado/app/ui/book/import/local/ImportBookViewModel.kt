package io.legado.app.ui.book.import.local

import io.legado.app.ui.config.otherConfig.OtherConfig
import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.getTreeDocumentId
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.constant.AppPattern.archiveFileRegex
import io.legado.app.constant.AppPattern.bookFileRegex
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.model.localBook.LocalBook
import io.legado.app.ui.config.importBookConfig.ImportBookConfig
import io.legado.app.ui.widget.components.list.InteractionState
import io.legado.app.ui.widget.components.list.ListUiState
import io.legado.app.utils.AlphanumComparator
import io.legado.app.utils.ArchiveUtils
import io.legado.app.utils.FileDoc
import io.legado.app.utils.delete
import io.legado.app.utils.exists
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.isUri
import io.legado.app.utils.list
import io.legado.app.utils.mapParallel
import io.legado.app.utils.takePersistablePermissionSafely
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Immutable
data class ImportBookUiState(
    override val items: List<ImportBook> = emptyList(),
    override val selectedIds: Set<Any> = emptySet(),
    override val searchKey: String = "",
    val interaction: InteractionState = InteractionState(),
    val pathNames: List<String> = emptyList(),
    val canGoBack: Boolean = false,
    val sort: Int = 0
) : ListUiState<ImportBook> {
    override val isSearch: Boolean get() = interaction.isSearchMode
    override val isLoading: Boolean get() = interaction.isLoading
}

enum class ImportFolderPickTarget {
    DEFAULT_BOOK,
    IMPORT_FOLDER
}

sealed interface ImportBookIntent {
    data object Initialize : ImportBookIntent
    data object SelectFolderClick : ImportBookIntent
    data class FolderPicked(val uri: Uri?, val target: ImportFolderPickTarget) : ImportBookIntent
    data class SearchToggle(val enabled: Boolean) : ImportBookIntent
    data class SearchQueryChange(val query: String) : ImportBookIntent
    data class SortChange(val sort: Int) : ImportBookIntent
    data object ScanFolder : ImportBookIntent
    data object NavigateBack : ImportBookIntent
    data class NavigateToLevel(val level: Int) : ImportBookIntent
    data object SelectAll : ImportBookIntent
    data object SelectInvert : ImportBookIntent
    data object AddToBookshelf : ImportBookIntent
    data class AddSingleToBookshelf(val item: ImportBook) : ImportBookIntent
    data object DeleteSelection : ImportBookIntent
    data class ItemClick(val item: ImportBook) : ImportBookIntent
    data class ArchiveEntrySelected(val fileDoc: FileDoc, val fileName: String) : ImportBookIntent
    data class ImportArchiveConfirmed(val fileDoc: FileDoc, val fileName: String) : ImportBookIntent
}

sealed interface ImportBookEffect {
    data class RequestFolderPicker(
        val target: ImportFolderPickTarget,
        val initialUri: Uri? = null
    ) : ImportBookEffect

    data class OpenBook(val book: Book) : ImportBookEffect
    data class ShowArchiveEntries(val fileDoc: FileDoc, val fileNames: List<String>) : ImportBookEffect
    data class ShowImportArchiveDialog(val fileDoc: FileDoc, val fileName: String) : ImportBookEffect
    data class ShowToastRes(val resId: Int) : ImportBookEffect
}

class ImportBookViewModel(application: Application) : BaseViewModel(application) {

    private enum class SourceMode {
        CURRENT_DIR,
        SCAN_RECURSIVE
    }

    private data class InternalState(
        val rootDoc: FileDoc? = null,
        val subDocs: List<FileDoc> = emptyList(),
        val sourceDocs: List<FileDoc> = emptyList(),
        val selectedIds: Set<String> = emptySet(),
        val searchKey: String = "",
        val sort: Int,
        val sourceMode: SourceMode = SourceMode.CURRENT_DIR,
        val interaction: InteractionState = InteractionState()
    )

    private val _state = MutableStateFlow(
        InternalState(sort = ImportBookConfig.localBookImportSort)
    )
    private val _effects = MutableSharedFlow<ImportBookEffect>(extraBufferCapacity = 1)
    val effects = _effects.asSharedFlow()

    private var scanDocJob: Job? = null
    private var autoSyncJob: Job? = null

    fun dispatch(intent: ImportBookIntent) {
        when (intent) {
            ImportBookIntent.Initialize -> initialize()
            ImportBookIntent.SelectFolderClick -> {
                _effects.tryEmit(
                    ImportBookEffect.RequestFolderPicker(
                        target = ImportFolderPickTarget.IMPORT_FOLDER,
                        initialUri = ImportBookConfig.importBookPath
                            ?.takeIf { it.isUri() }
                            ?.toUri()
                    )
                )
            }

            is ImportBookIntent.FolderPicked -> onFolderPicked(intent.uri, intent.target)
            is ImportBookIntent.SearchToggle -> setSearchMode(intent.enabled)
            is ImportBookIntent.SearchQueryChange -> setSearchKey(intent.query)
            is ImportBookIntent.SortChange -> setSort(intent.sort)
            ImportBookIntent.ScanFolder -> scanCurrentDoc()
            ImportBookIntent.NavigateBack -> navigateBack()
            is ImportBookIntent.NavigateToLevel -> navigateToLevel(intent.level)
            ImportBookIntent.SelectAll -> selectAllCheckable()
            ImportBookIntent.SelectInvert -> invertSelection()
            ImportBookIntent.AddToBookshelf -> addSelectedToBookshelf()
            is ImportBookIntent.AddSingleToBookshelf -> addSingleToBookshelf(intent.item)
            ImportBookIntent.DeleteSelection -> deleteSelectedDocs()
            is ImportBookIntent.ItemClick -> onItemClick(intent.item)
            is ImportBookIntent.ArchiveEntrySelected -> onArchiveEntrySelected(
                intent.fileDoc,
                intent.fileName
            )

            is ImportBookIntent.ImportArchiveConfirmed -> addArchiveToBookShelf(
                intent.fileDoc,
                intent.fileName
            )
        }
    }

    val uiState = combine(
        _state,
        appDb.bookDao.flowLocal()
    ) { state, localBooks ->
        val localFileNames = localBooks.asSequence().map { it.originName }.toSet()

        val docs = state.sourceDocs.map { fileDoc ->
            ImportBook(
                file = fileDoc,
                isOnBookShelf = !fileDoc.isDir && fileDoc.name in localFileNames
            )
        }

        val filteredDocs = if (state.searchKey.isBlank()) {
            docs
        } else {
            docs.filter { it.name.contains(state.searchKey, ignoreCase = true) }
        }

        val comparator = when (state.sort) {
            2 -> compareBy<ImportBook>({ !it.isDir }, { -it.lastModified })
            1 -> compareBy({ !it.isDir }, { -it.size })
            else -> compareBy { !it.isDir }
        } then compareBy(AlphanumComparator) { it.name }

        val sortedDocs = filteredDocs.sortedWith(comparator)
        val checkableIds = sortedDocs
            .asSequence()
            .filter { !it.isDir && !it.isOnBookShelf }
            .map { it.selectionId }
            .toSet()

        val selectedIds = state.selectedIds.filterTo(hashSetOf()) { it in checkableIds }
        val pathNames = state.rootDoc?.let { root ->
            buildList {
                add(root.name)
                addAll(state.subDocs.map { it.name })
            }
        } ?: emptyList()

        ImportBookUiState(
            items = sortedDocs,
            selectedIds = selectedIds,
            searchKey = state.searchKey,
            interaction = state.interaction,
            pathNames = pathNames,
            canGoBack = state.subDocs.isNotEmpty(),
            sort = state.sort
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ImportBookUiState()
        )

    fun hasRootDoc(): Boolean = _state.value.rootDoc != null

    private fun initialize() {
        val defaultPath = OtherConfig.defaultBookTreeUri?.takeIf { it.isUri() }
        val effectiveDefaultPath = defaultPath ?: firstPersistedTreeUri()?.toString()
        if (effectiveDefaultPath.isNullOrBlank()) {
            _effects.tryEmit(
                ImportBookEffect.RequestFolderPicker(target = ImportFolderPickTarget.DEFAULT_BOOK)
            )
            return
        }
        if (OtherConfig.defaultBookTreeUri != effectiveDefaultPath) {
            OtherConfig.defaultBookTreeUri = effectiveDefaultPath
        }
        val importPath = ImportBookConfig.importBookPath
        if (importPath.isNullOrBlank() || !importPath.isUri()) {
            ImportBookConfig.importBookPath = effectiveDefaultPath
        }
        initRootDoc(changedFolder = true)
        startAutoSync()
    }

    private fun onFolderPicked(uri: Uri?, target: ImportFolderPickTarget) {
        uri ?: return
        val pickedUri = persistFolderPermission(uri)
        when (target) {
            ImportFolderPickTarget.DEFAULT_BOOK -> {
                OtherConfig.defaultBookTreeUri = pickedUri.toString()
                if (ImportBookConfig.importBookPath.isNullOrBlank()) {
                    ImportBookConfig.importBookPath = OtherConfig.defaultBookTreeUri
                }
            }

            ImportFolderPickTarget.IMPORT_FOLDER -> {
                ImportBookConfig.importBookPath = pickedUri.toString()
                if (OtherConfig.defaultBookTreeUri.isNullOrBlank()) {
                    OtherConfig.defaultBookTreeUri = pickedUri.toString()
                }
            }
        }
        initRootDoc(changedFolder = true)
        startAutoSync()
    }

    private fun persistFolderPermission(uri: Uri): Uri {
        val normalizedUri = normalizeFolderUri(uri)
        uri.takePersistablePermissionSafely(context)
        if (normalizedUri != uri) {
            normalizedUri.takePersistablePermissionSafely(context)
        }
        return normalizedUri
    }

    private fun normalizeFolderUri(uri: Uri): Uri {
        if (!uri.isContentScheme()) return uri
        return runCatching {
            DocumentsContract.buildTreeDocumentUri(uri.authority, getTreeDocumentId(uri))
        }.recoverCatching {
            DocumentsContract.buildTreeDocumentUri(
                uri.authority,
                DocumentsContract.getDocumentId(uri)
            )
        }.getOrDefault(uri)
    }

    private fun initRootDoc(changedFolder: Boolean) {
        if (hasRootDoc() && !changedFolder) {
            reloadCurrentDoc()
            return
        }
        val candidates = linkedSetOf<String>().apply {
            ImportBookConfig.importBookPath
                ?.takeIf { it.isNotBlank() && it.isUri() }
                ?.let(::add)
            OtherConfig.defaultBookTreeUri
                ?.takeIf { it.isNotBlank() && it.isUri() }
                ?.let(::add)
        }
        if (candidates.isEmpty()) {
            firstPersistedTreeUri()?.toString()?.let { persistedPath ->
                if (trySetRootDoc(persistedPath)) {
                    OtherConfig.defaultBookTreeUri = persistedPath
                    ImportBookConfig.importBookPath = persistedPath
                    return
                }
            }
            _effects.tryEmit(
                ImportBookEffect.RequestFolderPicker(target = ImportFolderPickTarget.DEFAULT_BOOK)
            )
            return
        }

        for (path in candidates) {
            if (trySetRootDoc(path)) {
                if (ImportBookConfig.importBookPath != path) {
                    ImportBookConfig.importBookPath = path
                }
                return
            }
            val persistedUri = path.takeIf { it.isUri() }
                ?.toUri()
                ?.let(::findPersistedTreeUri)
            if (persistedUri != null) {
                val persistedPath = persistedUri.toString()
                if (trySetRootDoc(persistedPath)) {
                    ImportBookConfig.importBookPath = persistedPath
                    return
                }
            }
        }

        firstPersistedTreeUri()?.toString()?.let { persistedPath ->
            if (trySetRootDoc(persistedPath)) {
                OtherConfig.defaultBookTreeUri = persistedPath
                ImportBookConfig.importBookPath = persistedPath
                return
            }
        }

        _effects.tryEmit(
            ImportBookEffect.RequestFolderPicker(target = ImportFolderPickTarget.DEFAULT_BOOK)
        )
    }

    private fun trySetRootDoc(path: String): Boolean {
        val rootUri = if (path.isUri()) path.toUri() else Uri.fromFile(File(path))
        return kotlin.runCatching {
            if (rootUri.isContentScheme()) {
                val doc = DocumentFile.fromTreeUri(context, rootUri)
                    ?: DocumentFile.fromSingleUri(context, rootUri)
                    ?: return false
                val rootName = doc.name
                    ?.takeIf { it.isNotBlank() }
                    ?: runCatching {
                        getTreeDocumentId(rootUri).substringAfter(':').ifBlank { "root" }
                    }.getOrElse { "root" }
                setRootDoc(
                    FileDoc(
                        name = rootName,
                        isDir = true,
                        size = doc.length(),
                        lastModified = doc.lastModified(),
                        uri = doc.uri
                    )
                )
            } else {
                val file = File(rootUri.path ?: return false)
                if (!file.exists() || !file.isDirectory) return false
                setRootDoc(FileDoc.fromFile(file))
            }
            true
        }.getOrDefault(false)
    }

    private fun findPersistedTreeUri(targetUri: Uri): Uri? {
        if (!targetUri.isContentScheme()) return null
        val permissions = context.contentResolver.persistedUriPermissions
        if (permissions.isEmpty()) return null
        val targetAuthority = targetUri.authority
        val targetTreeId = runCatching { getTreeDocumentId(targetUri) }.getOrNull()

        return permissions.firstOrNull { permission ->
            permission.isReadPermission
                    && permission.uri.authority == targetAuthority
                    && runCatching { getTreeDocumentId(permission.uri) }.getOrNull() == targetTreeId
        }?.uri ?: permissions.firstOrNull { permission ->
            permission.isReadPermission && permission.uri.authority == targetAuthority
        }?.uri
    }

    private fun firstPersistedTreeUri(): Uri? {
        return context.contentResolver.persistedUriPermissions
            .firstOrNull { it.isReadPermission && it.uri.isContentScheme() }
            ?.uri
            ?.let(::normalizeFolderUri)
    }

    fun clearRoot() {
        _state.update {
            it.copy(
                rootDoc = null,
                subDocs = emptyList(),
                sourceDocs = emptyList(),
                selectedIds = emptySet(),
                sourceMode = SourceMode.CURRENT_DIR
            )
        }
    }

    fun setRootDoc(rootDoc: FileDoc) {
        _state.update {
            it.copy(
                rootDoc = rootDoc,
                subDocs = emptyList(),
                selectedIds = emptySet(),
                sourceMode = SourceMode.CURRENT_DIR
            )
        }
        loadCurrentDoc()
    }

    fun reloadCurrentDoc() {
        loadCurrentDoc()
    }

    fun navigateNext(fileDoc: FileDoc) {
        _state.update {
            it.copy(
                subDocs = it.subDocs + fileDoc,
                selectedIds = emptySet()
            )
        }
        loadCurrentDoc()
    }

    fun navigateBack(): Boolean {
        val hasSubDocs = _state.value.subDocs.isNotEmpty()
        if (!hasSubDocs) return false
        _state.update {
            it.copy(
                subDocs = it.subDocs.dropLast(1),
                selectedIds = emptySet()
            )
        }
        loadCurrentDoc()
        return true
    }

    fun navigateToLevel(index: Int) {
        val state = _state.value
        val rootDoc = state.rootDoc ?: return
        if (index < 0 || index > state.subDocs.size) return

        val newSubDocs = if (index == 0) {
            emptyList()
        } else {
            state.subDocs.take(index)
        }
        _state.update {
            it.copy(
                subDocs = newSubDocs,
                selectedIds = emptySet()
            )
        }
        loadDoc(newSubDocs.lastOrNull() ?: rootDoc)
    }

    fun setSort(sort: Int) {
        _state.update { it.copy(sort = sort) }
        ImportBookConfig.localBookImportSort = sort
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

    fun toggleSelection(id: String) {
        _state.update {
            val newSelected = if (id in it.selectedIds) {
                it.selectedIds - id
            } else {
                it.selectedIds + id
            }
            it.copy(selectedIds = newSelected)
        }
    }

    fun selectAllCheckable() {
        val selected = uiState.value.items
            .asSequence()
            .filter { !it.isDir && !it.isOnBookShelf }
            .map { it.selectionId }
            .toSet()
        _state.update { it.copy(selectedIds = selected) }
    }

    fun invertSelection() {
        val checkableIds = uiState.value.items
            .asSequence()
            .filter { !it.isDir && !it.isOnBookShelf }
            .map { it.selectionId }
            .toSet()
        _state.update { state ->
            state.copy(selectedIds = checkableIds - state.selectedIds)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedIds = emptySet()) }
    }

    fun addSelectedToBookshelf() {
        val selectedBooks = uiState.value.items
            .filter { it.selectionId in uiState.value.selectedIds }
            .filter { !it.isDir && !it.isOnBookShelf }

        if (selectedBooks.isEmpty()) return

        execute {
            LocalBook.importFiles(selectedBooks.map { it.file.uri })
        }.onError {
            context.toastOnUi("添加书架失败，请尝试重新选择文件夹")
            AppLog.put("添加书架失败\n${it.localizedMessage}", it)
        }.onSuccess {
            context.toastOnUi("添加书架成功")
        }.onFinally {
            clearSelection()
        }
    }

    private fun addSingleToBookshelf(item: ImportBook) {
        if (item.isDir || item.isOnBookShelf) return
        execute {
            LocalBook.importFiles(listOf(item.file.uri))
        }.onError {
            context.toastOnUi("添加书架失败，请尝试重新选择文件夹")
            AppLog.put("添加书架失败\n${it.localizedMessage}", it)
        }.onSuccess {
            context.toastOnUi("添加书架成功")
        }.onFinally {
            _state.update { state ->
                state.copy(selectedIds = state.selectedIds - item.selectionId)
            }
        }
    }

    private fun onItemClick(item: ImportBook) {
        when {
            item.isDir -> navigateNext(item.file)
            item.isOnBookShelf -> onImportedFileClick(item.file)
            else -> toggleSelection(item.selectionId)
        }
    }

    private fun onImportedFileClick(fileDoc: FileDoc) {
        if (!ArchiveUtils.isArchive(fileDoc.name)) {
            appDb.bookDao.getBookByFileName(fileDoc.name)?.let { book ->
                val filePath = fileDoc.toString()
                if (book.bookUrl != filePath) {
                    book.bookUrl = filePath
                    appDb.bookDao.insert(book)
                }
                _effects.tryEmit(ImportBookEffect.OpenBook(book))
            }
            return
        }
        val fileNames = ArchiveUtils.getArchiveFilesName(fileDoc) {
            it.matches(AppPattern.bookFileRegex)
        }
        when {
            fileNames.isEmpty() -> _effects.tryEmit(
                ImportBookEffect.ShowToastRes(io.legado.app.R.string.unsupport_archivefile_entry)
            )

            fileNames.size == 1 -> onArchiveEntrySelected(fileDoc, fileNames.first())
            else -> _effects.tryEmit(ImportBookEffect.ShowArchiveEntries(fileDoc, fileNames))
        }
    }

    private fun onArchiveEntrySelected(fileDoc: FileDoc, fileName: String) {
        appDb.bookDao.getBookByFileName(fileName)?.let {
            _effects.tryEmit(ImportBookEffect.OpenBook(it))
        } ?: _effects.tryEmit(ImportBookEffect.ShowImportArchiveDialog(fileDoc, fileName))
    }

    private fun addArchiveToBookShelf(fileDoc: FileDoc, fileName: String) {
        execute {
            LocalBook.importArchiveFile(fileDoc.uri, fileName) { it.contains(fileName) }
                .firstOrNull()
        }.onSuccess { book ->
            if (book != null) {
                _effects.tryEmit(ImportBookEffect.OpenBook(book))
            } else {
                _effects.tryEmit(
                    ImportBookEffect.ShowToastRes(io.legado.app.R.string.error)
                )
            }
        }.onError {
            _effects.tryEmit(
                ImportBookEffect.ShowToastRes(io.legado.app.R.string.error)
            )
        }
    }

    fun deleteSelectedDocs() {
        val selectedIds = uiState.value.selectedIds.mapTo(hashSetOf()) { it.toString() }
        if (selectedIds.isEmpty()) return

        execute {
            _state.value.sourceDocs
                .filter { it.toString() in selectedIds }
                .forEach { it.delete() }
        }.onFinally {
            clearSelection()
            refreshCurrentSource()
        }
    }

    fun scanCurrentDoc() {
        val current = currentDoc() ?: return

        scanDocJob?.cancel()
        _state.update {
            it.copy(
                sourceDocs = emptyList(),
                selectedIds = emptySet(),
                sourceMode = SourceMode.SCAN_RECURSIVE,
                interaction = it.interaction.copy(isLoading = true)
            )
        }

        scanDocJob = viewModelScope.launch(IO) {
            kotlin.runCatching {
                scanDoc(current)
            }.onSuccess { docs ->
                _state.update {
                    it.copy(
                        sourceDocs = docs,
                        selectedIds = emptySet(),
                        interaction = it.interaction.copy(isLoading = false)
                    )
                }
            }.onFailure {
                withContext(Main) {
                    context.toastOnUi("扫描文件夹出错\n${it.localizedMessage}")
                }
                _state.update { state ->
                    state.copy(interaction = state.interaction.copy(isLoading = false))
                }
            }
        }
    }

    private fun loadCurrentDoc() {
        currentDoc()?.let { loadDoc(it) }
    }

    private fun currentDoc(state: InternalState = _state.value): FileDoc? {
        return state.subDocs.lastOrNull() ?: state.rootDoc
    }

    private fun loadDoc(fileDoc: FileDoc) {
        scanDocJob?.cancel()
        _state.update {
            it.copy(
                sourceDocs = emptyList(),
                selectedIds = emptySet(),
                sourceMode = SourceMode.CURRENT_DIR,
                interaction = it.interaction.copy(isLoading = true)
            )
        }

        execute {
            listCurrentDocDocs(fileDoc)
        }.onSuccess { docs ->
            _state.update {
                it.copy(
                    sourceDocs = docs,
                    selectedIds = emptySet(),
                    interaction = it.interaction.copy(isLoading = false)
                )
            }
        }.onError {
            context.toastOnUi("获取文件列表出错\n${it.localizedMessage}")
            _state.update { state ->
                state.copy(interaction = state.interaction.copy(isLoading = false))
            }
        }
    }

    private fun listCurrentDocDocs(fileDoc: FileDoc): List<FileDoc> {
        return fileDoc.list { item ->
            when {
                item.name.startsWith(".") -> false
                item.isDir -> true
                else -> item.name.matches(bookFileRegex) || item.name.matches(archiveFileRegex)
            }
        } ?: emptyList()
    }

    private fun refreshCurrentSource() {
        when (_state.value.sourceMode) {
            SourceMode.CURRENT_DIR -> loadCurrentDoc()
            SourceMode.SCAN_RECURSIVE -> scanCurrentDoc()
        }
    }

    private fun startAutoSync() {
        if (autoSyncJob?.isActive == true) return
        autoSyncJob = viewModelScope.launch(IO) {
            while (isActive) {
                delay(1500)
                syncSourceDocs()
            }
        }
    }

    private suspend fun syncSourceDocs() {
        if (scanDocJob?.isActive == true) return
        val snapshot = _state.value
        if (snapshot.interaction.isLoading || snapshot.sourceDocs.isEmpty()) return

        val current = currentDoc(snapshot) ?: return
        when (snapshot.sourceMode) {
            SourceMode.CURRENT_DIR -> {
                val latestDocs = kotlin.runCatching { listCurrentDocDocs(current) }.getOrNull() ?: return
                val latestIds = latestDocs.asSequence().map { it.toString() }.toSet()
                val sourceIds = snapshot.sourceDocs.asSequence().map { it.toString() }.toSet()
                if (latestIds == sourceIds) return

                _state.update { state ->
                    state.copy(
                        sourceDocs = latestDocs,
                        selectedIds = state.selectedIds.filterTo(hashSetOf()) { it in latestIds }
                    )
                }
            }

            SourceMode.SCAN_RECURSIVE -> {
                if (snapshot.sourceDocs.none { !it.exists() }) return
                val latestDocs = kotlin.runCatching { scanDoc(current) }.getOrNull() ?: return
                val latestIds = latestDocs.asSequence().map { it.toString() }.toSet()
                _state.update { state ->
                    state.copy(
                        sourceDocs = latestDocs,
                        selectedIds = state.selectedIds.filterTo(hashSetOf()) { it in latestIds }
                    )
                }
            }
        }
    }

    private suspend fun scanDoc(fileDoc: FileDoc): List<FileDoc> {
        val channel = Channel<FileDoc>(UNLIMITED)
        var n = 1
        channel.trySend(fileDoc)
        val docs = arrayListOf<FileDoc>()

        channel.consumeAsFlow()
            .mapParallel(16) { doc ->
                doc.list() ?: emptyList<FileDoc>()
            }
            .onEach { fileDocs ->
                n--
                fileDocs.forEach {
                    if (it.isDir) {
                        n++
                        channel.trySend(it)
                    } else if (it.name.matches(bookFileRegex) || it.name.matches(archiveFileRegex)) {
                        docs.add(it)
                    }
                }
            }
            .takeWhile { n > 0 }
            .collect {}

        return docs
    }

    override fun onCleared() {
        autoSyncJob?.cancel()
        scanDocJob?.cancel()
        super.onCleared()
    }
}
