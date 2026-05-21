package io.legado.app.ui.main.bookshelf

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.base.BaseRuleEvent
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.data.repository.BookGroupRepository
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.BookSourceRepository
import io.legado.app.data.repository.BookshelfRepository
import io.legado.app.data.repository.UploadRepository
import io.legado.app.domain.usecase.AddBookUseCase
import io.legado.app.domain.usecase.BatchCacheDownloadUseCase
import io.legado.app.domain.usecase.ExportBookshelfUseCase
import io.legado.app.domain.usecase.ImportBookshelfUseCase
import io.legado.app.domain.usecase.RefreshTocUseCase
import io.legado.app.domain.usecase.UpdateBooksGroupUseCase
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.CacheBook
import io.legado.app.model.SourceCallBack
import io.legado.app.service.CacheBookService
import io.legado.app.ui.config.bookshelfConfig.BookshelfConfig
import io.legado.app.utils.eventBus.FlowEventBus
import io.legado.app.utils.move
import io.legado.app.utils.onEachParallel
import io.legado.app.utils.postEvent
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

class BookshelfViewModel(
    application: Application,
    private val bookRepository: BookRepository,
    private val bookGroupRepository: BookGroupRepository,
    private val bookSourceRepository: BookSourceRepository,
    private val bookshelfRepository: BookshelfRepository,
    private val uploadRepository: UploadRepository,
    private val batchCacheDownloadUseCase: BatchCacheDownloadUseCase,
    private val updateBooksGroupUseCase: UpdateBooksGroupUseCase,
    private val refreshTocUseCase: RefreshTocUseCase,
    private val addBookUseCase: AddBookUseCase,
    private val importBookshelfUseCase: ImportBookshelfUseCase,
    private val exportBookshelfUseCase: ExportBookshelfUseCase
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
    private val draggingBooksFlow = MutableStateFlow<List<BookUiItem>?>(null)
    private val pendingSavedBooksFlow = MutableStateFlow<List<BookUiItem>?>(null)
    private val isInitialLoadingFlow = MutableStateFlow(true)

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
        .onEach {
            if (it.isNotEmpty()) {
                isInitialLoadingFlow.value = false
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allGroupsFlow: StateFlow<List<BookGroup>> = bookGroupRepository.flowAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private data class GroupPreviewState(
        val previews: ImmutableMap<Long, ImmutableList<BookUiItem>>,
        val counts: ImmutableMap<Long, Int>,
        val allBookCount: Int
    )

    private data class DataForPreviews(
        val groups: List<BookGroup>,
        val bookGroupStyle: Int,
        val systemCountsMap: Map<Long, Int>,
        val allBookCount: Int
    )

    val groupSelectorState: StateFlow<BookshelfGroupSelectorState> = combine(
        groupsFlow,
        groupIdFlow
    ) { groups, selectedGroupId ->
        BookshelfGroupSelectorState(
            groups = groups.map { it.toBookGroupUi() }.toImmutableList(),
            selectedGroupIndex = groups.indexOfFirst { it.groupId == selectedGroupId }
                .coerceAtLeast(0),
            selectedGroupId = selectedGroupId
        )
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BookshelfGroupSelectorState())

    @OptIn(ExperimentalCoroutinesApi::class)
    val booksFlow: Flow<List<BookUiItem>> = groupIdFlow
        .flatMapLatest { groupId ->
            combine(
                bookRepository.flowBookShelfByGroup(groupId),
                groupsFlow,
                sortConfigFlow
            ) { list, groups, sortConfig ->
                bookshelfRepository.sortBooks(
                    list,
                    groups.find { it.groupId == groupId },
                    sortConfig.sort,
                    sortConfig.sortOrder
                ).map { it.toUiItem() }
            }
        }.distinctUntilChanged().flowOn(Dispatchers.Default)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val allGroupBooksFlow: StateFlow<Map<Long, List<BookUiItem>>> = combine(
        groupsFlow, sortConfigFlow
    ) { groups, sortConfig ->
        groups to sortConfig
    }.flatMapLatest { (groups, sortConfig) ->
        if (groups.isEmpty()) {
            flowOf(emptyMap())
        } else {
            val flows = groups.map { group ->
                bookRepository.flowBookShelfByGroup(group.groupId).map { books ->
                    group.groupId to bookshelfRepository.sortBooks(
                        books,
                        group,
                        sortConfig.sort,
                        sortConfig.sortOrder
                    ).map { it.toUiItem() }
                }
            }
            combine(flows) { it.toMap() }
        }
    }.distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val allGroupBooksImmutableFlow: StateFlow<ImmutableMap<Long, ImmutableList<BookUiItem>>> =
        allGroupBooksFlow.map { map ->
            map.mapValues { it.value.toImmutableList() }.toImmutableMap()
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), persistentMapOf())

    private val visibleBooksFlow: Flow<List<BookUiItem>> = combine(
        booksFlow,
        searchKeyFlow,
        searchModeFlow
    ) { books, searchKey, isSearchMode ->
        filterBooks(books, searchKey, isSearchMode)
    }.distinctUntilChanged()

    private val selectedGroupCanReorderFlow = combine(
        isEditModeFlow,
        searchModeFlow,
        groupIdFlow,
        groupsFlow,
        sortConfigFlow
    ) { isEditMode, isSearchMode, groupId, groups, sortConfig ->
        val group = groups.find { it.groupId == groupId }
        val bookSort = group?.bookSort?.takeIf { it >= 0 } ?: sortConfig.sort
        isEditMode && !isSearchMode && bookSort == 3
    }.distinctUntilChanged()

    private val selectedVisibleBookUrlsFlow = combine(
        selectedBookUrlsFlow,
        visibleBooksFlow
    ) { selectedBookUrls, visibleBooks ->
        val visibleBookUrls = visibleBooks.mapTo(hashSetOf()) { it.book.bookUrl }
        selectedBookUrls.intersect(visibleBookUrls)
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val groupPreviewsFlow = combine(
        groupsFlow,
        bookGroupStyleFlow,
        bookRepository.flowSystemGroupCounts(),
        bookRepository.flowAllBookShelfCount()
    ) { groups, bookGroupStyle, systemCounts, totalCount ->
        DataForPreviews(
            groups,
            bookGroupStyle,
            systemCounts.associate { it.groupId to it.count },
            totalCount
        )
    }.flatMapLatest { data ->
        val groups = data.groups
        val bookGroupStyle = data.bookGroupStyle
        val systemCountsMap = data.systemCountsMap
        val allBookCount = data.allBookCount

        if (bookGroupStyle !in 2..3) {
            flowOf(GroupPreviewState(persistentMapOf(), persistentMapOf(), allBookCount))
        } else if (groups.isEmpty()) {
            flowOf(GroupPreviewState(persistentMapOf(), persistentMapOf(), allBookCount))
        } else {
            val groupFlows = groups.map { group ->
                val countFlow: Flow<Int> = if (group.groupId > 0) {
                    bookRepository.flowUserGroupBookCount(group.groupId)
                } else {
                    flowOf(systemCountsMap[group.groupId] ?: 0)
                }
                val previewFlow = bookRepository.flowGroupPreview(group.groupId)
                combine(countFlow, previewFlow) { count, preview ->
                    Triple(group.groupId, count, preview.map { it.toUiItem() })
                }
            }
            combine(groupFlows) { results ->
                var previews = persistentMapOf<Long, ImmutableList<BookUiItem>>()
                var counts = persistentMapOf<Long, Int>()
                results.forEach { (groupId, count, preview) ->
                    counts = counts.put(groupId, count)
                    previews = previews.put(groupId, preview.toImmutableList())
                }
                GroupPreviewState(previews, counts, allBookCount)
            }
        }
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
        val draggingBooks: List<BookUiItem>?,
        val pendingSavedBooks: List<BookUiItem>?
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

    private val groupPreviewsStateFlow = MutableStateFlow(
        GroupPreviewState(persistentMapOf(), persistentMapOf(), 0)
    )

    private val allGroupBooksStateFlow =
        MutableStateFlow<ImmutableMap<Long, ImmutableList<BookUiItem>>>(
        persistentMapOf()
    )

    private val dataStateFlow = combine(
        booksFlow,
        groupsFlow,
        allGroupsFlow,
        groupPreviewsStateFlow,
        internalStateFlow
    ) { books, groups, allGroups, previews, internal ->
        BookshelfDataCore(books, groups, allGroups, previews, internal)
    }.combine(allGroupBooksStateFlow) { core, allGroupBooks ->
        BookshelfDataState(
            books = core.books,
            groups = core.groups.map { it.toBookGroupUi() },
            allGroups = core.allGroups.map { it.toBookGroupUi() },
            previews = core.previews,
            internal = core.internal,
            allGroupBooks = allGroupBooks
        )
    }

    private data class BookshelfDataCore(
        val books: List<BookUiItem>,
        val groups: List<BookGroup>,
        val allGroups: List<BookGroup>,
        val previews: GroupPreviewState,
        val internal: InternalState
    )

    private data class BookshelfDataState(
        val books: List<BookUiItem>,
        val groups: List<BookGroupUi>,
        val allGroups: List<BookGroupUi>,
        val previews: GroupPreviewState,
        val internal: InternalState,
        val allGroupBooks: ImmutableMap<Long, ImmutableList<BookUiItem>>
    )

    val uiState: StateFlow<BookshelfUiState> = combine(
        dataStateFlow,
        interactionStateFlow,
        visibleBooksFlow,
        isInitialLoadingFlow
    ) { data, interaction, filteredBooks, isInitialLoading ->
        val books = data.books
        val groups = data.groups
        val allGroups = data.allGroups
        val previews = data.previews
        val internal = data.internal
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
            isInitialLoading = isInitialLoading,
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
            subtitle = when {
                interaction.isEditMode -> {
                    context.getString(R.string.bookshelf_total_count, previews.allBookCount)
                }

                internal.isSearchMode -> {
                    context.getString(R.string.bookshelf_total_count, filteredBooks.size)
                }

                else -> null
            },
            currentGroupName = currentGroupName,
            draggingBooks = interaction.draggingBooks?.toImmutableList(),
            pendingSavedBooks = interaction.pendingSavedBooks?.toImmutableList(),
            allGroupBooks = data.allGroupBooks
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BookshelfUiState())

    init {
        viewModelScope.launch {
            delay(500)
            isInitialLoadingFlow.value = false
        }
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

        viewModelScope.launch {
            groupPreviewsFlow.collect { groupPreviewsStateFlow.value = it }
        }
        viewModelScope.launch {
            allGroupBooksImmutableFlow.collect { allGroupBooksStateFlow.value = it }
        }
        viewModelScope.launch {
            combine(booksFlow, selectedGroupCanReorderFlow) { books, canReorderBooks ->
                books to canReorderBooks
            }.collect { (books, canReorderBooks) ->
                syncDragState(books, canReorderBooks)
            }
        }

        viewModelScope.launch {
            isInitialLoadingFlow.filter { !it }.collect {
                if (BookshelfConfig.autoRefreshBook) {
                    upAllBookToc()
                }
            }
        }
    }

    private fun filterBooks(
        books: List<BookUiItem>,
        searchKey: String,
        isSearchMode: Boolean
    ): List<BookUiItem> {
        return if (!isSearchMode || searchKey.isBlank()) {
            books
        } else {
            books.filter { it.matches(searchKey) }
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
        selectedBookUrlsFlow.value = uiState.value.items.mapTo(hashSetOf()) { it.book.bookUrl }
    }

    fun invertVisibleSelection() {
        val visibleBookUrls = uiState.value.items.mapTo(hashSetOf()) { it.book.bookUrl }
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

    fun saveBookOrder(reorderedBooks: List<BookUiItem>) {
        if (reorderedBooks.isEmpty()) return
        val isDescending = BookshelfConfig.bookshelfSortOrder == 1
        val maxOrder = reorderedBooks.size
        execute {
            val updates = reorderedBooks.mapIndexedNotNull { index, bookUi ->
                appDb.bookDao.getBook(bookUi.book.bookUrl)?.apply {
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

    fun refreshBooks(books: List<BookUiItem>) {
        if (isRefreshingFlow.value) return
        isRefreshingFlow.value = true
        val limit = BookshelfConfig.bookshelfRefreshingLimit
        val list = if (limit > 0) books.take(limit) else books
        enqueueTocUpdate(list.map { it.book }, resetRefreshWhenIdle = true)
    }

    fun startDraggingBooks(books: List<BookUiItem>) {
        draggingBooksFlow.value = books
    }

    fun moveDraggingBook(fromIndex: Int, toIndex: Int, fallbackBooks: List<BookUiItem>) {
        if (fromIndex == toIndex) return
        val sourceBooks = draggingBooksFlow.value ?: fallbackBooks
        if (fromIndex !in sourceBooks.indices || toIndex !in sourceBooks.indices) return
        draggingBooksFlow.value = sourceBooks.toMutableList().apply {
            move(fromIndex, toIndex)
        }
    }

    fun finishDraggingBooks() {
        val reorderedUiBooks = draggingBooksFlow.value ?: return
        pendingSavedBooksFlow.value = reorderedUiBooks
        draggingBooksFlow.value = null
        saveBookOrder(reorderedUiBooks)
    }

    private fun syncDragState(books: List<BookUiItem>, canReorderBooks: Boolean) {
        if (!canReorderBooks) {
            clearDragState()
            return
        }
        val pending = pendingSavedBooksFlow.value ?: return
        if (books.map { it.book.bookUrl } == pending.map { it.book.bookUrl }) {
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
    fun upAllBookToc() {
        execute {
            addToWaitUp(appDb.bookDao.hasUpdateBooks)
        }
    }

    fun upToc(books: List<BookUiItem>) {
        val limit = BookshelfConfig.bookshelfRefreshingLimit
        val list = if (limit > 0) books.take(limit) else books
        enqueueTocUpdate(list.map { it.book }, resetRefreshWhenIdle = false)
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
        refreshTocUseCase.execute(bookUrl) { source, book ->
            addDownload(source, book)
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
        loadingTextFlow.value = "添加中..."
        addBookJob = execute {
            val successCount = addBookUseCase.execute(bookUrls) {
                loadingTextFlow.value = "添加中... ($it)"
            }
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

    fun exportToUri(uri: Uri, items: List<BookUiItem>) {
        execute {
            exportBookshelfUseCase.exportToUri(uri, items).getOrThrow()
        }.onSuccess {
            _eventChannel.trySend(BaseRuleEvent.ShowSnackbar("导出成功"))
        }.onError {
            _eventChannel.trySend(BaseRuleEvent.ShowSnackbar("导出失败\n${it.localizedMessage}"))
        }
    }

    fun uploadBookshelf(items: List<BookUiItem>) {
        execute {
            val json = exportBookshelfUseCase.exportToJson(items).getOrThrow()
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

    fun exportBookshelf(items: List<BookUiItem>?, success: (file: File) -> Unit) {
        execute {
            items ?: throw NoStackTraceException("书籍不能为空")
            exportBookshelfUseCase.exportToFile(items).getOrThrow()
        }.onSuccess {
            success(it)
        }.onError {
            context.toastOnUi("导出书籍出错\n${it.localizedMessage}")
        }
    }

    fun importBookshelf(str: String, groupId: Long) {
        execute {
            importBookshelfUseCase.import(str, groupId) {
                loadingTextFlow.value = it
            }.getOrThrow()
        }.onSuccess {
            context.toastOnUi(R.string.success)
        }.onError {
            context.toastOnUi(it.localizedMessage ?: "ERROR")
        }.onFinally {
            loadingTextFlow.value = null
        }
    }

    fun importBookshelf(uri: Uri, groupId: Long) {
        execute {
            importBookshelfUseCase.import(uri, groupId) {
                loadingTextFlow.value = it
            }.getOrThrow()
        }.onSuccess {
            context.toastOnUi(R.string.success)
        }.onError {
            context.toastOnUi(it.localizedMessage ?: "ERROR")
        }.onFinally {
            loadingTextFlow.value = null
        }
    }

    private fun BookShelfItem.matchesSearchKey(searchKey: String): Boolean {
        return name.contains(searchKey, true) ||
                author.contains(searchKey, true) ||
                originName.contains(searchKey, true)
    }

}
