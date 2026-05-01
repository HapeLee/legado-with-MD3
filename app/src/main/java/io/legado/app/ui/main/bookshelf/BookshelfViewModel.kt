package io.legado.app.ui.main.bookshelf

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import com.google.gson.stream.JsonWriter
import io.legado.app.R
import io.legado.app.base.BaseRuleEvent
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.repository.BookGroupRepository
import io.legado.app.data.repository.UploadRepository
import io.legado.app.domain.usecase.BatchCacheDownloadUseCase
import io.legado.app.domain.usecase.UpdateBooksGroupUseCase
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.addType
import io.legado.app.help.book.isUpError
import io.legado.app.help.book.removeType
import io.legado.app.help.book.sync
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.http.decompressed
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.model.CacheBook
import io.legado.app.model.ReadBook
import io.legado.app.model.SourceCallBack
import io.legado.app.model.webBook.WebBook
import io.legado.app.service.CacheBookService
import io.legado.app.ui.config.bookshelfConfig.BookshelfConfig
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.cnCompare
import io.legado.app.utils.eventBus.FlowEventBus
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.move
import io.legado.app.utils.onEachParallel
import io.legado.app.utils.postEvent
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.readText
import io.legado.app.utils.toastOnUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

class BookshelfViewModel(
    application: Application,
    private val bookGroupRepository: BookGroupRepository,
    private val uploadRepository: UploadRepository,
    private val batchCacheDownloadUseCase: BatchCacheDownloadUseCase,
    private val updateBooksGroupUseCase: UpdateBooksGroupUseCase
) : BaseViewModel(application) {
    private var addBookJob: Coroutine<*>? = null

    private val groupIdFlow = MutableStateFlow(BookshelfConfig.saveTabPosition)
    private val searchKeyFlow = MutableStateFlow("")
    private val searchModeFlow = MutableStateFlow(false)
    private val loadingTextFlow = MutableStateFlow<String?>(null)
    private val activeOverlayFlow = MutableStateFlow<BookshelfOverlay?>(null)
    private val isEditModeFlow = MutableStateFlow(false)
    private val selectedBookUrlsFlow = MutableStateFlow<Set<String>>(emptySet())
    private val isInFolderRootFlow = MutableStateFlow(BookshelfConfig.bookGroupStyle == 2)
    private val isRefreshingFlow = MutableStateFlow(false)
    private val bookGroupStyleFlow = MutableStateFlow(BookshelfConfig.bookGroupStyle)
    private val draggingBooksFlow = MutableStateFlow<List<BookShelfItem>?>(null)
    private val pendingSavedBooksFlow = MutableStateFlow<List<BookShelfItem>?>(null)

    private data class BookshelfSortConfig(
        val sort: Int,
        val sortOrder: Int
    )

    private fun readSortConfig() = BookshelfSortConfig(
        sort = BookshelfConfig.bookshelfSort,
        sortOrder = BookshelfConfig.bookshelfSortOrder
    )

    private val sortConfigFlow: StateFlow<BookshelfSortConfig> = snapshotFlow {
        readSortConfig()
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), readSortConfig())

    // 更新相关
    private val updateQueueLock = Any()
    private val waitUpTocBooks = LinkedList<String>()
    private val onUpTocBooks = ConcurrentHashMap.newKeySet<String>()
    private val updatingBooksFlow = MutableStateFlow<Set<String>>(emptySet())
    private val upBooksCountFlow = MutableStateFlow(0)
    private var upTocJob: Job? = null
    private var cacheBookJob: Job? = null
    private val eventListenerSource = ConcurrentHashMap<BookSource, Boolean>()

    val scrollTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val updateConcurrency: Int
        get() = AppConfig.threadCount.coerceIn(1, AppConst.MAX_THREAD)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val updateDispatcher: CoroutineDispatcher
        get() = Dispatchers.IO.limitedParallelism(updateConcurrency)

    protected val _eventChannel = Channel<BaseRuleEvent>()
    val events = _eventChannel.receiveAsFlow()

    val groupsFlow: StateFlow<List<BookGroup>> = bookGroupRepository.flowShow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allGroupsFlow: StateFlow<List<BookGroup>> = bookGroupRepository.flowAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allBooksFlow = appDb.bookDao.flowBookShelf()
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    private data class GroupPreviewState(
        val previews: ImmutableMap<Long, ImmutableList<BookShelfItem>>,
        val counts: ImmutableMap<Long, Int>,
        val allBookCount: Int
    )

    val groupSelectorState: StateFlow<BookshelfGroupSelectorState> = combine(
        groupsFlow,
        groupIdFlow
    ) { groups, selectedGroupId ->
        BookshelfGroupSelectorState(
            groups = groups.toImmutableList(),
            selectedGroupIndex = groups.indexOfFirst { it.groupId == selectedGroupId }
                .coerceAtLeast(0),
            selectedGroupId = selectedGroupId
        )
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BookshelfGroupSelectorState())

    @OptIn(ExperimentalCoroutinesApi::class)
    val booksFlow = groupIdFlow
        .flatMapLatest { groupId ->
            combine(
                appDb.bookDao.flowBookShelfByGroup(groupId),
                groupsFlow,
                sortConfigFlow
            ) { list, groups, sortConfig ->
                sortBooks(
                    list,
                    groups.find { it.groupId == groupId },
                    sortConfig
                )
            }
        }.distinctUntilChanged().flowOn(Dispatchers.Default)

    private val visibleBooksFlow = combine(
        booksFlow,
        searchKeyFlow,
        searchModeFlow
    ) { books, searchKey, isSearchMode ->
        filterBooks(books, searchKey, isSearchMode)
    }.distinctUntilChanged()

    private val selectedVisibleBookUrlsFlow = combine(
        selectedBookUrlsFlow,
        visibleBooksFlow
    ) { selectedBookUrls, visibleBooks ->
        val visibleBookUrls = visibleBooks.mapTo(hashSetOf()) { it.bookUrl }
        selectedBookUrls.intersect(visibleBookUrls)
    }.distinctUntilChanged()

    private val groupPreviewsFlow =
        combine(
            groupsFlow,
            allBooksFlow,
            bookGroupStyleFlow,
            sortConfigFlow
        ) { groups, allBooks, bookGroupStyle, sortConfig ->
            buildGroupPreviewState(groups, allBooks, bookGroupStyle, sortConfig)
        }.distinctUntilChanged().flowOn(Dispatchers.Default)

    private val coreInternalStateFlow = combine(
        groupIdFlow,
        searchKeyFlow,
        searchModeFlow,
        loadingTextFlow,
        updatingBooksFlow
    ) { groupId, searchKey, isSearchMode, loadingText, updatingBooks ->
        InternalState(
            groupId = groupId,
            searchKey = searchKey,
            isSearchMode = isSearchMode,
            loadingText = loadingText,
            updatingBooks = updatingBooks,
            upBooksCount = 0,
            sortConfig = readSortConfig()
        )
    }

    private val internalStateFlow = combine(
        coreInternalStateFlow,
        upBooksCountFlow,
        sortConfigFlow
    ) { core, upBooksCount, sortConfig ->
        core.copy(
            upBooksCount = upBooksCount,
            sortConfig = sortConfig
        )
    }

    private data class InternalState(
        val groupId: Long,
        val searchKey: String,
        val isSearchMode: Boolean,
        val loadingText: String?,
        val updatingBooks: Set<String>,
        val upBooksCount: Int,
        val sortConfig: BookshelfSortConfig
    )

    data class BookshelfInteractionState(
        val activeOverlay: BookshelfOverlay?,
        val isEditMode: Boolean,
        val selectedBookUrls: Set<String>,
        val isInFolderRoot: Boolean,
        val isRefreshing: Boolean,
        val bookGroupStyle: Int,
        val draggingBooks: List<BookShelfItem>?,
        val pendingSavedBooks: List<BookShelfItem>?
    )

    private val editStateFlow = combine(
        activeOverlayFlow,
        isEditModeFlow,
        selectedVisibleBookUrlsFlow,
        isInFolderRootFlow
    ) { activeOverlay, isEditMode, selectedBookUrls, isInFolderRoot ->
        EditState(activeOverlay, isEditMode, selectedBookUrls, isInFolderRoot)
    }

    private data class EditState(
        val activeOverlay: BookshelfOverlay?,
        val isEditMode: Boolean,
        val selectedBookUrls: Set<String>,
        val isInFolderRoot: Boolean
    )

    private val interactionStateFlow = combine(
        editStateFlow,
        isRefreshingFlow,
        bookGroupStyleFlow,
        draggingBooksFlow,
        pendingSavedBooksFlow
    ) { editState, isRefreshing, bookGroupStyle, draggingBooks, pendingSavedBooks ->
        BookshelfInteractionState(
            activeOverlay = editState.activeOverlay,
            isEditMode = editState.isEditMode,
            selectedBookUrls = editState.selectedBookUrls,
            isInFolderRoot = editState.isInFolderRoot,
            isRefreshing = isRefreshing,
            bookGroupStyle = bookGroupStyle,
            draggingBooks = draggingBooks,
            pendingSavedBooks = pendingSavedBooks
        )
    }

    private val dataStateFlow = combine(
        booksFlow,
        groupsFlow,
        allGroupsFlow,
        groupPreviewsFlow,
        internalStateFlow
    ) { books, groups, allGroups, previews, internal ->
        BookshelfDataState(books, groups, allGroups, previews, internal)
    }

    private data class BookshelfDataState(
        val books: List<BookShelfItem>,
        val groups: List<BookGroup>,
        val allGroups: List<BookGroup>,
        val previews: GroupPreviewState,
        val internal: InternalState
    )

    val uiState: StateFlow<BookshelfUiState> = combine(
        dataStateFlow,
        interactionStateFlow
    ) { data, interaction ->
        val books = data.books
        val groups = data.groups
        val allGroups = data.allGroups
        val previews = data.previews
        val internal = data.internal
        val filteredBooks = filterBooks(books, internal.searchKey, internal.isSearchMode)
        val selectedGroupIndex = groups.indexOfFirst { it.groupId == internal.groupId }
            .coerceAtLeast(0)
        val currentGroupName = allGroups.firstOrNull { it.groupId == internal.groupId }?.groupName
            ?: groups.getOrNull(selectedGroupIndex)?.groupName
        val selectedIds = interaction.selectedBookUrls.mapTo(linkedSetOf<Any>()) { it }
        val title = buildTitle(
            bookGroupStyle = interaction.bookGroupStyle,
            isInFolderRoot = interaction.isInFolderRoot,
            isEditMode = interaction.isEditMode,
            isSearchMode = internal.isSearchMode,
            currentGroupName = currentGroupName,
            upBooksCount = internal.upBooksCount
        )

        BookshelfUiState(
            items = filteredBooks.toImmutableList(),
            selectedIds = selectedIds.toImmutableSet(),
            groups = groups.toImmutableList(),
            allGroups = allGroups.toImmutableList(),
            groupPreviews = previews.previews,
            groupBookCounts = previews.counts,
            currentGroupBookCount = books.size,
            allBooksCount = previews.allBookCount,
            selectedGroupIndex = selectedGroupIndex,
            selectedGroupId = internal.groupId,
            searchKey = internal.searchKey,
            isSearch = internal.isSearchMode,
            isLoading = internal.loadingText != null,
            loadingText = internal.loadingText,
            upBooksCount = internal.upBooksCount,
            updatingBooks = internal.updatingBooks.toImmutableSet(),
            activeOverlay = interaction.activeOverlay,
            isEditMode = interaction.isEditMode,
            selectedBookUrls = interaction.selectedBookUrls.toImmutableSet(),
            isInFolderRoot = interaction.isInFolderRoot,
            isRefreshing = interaction.isRefreshing,
            bookGroupStyle = interaction.bookGroupStyle,
            bookshelfSort = internal.sortConfig.sort,
            bookshelfSortOrder = internal.sortConfig.sortOrder,
            title = title,
            subtitle = if (interaction.isEditMode) {
                context.getString(R.string.bookshelf_total_count, previews.allBookCount)
            } else {
                null
            },
            currentGroupName = currentGroupName,
            draggingBooks = interaction.draggingBooks?.toImmutableList(),
            pendingSavedBooks = interaction.pendingSavedBooks?.toImmutableList()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BookshelfUiState())

    init {
        viewModelScope.launch {
            FlowEventBus.with<Unit>(EventBus.UP_ALL_BOOK_TOC).collect {
                upAllBookToc()
            }
        }
        viewModelScope.launch {
            snapshotFlow { BookshelfConfig.bookGroupStyle }
                .distinctUntilChanged()
                .collect { style ->
                    updateBookGroupStyle(style)
                }
        }
        viewModelScope.launch {
            snapshotFlow { BookshelfConfig.showWaitUpCount }
                .distinctUntilChanged()
                .collect {
                    postUpBooksCount()
                }
        }

        if (BookshelfConfig.autoRefreshBook) {
            upAllBookToc()
        }
    }

    private fun filterBooks(
        books: List<BookShelfItem>,
        searchKey: String,
        isSearchMode: Boolean
    ): List<BookShelfItem> {
        return if (!isSearchMode || searchKey.isBlank()) {
            books
        } else {
            books.filter { it.matchesSearchKey(searchKey) }
        }
    }

    private fun sortBooks(
        list: List<BookShelfItem>,
        group: BookGroup?,
        sortConfig: BookshelfSortConfig
    ): List<BookShelfItem> {
        val bookSort = if (group != null && group.bookSort >= 0) {
            group.bookSort
        } else {
            sortConfig.sort
        }
        val isDescending = sortConfig.sortOrder == 1

        return when (bookSort) {
            1 -> if (isDescending) list.sortedByDescending { it.latestChapterTime }
            else list.sortedBy { it.latestChapterTime }

            2 -> if (isDescending)
                list.sortedWith { o1, o2 -> o2.name.cnCompare(o1.name) }
            else
                list.sortedWith { o1, o2 -> o1.name.cnCompare(o2.name) }

            3 -> if (isDescending) list.sortedByDescending { it.order }
            else list.sortedBy { it.order }

            4 -> if (isDescending) list.sortedByDescending {
                max(
                    it.latestChapterTime,
                    it.durChapterTime
                )
            }
            else list.sortedBy { max(it.latestChapterTime, it.durChapterTime) }

            5 -> if (isDescending)
                list.sortedWith { o1, o2 -> o2.author.cnCompare(o1.author) }
            else
                list.sortedWith { o1, o2 -> o1.author.cnCompare(o2.author) }

            else -> if (isDescending) list.sortedByDescending { it.durChapterTime }
            else list.sortedBy { it.durChapterTime }
        }
    }

    private fun buildTitle(
        bookGroupStyle: Int,
        isInFolderRoot: Boolean,
        isEditMode: Boolean,
        isSearchMode: Boolean,
        currentGroupName: String?,
        upBooksCount: Int
    ): String {
        val bookshelfTitle = context.getString(R.string.bookshelf)
        val baseTitle = when {
            isSearchMode && bookGroupStyle == 0 -> bookshelfTitle
            isSearchMode -> currentGroupName ?: bookshelfTitle
            bookGroupStyle == 1 -> currentGroupName ?: bookshelfTitle
            bookGroupStyle == 2 -> if (isInFolderRoot) {
                bookshelfTitle
            } else {
                currentGroupName ?: bookshelfTitle
            }

            else -> bookshelfTitle
        }
        return when {
            isEditMode -> bookshelfTitle
            upBooksCount > 0 -> "$baseTitle ($upBooksCount)"
            else -> baseTitle
        }
    }

    private fun buildGroupPreviewState(
        groups: List<BookGroup>,
        allBooks: List<BookShelfItem>,
        bookGroupStyle: Int,
        sortConfig: BookshelfSortConfig
    ): GroupPreviewState {
        if (bookGroupStyle !in 2..3) {
            return GroupPreviewState(persistentMapOf(), persistentMapOf(), allBooks.size)
        }

        val buckets = HashMap<Long, MutableList<BookShelfItem>>(groups.size)
        groups.forEach { group ->
            buckets[group.groupId] = ArrayList()
        }

        val userGroups = groups.filter { it.groupId > 0 }
        val sumUserGroupIds = userGroups.sumOf { it.groupId }

        fun add(groupId: Long, book: BookShelfItem) {
            buckets[groupId]?.add(book)
        }

        allBooks.forEach { book ->
            add(BookGroup.IdAll, book)
            if (book.isRootGroupBook(sumUserGroupIds)) add(BookGroup.IdRoot, book)
            if (book.isLocal) add(BookGroup.IdLocal, book)
            if (book.isAudio) add(BookGroup.IdAudio, book)
            if (book.isNetNoneGroupBook(sumUserGroupIds)) add(BookGroup.IdNetNone, book)
            if (book.isLocalNoneGroupBook(sumUserGroupIds)) add(BookGroup.IdLocalNone, book)
            if (book.isImage) add(BookGroup.IdManga, book)
            if ((book.type and BookType.text) > 0) add(BookGroup.IdText, book)
            if ((book.type and BookType.updateError) > 0) add(BookGroup.IdError, book)
            if (book.durChapterIndex == 0 && book.durChapterPos == 0) {
                add(BookGroup.IdUnread, book)
            }
            if (book.totalChapterNum > 0 &&
                book.durChapterIndex > 0 &&
                book.durChapterIndex < book.totalChapterNum - 1
            ) {
                add(BookGroup.IdReading, book)
            }
            if (book.totalChapterNum > 0 && book.durChapterIndex >= book.totalChapterNum - 1) {
                add(BookGroup.IdReadFinished, book)
            }
            userGroups.forEach { group ->
                if ((book.group and group.groupId) != 0L) {
                    add(group.groupId, book)
                }
            }
        }

        val previews = HashMap<Long, ImmutableList<BookShelfItem>>(groups.size)
        val counts = HashMap<Long, Int>(groups.size)
        groups.forEach { group ->
            val groupBooks = buckets[group.groupId].orEmpty()
            counts[group.groupId] = groupBooks.size
            previews[group.groupId] = buildGroupPreview(sortBooks(groupBooks, group, sortConfig))
        }
        return GroupPreviewState(previews.toImmutableMap(), counts.toImmutableMap(), allBooks.size)
    }

    private fun BookShelfItem.isRootGroupBook(sumUserGroupIds: Long): Boolean {
        return (type and BookType.text) > 0 &&
                (type and BookType.local) == 0 &&
                (sumUserGroupIds and group) == 0L
    }

    private fun BookShelfItem.isNetNoneGroupBook(sumUserGroupIds: Long): Boolean {
        return (type and BookType.audio) == 0 &&
                (type and BookType.local) == 0 &&
                (sumUserGroupIds and group) == 0L
    }

    private fun BookShelfItem.isLocalNoneGroupBook(sumUserGroupIds: Long): Boolean {
        return (type and BookType.local) > 0 &&
                (sumUserGroupIds and group) == 0L
    }

    private fun buildGroupPreview(sortedBooks: List<BookShelfItem>): ImmutableList<BookShelfItem> {
        val booksWithCover = sortedBooks.filter { it.getDisplayCover() != null }
        return if (booksWithCover.size >= 4) {
            booksWithCover.take(4)
        } else {
            (booksWithCover + sortedBooks.filter { it.getDisplayCover() == null }).take(4)
        }.toImmutableList()
    }

    fun changeGroup(groupId: Long) {
        if (groupIdFlow.value != groupId) {
            groupIdFlow.value = groupId
            BookshelfConfig.saveTabPosition = groupId
            clearSelection()
            clearDragState()
        }
    }

    fun setSearchKey(key: String) {
        searchKeyFlow.value = key
    }

    fun setSearchMode(active: Boolean) {
        searchModeFlow.value = active
        if (!active) {
            searchKeyFlow.value = ""
        }
        clearSelection()
    }

    fun showOverlay(overlay: BookshelfOverlay) {
        activeOverlayFlow.value = overlay
    }

    fun dismissOverlay() {
        activeOverlayFlow.value = null
    }

    fun toggleEditMode() {
        if (isEditModeFlow.value) {
            exitEditMode()
            return
        }
        if (bookGroupStyleFlow.value == 2 && isInFolderRootFlow.value) {
            isInFolderRootFlow.value = false
        }
        isEditModeFlow.value = true
        clearSelection()
    }

    fun exitEditMode() {
        isEditModeFlow.value = false
        clearSelection()
        clearDragState()
    }

    fun clearSelection() {
        selectedBookUrlsFlow.value = emptySet()
    }

    fun selectAllVisible() {
        selectedBookUrlsFlow.value = uiState.value.items.mapTo(hashSetOf()) { it.bookUrl }
    }

    fun invertVisibleSelection() {
        val visibleBookUrls = uiState.value.items.mapTo(hashSetOf()) { it.bookUrl }
        selectedBookUrlsFlow.value = visibleBookUrls - selectedBookUrlsFlow.value
    }

    fun toggleBookSelection(bookUrl: String) {
        selectedBookUrlsFlow.value = if (selectedBookUrlsFlow.value.contains(bookUrl)) {
            selectedBookUrlsFlow.value - bookUrl
        } else {
            selectedBookUrlsFlow.value + bookUrl
        }
    }

    fun setInFolderRoot(isInFolderRoot: Boolean) {
        if (isInFolderRootFlow.value != isInFolderRoot) {
            isInFolderRootFlow.value = isInFolderRoot
            clearSelection()
            clearDragState()
        }
    }

    private fun updateBookGroupStyle(bookGroupStyle: Int) {
        val previousStyle = bookGroupStyleFlow.value
        if (previousStyle == bookGroupStyle) return
        bookGroupStyleFlow.value = bookGroupStyle
        if (bookGroupStyle == 2 && previousStyle != 2) {
            isInFolderRootFlow.value = true
        } else if (bookGroupStyle != 2) {
            isInFolderRootFlow.value = false
        }
        clearSelection()
        clearDragState()
    }

    fun moveBooksToGroup(bookUrls: Set<String>, groupId: Long) {
        if (bookUrls.isEmpty()) return
        execute {
            updateBooksGroupUseCase.replaceGroup(bookUrls, groupId)
        }.onError {
            context.toastOnUi("更新分组失败\n${it.localizedMessage}")
        }
    }

    fun saveBookOrder(reorderedBooks: List<BookShelfItem>) {
        if (reorderedBooks.isEmpty()) return
        val isDescending = BookshelfConfig.bookshelfSortOrder == 1
        val maxOrder = reorderedBooks.size
        execute {
            val updates = reorderedBooks.mapIndexedNotNull { index, book ->
                appDb.bookDao.getBook(book.bookUrl)?.apply {
                    order = if (isDescending) maxOrder - index else index + 1
                }
            }
            if (updates.isNotEmpty()) {
                appDb.bookDao.update(*updates.toTypedArray())
            }
        }.onError {
            context.toastOnUi("排序保存失败\n${it.localizedMessage}")
        }
    }

    fun downloadBooks(bookUrls: Set<String>, downloadAllChapters: Boolean = false) {
        if (bookUrls.isEmpty()) return
        execute {
            batchCacheDownloadUseCase.execute(
                bookUrls = bookUrls,
                downloadAllChapters = downloadAllChapters,
                skipAudioBooks = true
            )
        }.onSuccess { count ->
            if (count > 0) {
                context.toastOnUi("已加入缓存队列: $count 本")
            } else {
                context.toastOnUi(R.string.no_download)
            }
        }.onError {
            context.toastOnUi("批量缓存失败\n${it.localizedMessage}")
        }
    }

    fun refreshBooks(books: List<BookShelfItem>) {
        if (isRefreshingFlow.value) return
        isRefreshingFlow.value = true
        enqueueTocUpdate(books, resetRefreshWhenIdle = true)
    }

    fun startDraggingBooks(books: List<BookShelfItem>) {
        draggingBooksFlow.value = books
    }

    fun moveDraggingBook(fromIndex: Int, toIndex: Int, fallbackBooks: List<BookShelfItem>) {
        if (fromIndex == toIndex) return
        val sourceBooks = draggingBooksFlow.value ?: fallbackBooks
        if (fromIndex !in sourceBooks.indices || toIndex !in sourceBooks.indices) return
        draggingBooksFlow.value = sourceBooks.toMutableList().apply {
            move(fromIndex, toIndex)
        }
    }

    fun finishDraggingBooks() {
        val reorderedBooks = draggingBooksFlow.value ?: return
        pendingSavedBooksFlow.value = reorderedBooks
        draggingBooksFlow.value = null
        saveBookOrder(reorderedBooks)
    }

    fun syncDragState(books: List<BookShelfItem>, canReorderBooks: Boolean) {
        if (!canReorderBooks) {
            clearDragState()
            return
        }
        val pending = pendingSavedBooksFlow.value ?: return
        if (books.map { it.bookUrl } == pending.map { it.bookUrl }) {
            pendingSavedBooksFlow.value = null
        }
    }

    private fun clearDragState() {
        draggingBooksFlow.value = null
        pendingSavedBooksFlow.value = null
    }

    fun gotoTop() {
        scrollTrigger.tryEmit(Unit)
    }

    // 更新逻辑移入
    fun upAllBookToc() {
        execute {
            addToWaitUp(appDb.bookDao.hasUpdateBooks)
        }
    }

    fun upToc(books: List<BookShelfItem>) {
        enqueueTocUpdate(books, resetRefreshWhenIdle = false)
    }

    private fun enqueueTocUpdate(
        books: List<BookShelfItem>,
        resetRefreshWhenIdle: Boolean
    ) {
        execute(context = updateDispatcher) {
            val bookUrls = books.filter { !it.isLocal && it.canUpdate }.map { it.bookUrl }
            val fullBooks = bookUrls.mapNotNull { appDb.bookDao.getBook(it) }
            addToWaitUp(fullBooks)
        }.onError {
            if (resetRefreshWhenIdle) {
                isRefreshingFlow.value = false
            }
        }.onFinally {
            if (resetRefreshWhenIdle) {
                completeRefreshIfIdle()
            }
        }
    }

    private fun addToWaitUp(books: List<Book>) {
        synchronized(updateQueueLock) {
            books.forEach { book ->
                if (!waitUpTocBooks.contains(book.bookUrl) &&
                    !onUpTocBooks.contains(book.bookUrl)
                ) {
                    waitUpTocBooks.add(book.bookUrl)
                }
            }
            if (upTocJob == null && waitUpTocBooks.isNotEmpty()) {
                startUpTocJobLocked()
            }
        }
        postUpBooksCount()
    }

    private fun startUpTocJobLocked() {
        upTocJob = viewModelScope.launch(updateDispatcher) {
            var completedWithoutFlowError = true
            flow {
                while (true) {
                    emit(pollWaitUpBookUrl() ?: break)
                }
            }.onEachParallel(updateConcurrency) {
                markBookUpdateStarted(it)
                try {
                    postEvent(EventBus.UP_BOOKSHELF, it)
                    updateToc(it)
                } finally {
                    markBookUpdateFinished(it)
                }
            }.catch {
                completedWithoutFlowError = false
                AppLog.put("更新目录出错\n${it.localizedMessage}", it)
            }.collect()

            finishUpTocJob(completedWithoutFlowError)
        }
        postUpBooksCount()
    }

    private fun pollWaitUpBookUrl(): String? = synchronized(updateQueueLock) {
        waitUpTocBooks.poll()
    }

    private fun markBookUpdateStarted(bookUrl: String) {
        synchronized(updateQueueLock) {
            onUpTocBooks.add(bookUrl)
        }
        updatingBooksFlow.value = onUpTocBooksSnapshot()
    }

    private fun markBookUpdateFinished(bookUrl: String) {
        synchronized(updateQueueLock) {
            onUpTocBooks.remove(bookUrl)
        }
        updatingBooksFlow.value = onUpTocBooksSnapshot()
        postEvent(EventBus.UP_BOOKSHELF, bookUrl)
        postUpBooksCount()
    }

    private fun onUpTocBooksSnapshot(): Set<String> = synchronized(updateQueueLock) {
        onUpTocBooks.toSet()
    }

    private fun finishUpTocJob(completedWithoutFlowError: Boolean) {
        val restarted = synchronized(updateQueueLock) {
            upTocJob = null
            if (waitUpTocBooks.isNotEmpty()) {
                startUpTocJobLocked()
                true
            } else {
                false
            }
        }

        if (!restarted) {
            completeRefreshIfIdle()
        }
        if (!restarted && completedWithoutFlowError && cacheBookJob == null && !CacheBookService.isRun) {
            cacheBook()
        }
    }

    private fun completeRefreshIfIdle() {
        val isIdle = synchronized(updateQueueLock) {
            upTocJob == null && waitUpTocBooks.isEmpty() && onUpTocBooks.isEmpty()
        }
        if (isIdle) {
            isRefreshingFlow.value = false
        }
    }

    private suspend fun updateToc(bookUrl: String) {
        val book = appDb.bookDao.getBook(bookUrl) ?: return
        val source = appDb.bookSourceDao.getBookSource(book.origin)
        if (source == null) {
            if (!book.isUpError) {
                book.addType(BookType.updateError)
                appDb.bookDao.update(book)
            }
            return
        }
        if (source.eventListener) {
            if (eventListenerSource.putIfAbsent(source, true) == null) {
                SourceCallBack.callBackSource(
                    viewModelScope,
                    SourceCallBack.START_SHELF_REFRESH,
                    source
                )
            }
        }
        kotlin.runCatching {
            val oldBook = book.copy()
            if (book.tocUrl.isBlank()) {
                WebBook.getBookInfoAwait(source, book)
            } else {
                WebBook.runPreUpdateJs(source, book)
            }
            val toc = WebBook.getChapterListAwait(source, book).getOrThrow()
            book.sync(oldBook)
            book.removeType(BookType.updateError)
            if (book.bookUrl == bookUrl) {
                appDb.bookDao.update(book)
            } else {
                appDb.bookDao.replace(oldBook, book)
                BookHelp.updateCacheFolder(oldBook, book)
            }
            appDb.bookChapterDao.delByBook(bookUrl)
            appDb.bookChapterDao.insert(*toc.toTypedArray())
            ReadBook.onChapterListUpdated(book)
            addDownload(source, book)
        }.onFailure {
            currentCoroutineContext().ensureActive()
            AppLog.put("${book.name} 更新目录失败\n${it.localizedMessage}", it)
            appDb.bookDao.getBook(book.bookUrl)?.let { book ->
                book.addType(BookType.updateError)
                appDb.bookDao.update(book)
            }
        }
    }

    private fun postUpBooksCount() {
        val count = if (BookshelfConfig.showWaitUpCount) {
            synchronized(updateQueueLock) {
                waitUpTocBooks.size + onUpTocBooks.size
            }
        } else {
            0
        }
        upBooksCountFlow.value = count
    }

    private fun addDownload(source: BookSource, book: Book) {
        if (AppConfig.preDownloadNum == 0) return
        val endIndex =
            min(book.totalChapterNum - 1, book.durChapterIndex + AppConfig.preDownloadNum)
        val cacheBook = CacheBook.getOrCreate(source, book)
        cacheBook.addDownload(book.durChapterIndex, endIndex)
    }

    private fun cacheBook() {
        eventListenerSource.toList().forEach {
            SourceCallBack.callBackSource(
                viewModelScope,
                SourceCallBack.END_SHELF_REFRESH,
                it.first
            )
        }
        eventListenerSource.clear()
        if (AppConfig.preDownloadNum == 0) return
        cacheBookJob?.cancel()
        cacheBookJob = viewModelScope.launch(updateDispatcher) {
            launch {
                while (isActive && CacheBook.isRun) {
                    CacheBook.setWorkingState(isUpdateQueueIdle())
                    delay(1000)
                }
            }
            CacheBook.startProcessJob(updateDispatcher)
        }
    }

    private fun isUpdateQueueIdle(): Boolean = synchronized(updateQueueLock) {
        waitUpTocBooks.isEmpty() && onUpTocBooks.isEmpty()
    }

    fun addBookByUrl(bookUrls: String) {
        var successCount = 0
        loadingTextFlow.value = "添加中..."
        addBookJob = execute {
            val hasBookUrlPattern: List<BookSourcePart> by lazy {
                appDb.bookSourceDao.hasBookUrlPattern
            }
            val urls = bookUrls.split("\n")
            for (url in urls) {
                val bookUrl = url.trim()
                if (bookUrl.isEmpty()) continue
                if (appDb.bookDao.getBook(bookUrl) != null) {
                    successCount++
                    continue
                }
                val baseUrl = NetworkUtils.getBaseUrl(bookUrl) ?: continue
                var source = appDb.bookSourceDao.getBookSourceAddBook(baseUrl)
                if (source == null) {
                    for (bookSource in hasBookUrlPattern) {
                        try {
                            val bs = bookSource.getBookSource()!!
                            if (bookUrl.matches(bs.bookUrlPattern!!.toRegex())) {
                                source = bs
                                break
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
                val bookSource = source ?: continue
                val book = Book(
                    bookUrl = bookUrl,
                    origin = bookSource.bookSourceUrl,
                    originName = bookSource.bookSourceName
                )
                kotlin.runCatching {
                    WebBook.getBookInfoAwait(bookSource, book)
                }.onSuccess {
                    val dbBook = appDb.bookDao.getBook(it.name, it.author)
                    if (dbBook != null) {
                        val toc = WebBook.getChapterListAwait(bookSource, it).getOrThrow()
                        dbBook.migrateTo(it, toc)
                        appDb.bookDao.insert(it)
                        appDb.bookChapterDao.insert(*toc.toTypedArray())
                    } else {
                        it.order = appDb.bookDao.minOrder - 1
                        it.save()
                    }
                    successCount++
                    loadingTextFlow.value = "添加中... ($successCount)"
                }
            }
        }.onSuccess {
            if (successCount > 0) {
                context.toastOnUi(R.string.success)
            } else {
                context.toastOnUi("添加网址失败")
            }
        }.onError {
            AppLog.put("添加网址出错\n${it.localizedMessage}", it, true)
        }.onFinally {
            loadingTextFlow.value = null
        }
    }

    fun exportToUri(uri: Uri, items: List<BookShelfItem>) {
        execute {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                val writer = JsonWriter(OutputStreamWriter(out, "UTF-8"))
                writer.setIndent("  ")
                writer.beginArray()
                items.forEach {
                    val bookMap = hashMapOf<String, String?>()
                    bookMap["name"] = it.name
                    bookMap["author"] = it.author
                    // intro is not in BookShelfItem, fetch from DB if needed or skip
                    // For now, let's keep it simple and skip intro or fetch it
                    val fullBook = appDb.bookDao.getBook(it.bookUrl)
                    bookMap["intro"] = fullBook?.getDisplayIntro()
                    GSON.toJson(bookMap, bookMap::class.java, writer)
                }
                writer.endArray()
                writer.close()
            }
        }.onSuccess {
            _eventChannel.trySend(BaseRuleEvent.ShowSnackbar("导出成功"))
        }.onError {
            _eventChannel.trySend(BaseRuleEvent.ShowSnackbar("导出失败\n${it.localizedMessage}"))
        }
    }

    fun uploadBookshelf(items: List<BookShelfItem>) {
        execute {
            val json = withContext(Dispatchers.Default) {
                val list = items.map {
                    val bookMap = hashMapOf<String, String?>()
                    bookMap["name"] = it.name
                    bookMap["author"] = it.author
                    val fullBook = appDb.bookDao.getBook(it.bookUrl)
                    bookMap["intro"] = fullBook?.getDisplayIntro()
                    bookMap
                }
                GSON.toJson(list)
            }
            uploadRepository.upload(
                fileName = "bookshelf.json",
                file = json,
                contentType = "application/json"
            )
        }.onSuccess { url ->
            _eventChannel.trySend(
                BaseRuleEvent.ShowSnackbar(
                    message = "上传成功: $url",
                    actionLabel = "复制链接",
                    url = url
                )
            )
        }.onError {
            _eventChannel.trySend(
                BaseRuleEvent.ShowSnackbar(
                    message = "上传失败: ${it.localizedMessage}"
                )
            )
        }
    }

    fun exportBookshelf(items: List<BookShelfItem>?, success: (file: File) -> Unit) {
        execute {
            items?.let {
                val path = "${context.filesDir}/books.json"
                FileUtils.delete(path)
                val file = FileUtils.createFileWithReplace(path)
                FileOutputStream(file).use { out ->
                    val writer = JsonWriter(OutputStreamWriter(out, "UTF-8"))
                    writer.setIndent("  ")
                    writer.beginArray()
                    items.forEach {
                        val bookMap = hashMapOf<String, String?>()
                        bookMap["name"] = it.name
                        bookMap["author"] = it.author
                        val fullBook = appDb.bookDao.getBook(it.bookUrl)
                        bookMap["intro"] = fullBook?.getDisplayIntro()
                        GSON.toJson(bookMap, bookMap::class.java, writer)
                    }
                    writer.endArray()
                    writer.close()
                }
                file
            } ?: throw NoStackTraceException("书籍不能为空")
        }.onSuccess {
            success(it)
        }.onError {
            context.toastOnUi("导出书籍出错\n${it.localizedMessage}")
        }
    }

    fun importBookshelf(str: String, groupId: Long) {
        execute {
            val text = str.trim()
            when {
                text.isAbsUrl() -> {
                    okHttpClient.newCallResponseBody {
                        url(text)
                    }.decompressed().text().let {
                        importBookshelf(it, groupId)
                    }
                }

                text.isJsonArray() -> {
                    importBookshelfByJson(text, groupId)
                }

                else -> {
                    throw NoStackTraceException("格式不对")
                }
            }
        }.onError {
            context.toastOnUi(it.localizedMessage ?: "ERROR")
        }
    }

    fun importBookshelf(uri: Uri, groupId: Long) {
        execute {
            uri.readText(context)
        }.onSuccess {
            importBookshelf(it, groupId)
        }.onError {
            context.toastOnUi(it.localizedMessage ?: "ERROR")
        }
    }

    private fun importBookshelfByJson(json: String, groupId: Long) {
        loadingTextFlow.value = "导入中..."
        execute {
            val bookSourceParts = appDb.bookSourceDao.allEnabledPart
            val semaphore = Semaphore(AppConfig.threadCount)
            GSON.fromJsonArray<Map<String, String?>>(json).getOrThrow().forEach { bookInfo ->
                val name = bookInfo["name"] ?: ""
                val author = bookInfo["author"] ?: ""
                if (name.isEmpty() || appDb.bookDao.has(name, author)) {
                    return@forEach
                }
                semaphore.withPermit {
                    WebBook.preciseSearch(
                        this, bookSourceParts, name, author,
                        semaphore = semaphore
                    ).onSuccess {
                        val book = it.first
                        if (groupId > 0) {
                            book.group = groupId
                        }
                        book.save()
                    }.onError { e ->
                        context.toastOnUi(e.localizedMessage)
                    }
                }
            }
        }.onError {
            it.printOnDebug()
        }.onFinally {
            loadingTextFlow.value = null
            context.toastOnUi(R.string.success)
        }
    }

    private fun BookShelfItem.matchesSearchKey(searchKey: String): Boolean {
        return name.contains(searchKey, true) ||
                author.contains(searchKey, true) ||
                originName.contains(searchKey, true)
    }

}
