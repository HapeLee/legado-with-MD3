package io.legado.app.ui.main.bookshelf

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.data.repository.BookGroupRepository
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.BookSourceRepository
import io.legado.app.data.repository.BookshelfRepository
import io.legado.app.data.repository.UploadRepository
import io.legado.app.domain.gateway.AppShellSettingsGateway
import io.legado.app.domain.gateway.BookshelfSettingsGateway
import io.legado.app.domain.gateway.DownloadCacheSettingsGateway
import io.legado.app.domain.gateway.PrivateAccessGateway
import io.legado.app.domain.gateway.PrivateContentGateway
import io.legado.app.domain.gateway.ThemeSettingsGateway
import io.legado.app.domain.model.PrivateAccessState
import io.legado.app.domain.model.PrivateUnlockTarget
import io.legado.app.domain.model.isPrivateBook
import io.legado.app.domain.model.settings.PrivateAccessSettings
import io.legado.app.domain.usecase.AddBookUseCase
import io.legado.app.domain.usecase.BatchCacheDownloadUseCase
import io.legado.app.domain.usecase.ExportBookshelfUseCase
import io.legado.app.domain.usecase.ImportBookshelfUseCase
import io.legado.app.domain.usecase.RefreshTocUseCase
import io.legado.app.domain.usecase.UpdateBooksGroupUseCase
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.CacheBook
import io.legado.app.model.SourceCallBack
import io.legado.app.service.CacheBookService
import io.legado.app.ui.config.themeConfig.TagColorPair
import io.legado.app.utils.GSON
import io.legado.app.utils.eventBus.FlowEventBus
import io.legado.app.utils.move
import io.legado.app.utils.onEachParallel
import io.legado.app.utils.postEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
import kotlinx.coroutines.flow.shareIn
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
    private val exportBookshelfUseCase: ExportBookshelfUseCase,
    private val bookshelfSettingsGateway: BookshelfSettingsGateway,
    private val appShellSettingsGateway: AppShellSettingsGateway,
    private val themeSettingsGateway: ThemeSettingsGateway,
    private val downloadCacheSettingsGateway: DownloadCacheSettingsGateway,
    private val privateAccessGateway: PrivateAccessGateway,
    private val privateContentGateway: PrivateContentGateway,
) : BaseViewModel(application) {
    private var addBookJob: Coroutine<*>? = null

    private val initialSettings = bookshelfSettingsGateway.currentSettings
    private val groupIdFlow = MutableStateFlow(initialSettings.saveTabPosition)
    private val searchKeyFlow = MutableStateFlow("")
    private val searchModeFlow = MutableStateFlow(false)
    private val loadingTextFlow = MutableStateFlow<String?>(null)
    private val activeOverlayFlow = MutableStateFlow<BookshelfOverlay?>(null)
    private val isEditModeFlow = MutableStateFlow(false)
    private val selectedBookUrlsFlow = MutableStateFlow<Set<String>>(emptySet())
    private val isInFolderRootFlow = MutableStateFlow(initialSettings.bookGroupStyle == 2)
    private val isRefreshingFlow = MutableStateFlow(false)
    private val bookGroupStyleFlow = MutableStateFlow(initialSettings.bookGroupStyle)
    private val draggingBooksFlow = MutableStateFlow<List<BookUiItem>?>(null)
    private val pendingSavedBooksFlow = MutableStateFlow<List<BookUiItem>?>(null)
    private val isInitialLoadingFlow = MutableStateFlow(true)
    private val pendingUploadUrlFlow = MutableStateFlow<String?>(null)

    /** 点击脱敏的私密书籍后挂起、等解锁成功再打开的目标书 */
    private val pendingOpenBookUrlFlow = MutableStateFlow<String?>(null)

    private data class BookshelfSortConfig(
        val sort: Int,
        val sortOrder: Int
    )

    private val bookshelfSettings = bookshelfSettingsGateway.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initialSettings)
    private val initialAppShellSettings = appShellSettingsGateway.currentSettings
    private val initialThemeSettings = themeSettingsGateway.currentSettings

    private val sortConfigFlow: StateFlow<BookshelfSortConfig> = bookshelfSettings
        .map { BookshelfSortConfig(it.bookshelfSort, it.bookshelfSortOrder) }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            BookshelfSortConfig(initialSettings.bookshelfSort, initialSettings.bookshelfSortOrder)
        )

    // 更新相关
    private val updateQueueLock = Any()
    private val waitUpTocBooks = LinkedList<String>()
    private val onUpTocBooks = ConcurrentHashMap.newKeySet<String>()
    private val updatingBooksFlow = MutableStateFlow<Set<String>>(emptySet())
    private val upBooksCountFlow = MutableStateFlow(0)
    private var upTocJob: Job? = null
    private var cacheBookJob: Job? = null
    private val eventListenerSource = ConcurrentHashMap<BookSource, Boolean>()

    private val _scrollTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollTrigger = _scrollTrigger.asSharedFlow()

    private val updateConcurrency: Int
        get() = downloadCacheSettingsGateway.currentSettings.threadCount
            .coerceIn(1, AppConst.MAX_THREAD)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val updateDispatcher: CoroutineDispatcher
        get() = Dispatchers.IO.limitedParallelism(updateConcurrency)

    private val _effects = MutableSharedFlow<BookshelfEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    val groupsFlow: SharedFlow<List<BookGroup>> = bookGroupRepository.flowShow()
        .onEach {
            if (it.isNotEmpty()) {
                isInitialLoadingFlow.value = false
            }
        }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    val allGroupsFlow: StateFlow<List<BookGroup>> = bookGroupRepository.flowAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 解锁态：进程内有效，重启应用即回到锁定。Eagerly 是为了点击时能同步读到当前值，
     * 决定"直接放行 / 弹生物框 / 弹密码框 / 引导设密码"。
     */
    private val privateAccessStateFlow: StateFlow<PrivateAccessState> =
        privateAccessGateway.state
            .stateIn(viewModelScope, SharingStarted.Eagerly, PrivateAccessState())

    private val privateAccessSettingsFlow: StateFlow<PrivateAccessSettings> =
        privateAccessGateway.settings
            .stateIn(viewModelScope, SharingStarted.Eagerly, PrivateAccessSettings())

    /** 解锁态与验证时机一起参与渲染，避免"关了验证却仍然显示锁定态"的分叉 */
    private val privateUiStateFlow: StateFlow<Pair<PrivateAccessState, PrivateAccessSettings>> =
        combine(privateAccessStateFlow, privateAccessSettingsFlow) { access, settings ->
            access to settings
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            PrivateAccessState() to PrivateAccessSettings()
        )

    /**
     * 私密判定所需的两个来源：私密分组掩码 + 被单独标记的书籍 url。
     * 判定在内存侧求并集，避免改动 BookDao 里十几处 BookShelfItem 投影。
     */
    private data class PrivateMarkers(
        val groupMask: Long,
        val bookUrls: Set<String>
    )

    private fun PrivateMarkers.isPrivate(item: BookShelfItem): Boolean =
        isPrivateBook(
            bookUrl = item.bookUrl,
            group = item.group,
            privateBookUrls = bookUrls,
            privateGroupMask = groupMask
        )

    private val privateMarkersFlow: StateFlow<PrivateMarkers> = combine(
        allGroupsFlow,
        privateContentGateway.flowPrivateBookUrls()
    ) { groups, privateBookUrls ->
        PrivateMarkers(
            groupMask = groups.fold(0L) { acc, group ->
                if (group.groupId > 0 && group.isPrivate) acc or group.groupId else acc
            },
            bookUrls = privateBookUrls
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PrivateMarkers(0L, emptySet()))

    private val hideEmptyGroupsFlow: StateFlow<Boolean> = bookshelfSettings
        .map { it.hideEmptyGroups }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            initialSettings.hideEmptyGroups
        )

    /**
     * 开启「隐藏空分组」时，返回当前书数为 0、应从分组列表中隐藏的 groupId 集合；
     * 关闭时始终为空集。「全部」分组永不隐藏，避免书架清空后无标签页可显示。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val hiddenGroupIdsFlow: SharedFlow<Set<Long>> = hideEmptyGroupsFlow
        .flatMapLatest { hide ->
            if (!hide) {
                flowOf(emptySet())
            } else {
                combine(
                    groupsFlow,
                    bookRepository.flowSystemGroupCounts()
                ) { groups, systemCounts ->
                    groups to systemCounts.associate { it.groupId to it.count }
                }.flatMapLatest { (groups, systemCountsMap) ->
                    val userGroups = groups.filter { it.groupId > 0 }
                    if (userGroups.isEmpty()) {
                        flowOf(computeHiddenGroupIds(groups, systemCountsMap, emptyMap()))
                    } else {
                        combine(
                            userGroups.map { group ->
                                bookRepository.flowUserGroupBookCount(group.groupId)
                                    .map { group.groupId to it }
                            }
                        ) { pairs ->
                            computeHiddenGroupIds(groups, systemCountsMap, pairs.toMap())
                        }
                    }
                }
            }
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    private fun computeHiddenGroupIds(
        groups: List<BookGroup>,
        systemCounts: Map<Long, Int>,
        userCounts: Map<Long, Int>
    ): Set<Long> = groups.mapNotNullTo(hashSetOf()) { group ->
        if (group.groupId == BookGroup.IdAll) return@mapNotNullTo null
        val count = if (group.groupId > 0) {
            userCounts[group.groupId] ?: 0
        } else {
            systemCounts[group.groupId] ?: 0
        }
        if (count == 0) group.groupId else null
    }

    private data class GroupPreviewState(
        val previews: ImmutableMap<Long, ImmutableList<BookUiItem>>,
        val counts: ImmutableMap<Long, Int>,
        val allBookCount: Int
    )

    private data class DataForPreviews(
        val groups: List<BookGroup>,
        val bookGroupStyle: Int,
        val systemCountsMap: Map<Long, Int>,
        val allBookCount: Int,
        val markers: PrivateMarkers
    )

    val groupSelectorState: StateFlow<BookshelfGroupSelectorState> = combine(
        groupsFlow,
        groupIdFlow,
        hiddenGroupIdsFlow
    ) { groups, selectedGroupId, hiddenIds ->
        val visibleGroups = groups.filter { it.groupId !in hiddenIds }
        BookshelfGroupSelectorState(
            groups = visibleGroups.map { it.toBookGroupUi() }.toImmutableList(),
            selectedGroupIndex = visibleGroups.indexOfFirst { it.groupId == selectedGroupId }
                .coerceAtLeast(0),
            selectedGroupId = selectedGroupId
        )
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BookshelfGroupSelectorState())

    private data class SelectedGroupBooksState(
        val groupId: Long,
        val books: List<BookUiItem>,
        val sortConfig: BookshelfSortConfig
    )

    private data class SelectedBooksState(
        val groupId: Long,
        val books: List<BookUiItem>,
        val visibleBooks: List<BookUiItem>,
        val searchKey: String,
        val isSearchMode: Boolean,
        val sortConfig: BookshelfSortConfig
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val selectedGroupBooksFlow: SharedFlow<SelectedGroupBooksState> = groupIdFlow
        .flatMapLatest { groupId ->
            combine(
                bookRepository.flowBookShelfByGroup(groupId),
                groupsFlow,
                sortConfigFlow,
                privateMarkersFlow
            ) { list, groups, sortConfig, markers ->
                SelectedGroupBooksState(
                    groupId = groupId,
                    books = bookshelfRepository.sortBooks(
                        list,
                        groups.find { it.groupId == groupId },
                        sortConfig.sort,
                        sortConfig.sortOrder
                    ).map { it.toUiItem(markers.isPrivate(it)) },
                    sortConfig = sortConfig
                )
            }
        }.distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    val booksFlow: Flow<List<BookUiItem>> = selectedGroupBooksFlow
        .map { it.books }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val allGroupBooksImmutableFlow: Flow<ImmutableMap<Long, ImmutableList<BookUiItem>>> =
        combine(groupsFlow, sortConfigFlow, privateMarkersFlow) { groups, sortConfig, markers ->
            Triple(groups, sortConfig, markers)
        }.flatMapLatest { (groups, sortConfig, markers) ->
            if (groups.isEmpty()) {
                flowOf(persistentMapOf())
            } else {
                val flows = groups.map { group ->
                    bookRepository.flowBookShelfByGroup(group.groupId).map { books ->
                        group.groupId to bookshelfRepository.sortBooks(
                            books,
                            group,
                            sortConfig.sort,
                            sortConfig.sortOrder
                        ).map { it.toUiItem(markers.isPrivate(it)) }.toImmutableList()
                    }
                }
                combine(flows) { results ->
                    results.fold(persistentMapOf<Long, ImmutableList<BookUiItem>>()) { acc, (id, list) ->
                        acc.putting(id, list)
                    }
                }
            }
        }.distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    private val selectedBooksStateFlow: Flow<SelectedBooksState> = combine(
        selectedGroupBooksFlow,
        searchKeyFlow,
        searchModeFlow,
        privateUiStateFlow
    ) { selectedGroup, searchKey, isSearchMode, privateUi ->
        // 未解锁时私密书籍不参与搜索匹配：否则"搜得到/搜不到"本身就泄漏了书名
        val (privateAccess, privateSettings) = privateUi
        SelectedBooksState(
            groupId = selectedGroup.groupId,
            books = selectedGroup.books,
            visibleBooks = filterBooks(
                books = selectedGroup.books,
                searchKey = searchKey,
                isSearchMode = isSearchMode,
                hidePrivate = { item ->
                    item.isLocked(privateAccess, privateSettings.verifyOnOpenBook)
                }
            ),
            searchKey = searchKey,
            isSearchMode = isSearchMode,
            sortConfig = selectedGroup.sortConfig
        )
    }.distinctUntilChanged()

    private val visibleBooksFlow: Flow<List<BookUiItem>> = selectedBooksStateFlow
        .map { it.visibleBooks }
        .distinctUntilChanged()

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
        bookRepository.flowAllBookShelfCount(),
        privateMarkersFlow
    ) { groups, bookGroupStyle, systemCounts, totalCount, markers ->
        DataForPreviews(
            groups,
            bookGroupStyle,
            systemCounts.associate { it.groupId to it.count },
            totalCount,
            markers
        )
    }.flatMapLatest { data ->
        val groups = data.groups
        val bookGroupStyle = data.bookGroupStyle
        val systemCountsMap = data.systemCountsMap
        val allBookCount = data.allBookCount
        val markers = data.markers

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
                    Triple(
                        group.groupId,
                        count,
                        preview.map { it.toUiItem(markers.isPrivate(it)) }
                    )
                }
            }
            combine(groupFlows) { results ->
                var previews = persistentMapOf<Long, ImmutableList<BookUiItem>>()
                var counts = persistentMapOf<Long, Int>()
                results.forEach { (groupId, count, preview) ->
                    counts = counts.putting(groupId, count)
                    previews = previews.putting(groupId, preview.toImmutableList())
                }
                GroupPreviewState(previews, counts, allBookCount)
            }
        }
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

    private val internalStateFlow = combine(
        groupIdFlow,
        loadingTextFlow,
        updatingBooksFlow,
        upBooksCountFlow,
        pendingOpenBookUrlFlow
    ) { groupId, loadingText, updatingBooks, upBooksCount, pendingOpenBookUrl ->
        InternalState(
            groupId = groupId,
            loadingText = loadingText,
            updatingBooks = updatingBooks,
            upBooksCount = upBooksCount,
            pendingOpenBookUrl = pendingOpenBookUrl
        )
    }

    private data class InternalState(
        val groupId: Long,
        val loadingText: String?,
        val updatingBooks: Set<String>,
        val upBooksCount: Int,
        val pendingOpenBookUrl: String?
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

    private val interactionStateFlow = combine(
        activeOverlayFlow,
        isEditModeFlow,
        selectedVisibleBookUrlsFlow,
        isInFolderRootFlow,
        isRefreshingFlow
    ) { activeOverlay, isEditMode, selectedBookUrls, isInFolderRoot, isRefreshing ->
        BookshelfInteractionState(
            activeOverlay = activeOverlay,
            isEditMode = isEditMode,
            selectedBookUrls = selectedBookUrls,
            isInFolderRoot = isInFolderRoot,
            isRefreshing = isRefreshing,
            bookGroupStyle = 0,
            draggingBooks = null,
            pendingSavedBooks = null
        )
    }.combine(
        combine(bookGroupStyleFlow, draggingBooksFlow, pendingSavedBooksFlow) { a, b, c ->
            Triple(a, b, c)
        }
    ) { interaction, (bookGroupStyle, draggingBooks, pendingSavedBooks) ->
        interaction.copy(
            bookGroupStyle = bookGroupStyle,
            draggingBooks = draggingBooks,
            pendingSavedBooks = pendingSavedBooks
        )
    }

    private val groupPreviewsStateFlow = MutableStateFlow(
        GroupPreviewState(persistentMapOf(), persistentMapOf(), 0)
    )

    private val dataStateFlow = combine(
        selectedBooksStateFlow,
        groupsFlow,
        allGroupsFlow,
        groupPreviewsStateFlow,
        internalStateFlow
    ) { selectedBooks, groups, allGroups, previews, internal ->
        BookshelfDataCore(selectedBooks, groups, allGroups, previews, internal)
    }.combine(allGroupBooksImmutableFlow) { core, allGroupBooks ->
        BookshelfDataState(
            selectedBooks = core.selectedBooks,
            groups = core.groups.map { it.toBookGroupUi() },
            allGroups = core.allGroups.map { it.toBookGroupUi() },
            previews = core.previews,
            internal = core.internal,
            allGroupBooks = allGroupBooks
        )
    }

    private data class BookshelfDataCore(
        val selectedBooks: SelectedBooksState,
        val groups: List<BookGroup>,
        val allGroups: List<BookGroup>,
        val previews: GroupPreviewState,
        val internal: InternalState
    )

    private data class BookshelfDataState(
        val selectedBooks: SelectedBooksState,
        val groups: List<BookGroupUi>,
        val allGroups: List<BookGroupUi>,
        val previews: GroupPreviewState,
        val internal: InternalState,
        val allGroupBooks: ImmutableMap<Long, ImmutableList<BookUiItem>>
    )

    private val contentUiState: Flow<BookshelfUiState> = combine(
        dataStateFlow,
        interactionStateFlow,
        isInitialLoadingFlow,
        hiddenGroupIdsFlow,
        privateUiStateFlow
    ) { data, interaction, isInitialLoading, hiddenIds, privateUi ->
        val (privateAccess, privateSettings) = privateUi
        val hidePrivateFromSearch: (BookUiItem) -> Boolean = { item ->
            item.isLocked(privateAccess, privateSettings.verifyOnOpenBook)
        }
        val selectedBooks = data.selectedBooks
        val groups = data.groups.filter { it.groupId !in hiddenIds }
        val allGroups = data.allGroups
        val previews = data.previews
        val internal = data.internal
        val visibleGroupBooks =
            if (!selectedBooks.isSearchMode || selectedBooks.searchKey.isBlank()) {
                data.allGroupBooks
            } else {
                data.allGroupBooks.mapValues { (_, books) ->
                    filterBooks(
                        books = books,
                        searchKey = selectedBooks.searchKey,
                        isSearchMode = true,
                        hidePrivate = hidePrivateFromSearch
                    ).toImmutableList()
                }.toImmutableMap()
            }
        val books = data.allGroupBooks[internal.groupId]
            ?: selectedBooks.books.takeIf { selectedBooks.groupId == internal.groupId }
            ?: emptyList()
        val filteredBooks = visibleGroupBooks[internal.groupId]
            ?: selectedBooks.visibleBooks.takeIf { selectedBooks.groupId == internal.groupId }
            ?: emptyList()
        val selectedGroupIndex = groups.indexOfFirst { it.groupId == internal.groupId }
            .coerceAtLeast(0)
        val currentGroup = allGroups.firstOrNull { it.groupId == internal.groupId }
            ?: groups.getOrNull(selectedGroupIndex)
        val currentGroupName = currentGroup?.groupName
        val selectedIds = interaction.selectedBookUrls.mapTo(linkedSetOf<Any>()) { it }
        val title = buildTitle(
            bookGroupStyle = interaction.bookGroupStyle,
            isInFolderRoot = interaction.isInFolderRoot,
            isEditMode = interaction.isEditMode,
            isSearchMode = selectedBooks.isSearchMode,
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
            searchKey = selectedBooks.searchKey,
            isSearch = selectedBooks.isSearchMode,
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
            bookshelfSort = selectedBooks.sortConfig.sort,
            bookshelfSortOrder = selectedBooks.sortConfig.sortOrder,
            title = title,
            subtitle = when {
                interaction.isEditMode -> {
                    val selectedText = context.getString(
                        R.string.bookshelf_selected_count,
                        interaction.selectedBookUrls.size
                    )
                    val groupTotalText = context.getString(
                        R.string.bookshelf_total_count,
                        books.size
                    )
                    context.getString(
                        R.string.bookshelf_edit_subtitle,
                        selectedText,
                        groupTotalText
                    )
                }

                selectedBooks.isSearchMode -> {
                    context.getString(R.string.bookshelf_total_count, filteredBooks.size)
                }

                else -> null
            },
            currentGroupName = currentGroupName,
            draggingBooks = interaction.draggingBooks?.toImmutableList(),
            pendingSavedBooks = interaction.pendingSavedBooks?.toImmutableList(),
            visibleGroupBooks = visibleGroupBooks,
            privateAccess = privateAccess,
            privateSettings = privateSettings,
            pendingOpenBookUrl = internal.pendingOpenBookUrl,
        )
    }

    val uiState: StateFlow<BookshelfUiState> = combine(
        contentUiState,
        bookshelfSettings,
        appShellSettingsGateway.settings,
        themeSettingsGateway.settings,
        pendingUploadUrlFlow,
    ) { state, settings, appShellSettings, themeSettings, pendingUploadUrl ->
        state.copy(
            settings = settings,
            useRaisedBottomInset = appShellSettings.useFloatingBottomBar || themeSettings.enableBlur,
            enableCustomTagColors = themeSettings.enableCustomTagColors,
            customTagColors = parseTagColors(themeSettings.customTagColorsJson),
            themeColor = themeSettings.themeColor,
            pendingUploadUrl = pendingUploadUrl,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        BookshelfUiState(
            settings = initialSettings,
            selectedGroupId = initialSettings.saveTabPosition,
            isInFolderRoot = initialSettings.bookGroupStyle == 2,
            bookGroupStyle = initialSettings.bookGroupStyle,
            useRaisedBottomInset = initialAppShellSettings.useFloatingBottomBar || initialThemeSettings.enableBlur,
            enableCustomTagColors = initialThemeSettings.enableCustomTagColors,
            customTagColors = parseTagColors(initialThemeSettings.customTagColorsJson),
            themeColor = initialThemeSettings.themeColor,
        ),
    )

    private fun parseTagColors(json: String?): ImmutableList<TagColorPair> = try {
        if (json.isNullOrBlank()) persistentListOf()
        else GSON.fromJson(json, Array<TagColorPair>::class.java).toImmutableList()
    } catch (_: Exception) {
        persistentListOf()
    }

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
            bookshelfSettings.collect { settings ->
                if (groupIdFlow.value != settings.saveTabPosition) {
                    groupIdFlow.value = settings.saveTabPosition
                    clearSelection()
                    clearDragState()
                }
                updateBookGroupStyle(settings.bookGroupStyle)
                postUpBooksCount()
            }
        }

        viewModelScope.launch {
            groupPreviewsFlow.collect { groupPreviewsStateFlow.value = it }
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
                if (bookshelfSettings.value.autoRefreshBook) {
                    upAllBookToc()
                }
            }
        }
    }

    fun onIntent(intent: BookshelfIntent) {
        when (intent) {
            is BookshelfIntent.ChangeGroup -> changeGroup(intent.groupId)
            is BookshelfIntent.SetSearchKey -> setSearchKey(intent.value)
            is BookshelfIntent.SetSearchMode -> setSearchMode(intent.active)
            is BookshelfIntent.ShowOverlay -> showOverlay(intent.overlay)
            BookshelfIntent.DismissOverlay -> dismissOverlay()
            BookshelfIntent.ToggleEditMode -> toggleEditMode()
            BookshelfIntent.ExitEditMode -> exitEditMode()
            BookshelfIntent.ClearSelection -> clearSelection()
            BookshelfIntent.SelectAllVisible -> selectAllVisible()
            BookshelfIntent.InvertVisibleSelection -> invertVisibleSelection()
            is BookshelfIntent.ToggleBookSelection -> toggleBookSelection(intent.bookUrl)
            is BookshelfIntent.SetInFolderRoot -> setInFolderRoot(intent.value)
            is BookshelfIntent.MoveBooksToGroup -> moveBooksToGroup(intent.bookUrls, intent.groupId)
            is BookshelfIntent.DownloadBooks -> downloadBooks(intent.bookUrls, intent.allChapters)
            is BookshelfIntent.RefreshBooks -> refreshBooks(intent.books)
            is BookshelfIntent.StartDragging -> startDraggingBooks(intent.books)
            is BookshelfIntent.MoveDragging -> moveDraggingBook(intent.from, intent.to, intent.books)
            BookshelfIntent.FinishDragging -> finishDraggingBooks()
            BookshelfIntent.ScrollToTop -> gotoTop()
            BookshelfIntent.RefreshAll -> upAllBookToc()
            is BookshelfIntent.RefreshToc -> upToc(intent.books)
            is BookshelfIntent.AddBookByUrl -> addBookByUrl(intent.urls)
            is BookshelfIntent.ExportToUri -> exportToUri(intent.uri, intent.books)
            is BookshelfIntent.UploadBookshelf -> uploadBookshelf(intent.books)
            is BookshelfIntent.ImportFromUri -> importBookshelf(intent.uri, intent.groupId)
            is BookshelfIntent.UpdateSetting -> viewModelScope.launch {
                bookshelfSettingsGateway.update(intent.transform)
            }
            is BookshelfIntent.SetCustomTagColorsEnabled -> viewModelScope.launch {
                themeSettingsGateway.update {
                    it.copy(enableCustomTagColors = intent.enabled)
                }
            }
            is BookshelfIntent.SetCustomTagColors -> viewModelScope.launch {
                themeSettingsGateway.update {
                    it.copy(customTagColorsJson = GSON.toJson(intent.colors))
                }
            }
            BookshelfIntent.UploadResultConsumed -> pendingUploadUrlFlow.value = null
            BookshelfIntent.ConsumePendingOpenBook -> pendingOpenBookUrlFlow.value = null
            is BookshelfIntent.RequestPrivateUnlock -> requestPrivateUnlock(intent.target)
            is BookshelfIntent.SubmitPrivatePassword ->
                submitPrivatePassword(intent.target, intent.password)

            is BookshelfIntent.UnlockPrivateWithBiometricPassword ->
                submitPrivatePassword(intent.target, intent.password)

            is BookshelfIntent.SetBooksPrivate ->
                setBooksPrivate(intent.bookUrls, intent.isPrivate)
        }
    }

    /**
     * 私密内容的统一入口：已解锁直接放行，否则按"生物快捷 → 应用内密码 → 引导设密码"降级。
     */
    private fun requestPrivateUnlock(target: PrivateUnlockTarget) {
        val access = privateAccessStateFlow.value
        if (target is PrivateUnlockTarget.Book) {
            // 打开书籍落成状态：解锁后列表才会重新包含这本书，此时再打开才不会丢事件
            pendingOpenBookUrlFlow.value = target.bookUrl
        }
        if (access.isUnlocked) return
        if (!access.hasPassword) {
            // 没有密码就没有解锁路径：把刚挂上的目标一起撤掉，
            // 否则以后任意一次授权（进分组、启动验证）都会让这本旧书被自动打开
            pendingOpenBookUrlFlow.value = null
            _effects.tryEmit(BookshelfEffect.NavigateToLocalPasswordSettings)
            return
        }
        if (access.canUseBiometricShortcut) {
            _effects.tryEmit(BookshelfEffect.RequestBiometricUnlock(target))
        } else {
            activeOverlayFlow.value = BookshelfOverlay.PrivatePassword(target)
        }
    }

    private fun submitPrivatePassword(target: PrivateUnlockTarget, password: String) {
        if (password.isEmpty()) return
        viewModelScope.launch {
            if (privateAccessGateway.verifyPassword(password, target)) {
                activeOverlayFlow.value = null
            } else {
                // 校验失败就把挂起动作一起清掉，避免之后某次成功解锁误打开旧目标
                pendingOpenBookUrlFlow.value = null
                _effects.tryEmit(
                    BookshelfEffect.ShowSnackbar(
                        context.getString(R.string.private_unlock_password_error)
                    )
                )
            }
        }
    }

    private fun setBooksPrivate(bookUrls: Set<String>, isPrivate: Boolean) {
        if (bookUrls.isEmpty()) return
        execute {
            privateContentGateway.setBooksPrivate(bookUrls, isPrivate)
        }.onSuccess {
            showMessage(context.getString(R.string.private_marked_books, bookUrls.size))
        }.onError {
            showMessage(
                context.getString(R.string.private_mark_failed, it.localizedMessage.orEmpty())
            )
        }
    }

    private fun filterBooks(
        books: List<BookUiItem>,
        searchKey: String,
        isSearchMode: Boolean,
        hidePrivate: (BookUiItem) -> Boolean = { false }
    ): List<BookUiItem> {
        // 仍处于锁定态的私密书籍先剔掉，再谈匹配：否则"能不能搜到"本身就是泄漏
        val candidates = if (isSearchMode && searchKey.isNotBlank()) {
            books.filterNot(hidePrivate)
        } else {
            books
        }
        return if (!isSearchMode || searchKey.isBlank()) {
            candidates
        } else {
            candidates.filter { it.matches(searchKey) }
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
            // "每次验证"频率下离开分组即撤销授权，下次进来要重新验证
            privateAccessGateway.revoke(PrivateUnlockTarget.Group(groupIdFlow.value))
            groupIdFlow.value = groupId
            viewModelScope.launch {
                bookshelfSettingsGateway.update { it.copy(saveTabPosition = groupId) }
            }
            clearSelection()
            clearDragState()
            // 切分组即放弃"解锁后打开"的挂起目标：这本书已经不在当前分组里了，
            // 留着它会在以后某次回到该分组时莫名把书弹开
            pendingOpenBookUrlFlow.value = null
        }
        // 进入私密分组不在这里申请权限：先呈现锁定页，由用户点"验证并查看"时再申请。
        // 否则左右滑动浏览分组会被连续弹窗打断，而用户此刻可能只是想路过这个分组。
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
        if (activeOverlayFlow.value is BookshelfOverlay.PrivatePassword) {
            pendingOpenBookUrlFlow.value = null
        }
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
            showMessage("更新分组失败\n${it.localizedMessage}")
        }
    }

    fun saveBookOrder(reorderedBooks: List<BookUiItem>) {
        if (reorderedBooks.isEmpty()) return
        val isDescending = bookshelfSettings.value.bookshelfSortOrder == 1
        val maxOrder = reorderedBooks.size
        execute {
            val updates = reorderedBooks.mapIndexedNotNull { index, bookUi ->
                bookRepository.getBook(bookUi.book.bookUrl)?.apply {
                    order = if (isDescending) maxOrder - index else index + 1
                }
            }
            if (updates.isNotEmpty()) {
                bookRepository.update(*updates.toTypedArray())
            }
        }.onError {
            showMessage("排序保存失败\n${it.localizedMessage}")
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
                showMessage("已加入缓存队列: $count 本")
            } else {
                showMessage(R.string.no_download)
            }
        }.onError {
            showMessage("批量缓存失败\n${it.localizedMessage}")
        }
    }

    fun refreshBooks(books: List<BookUiItem>) {
        if (isRefreshingFlow.value) return
        isRefreshingFlow.value = true
        val limit = bookshelfSettings.value.bookshelfRefreshingLimit
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
        _scrollTrigger.tryEmit(Unit)
    }
    fun upAllBookToc() {
        execute {
            addToWaitUp(bookRepository.getHasUpdateBooks())
        }
    }

    fun upToc(books: List<BookUiItem>) {
        val limit = bookshelfSettings.value.bookshelfRefreshingLimit
        val list = if (limit > 0) books.take(limit) else books
        enqueueTocUpdate(list.map { it.book }, resetRefreshWhenIdle = false)
    }

    private fun enqueueTocUpdate(
        books: List<BookShelfItem>,
        resetRefreshWhenIdle: Boolean
    ) {
        execute(context = updateDispatcher) {
            val bookUrls = books.filter { !it.isLocal && it.canUpdate }.map { it.bookUrl }
            val fullBooks = bookUrls.mapNotNull { bookRepository.getBook(it) }
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
        val count = if (bookshelfSettings.value.showWaitUpCount) {
            synchronized(updateQueueLock) {
                waitUpTocBooks.size + onUpTocBooks.size
            }
        } else {
            0
        }
        upBooksCountFlow.value = count
    }

    private fun addDownload(source: BookSource, book: Book) {
        if (downloadCacheSettingsGateway.currentSettings.preDownloadNum == 0) return
        val endIndex =
            min(
                book.totalChapterNum - 1,
                book.durChapterIndex + downloadCacheSettingsGateway.currentSettings.preDownloadNum
            )
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
        if (downloadCacheSettingsGateway.currentSettings.preDownloadNum == 0) return
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
                showMessage(R.string.success)
            } else {
                showMessage("添加网址失败")
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
            _effects.tryEmit(BookshelfEffect.ShowSnackbar("导出成功"))
        }.onError {
            _effects.tryEmit(BookshelfEffect.ShowSnackbar("导出失败\n${it.localizedMessage}"))
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
            pendingUploadUrlFlow.value = url
        }.onError {
            _effects.tryEmit(
                BookshelfEffect.ShowSnackbar(
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
            showMessage("导出书籍出错\n${it.localizedMessage}")
        }
    }

    fun importBookshelf(str: String, groupId: Long) {
        execute {
            importBookshelfUseCase.import(str, groupId) {
                loadingTextFlow.value = it
            }.getOrThrow()
        }.onSuccess {
            showMessage(R.string.success)
        }.onError {
            showMessage(it.localizedMessage ?: "ERROR")
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
            showMessage(R.string.success)
        }.onError {
            showMessage(it.localizedMessage ?: "ERROR")
        }.onFinally {
            loadingTextFlow.value = null
        }
    }

    private fun BookShelfItem.matchesSearchKey(searchKey: String): Boolean {
        return name.contains(searchKey, true) ||
                author.contains(searchKey, true) ||
                originName.contains(searchKey, true) ||
                kind?.contains(searchKey, true) == true ||
                customTag?.contains(searchKey, true) == true
    }

    private fun showMessage(resId: Int) = showMessage(context.getString(resId))

    private fun showMessage(message: String) {
        _effects.tryEmit(BookshelfEffect.ShowSnackbar(message))
    }

}
