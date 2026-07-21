package io.legado.app.ui.book.toc

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.base.BaseRuleViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.repository.ReadSettingsRepository
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.usecase.CacheBookChaptersUseCase
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.isLocal
import io.legado.app.help.bookmark.BookmarkExporter
import io.legado.app.model.CacheBook
import io.legado.app.model.ReadBook
import io.legado.app.model.cache.CacheBookDownloadState
import io.legado.app.model.localBook.LocalBook
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.list.ListUiState
import io.legado.app.ui.widget.components.list.SelectableItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class TocItemUi(
    override val id: Int,
    val title: String,
    val tag: String?,
    val isVolume: Boolean,
    val tocLevel: Int,
    val isVip: Boolean,
    val isPay: Boolean,
    val isDur: Boolean,
    val isSelected: Boolean,
    val downloadState: DownloadState,
    val wordCount: String?
) : SelectableItem<Int>

@Immutable
data class TocBookmarkItemUi(
    val id: Long,
    val chapterIndex: Int,
    val chapterPos: Int,
    val content: String,
    val chapterName: String,
    val isDur: Boolean,
    val raw: Bookmark
)

@Stable
data class TocActionState(
    override val items: ImmutableList<TocItemUi> = persistentListOf(),
    override val selectedIds: ImmutableSet<Int> = persistentSetOf(),
    override val searchKey: String = "",
    override val isSearch: Boolean = false,
    override val isLoading: Boolean = false,
    val downloadSummary: String = "",
    val useReplace: Boolean = false,
    val showWordCount: Boolean = true,
    val titleReplaceProgress: Float? = null,
) : ListUiState<TocItemUi>

@Stable
data class TocUiState(
    val action: TocActionState = TocActionState(),
    val book: Book? = null,
    val collapsedVolumes: ImmutableSet<Int> = persistentSetOf(),
    val bookmarks: ImmutableList<TocBookmarkItemUi> = persistentListOf(),
    val isSplitLongChapter: Boolean = false,
)

sealed interface TocIntent {
    data class SetSearchMode(val enabled: Boolean) : TocIntent
    data class SetSearchQuery(val query: String) : TocIntent
    data class ToggleVolume(val id: Int) : TocIntent
    data class ToggleSelection(val id: Int) : TocIntent
    data class SaveTocRegex(val regex: String) : TocIntent
    data class ExportBookmarks(val uri: Uri, val isMarkdown: Boolean) : TocIntent
    data class UpdateBookmark(val bookmark: Bookmark) : TocIntent
    data class DeleteBookmark(val bookmark: Bookmark) : TocIntent
    data class DownloadChapter(val id: Int) : TocIntent
    data object DownloadAll : TocIntent
    data object DownloadSelected : TocIntent
    data object SelectAll : TocIntent
    data object InvertSelection : TocIntent
    data object ClearSelection : TocIntent
    data object SelectFromLast : TocIntent
    data object AddBookmarksForSelected : TocIntent
    data object ToggleUseReplace : TocIntent
    data object ToggleShowWordCount : TocIntent
    data object ReverseToc : TocIntent
    data object ToggleSplitLongChapter : TocIntent
    data object ExpandAllVolumes : TocIntent
    data object CollapseAllVolumes : TocIntent
}

sealed interface TocEffect {
    data class ShowMessage(val message: String) : TocEffect
}

data class TocDomainItem(
    val chapter: BookChapter,
    val displayTitle: String,
    val downloadState: DownloadState
)

private data class DownloadContext(
    val downloadState: CacheBookDownloadState?,
    val cachedFiles: Set<String>
)

private data class TocUiConfig(
    val collapsedVolumes: Set<Int>,
    val useReplace: Boolean,
    val showWordCount: Boolean,
    val isReverse: Boolean,
    val defaultReplaceEnabled: Boolean,
    val chineseConverterType: Int,
)

private data class TocPreferences(
    val useReplace: Boolean,
    val showWordCount: Boolean
)

internal data class TitleCacheKey(
    val bookUrl: String,
    val useReplace: Boolean,
    val rulesFingerprint: Int,
    val chineseConverterType: Int,
    val chapterCount: Int,
    val chaptersFingerprint: Long
)

internal object TocTitleCache {
    private const val MAX_ENTRIES = 8
    private val entries = object : LinkedHashMap<TitleCacheKey, Map<Int, String>>(
        MAX_ENTRIES,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<TitleCacheKey, Map<Int, String>>?
        ): Boolean = size > MAX_ENTRIES
    }

    @Synchronized
    fun get(key: TitleCacheKey): Map<Int, String>? = entries[key]

    @Synchronized
    fun put(key: TitleCacheKey, titles: Map<Int, String>) {
        entries[key] = titles.toMap()
    }

    @Synchronized
    fun clear() = entries.clear()
}

private data class TitleReplaceState(
    val cacheKey: TitleCacheKey? = null,
    val titles: Map<Int, String> = emptyMap(),
    val completed: Int = 0,
    val total: Int = 0,
    val isRunning: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class TocViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val cacheBookChaptersUseCase: CacheBookChaptersUseCase,
    private val readSettingsRepository: ReadSettingsRepository,
    private val otherSettingsGateway: OtherSettingsGateway,
) : BaseRuleViewModel<TocItemUi, TocDomainItem, Int, TocActionState>(
    application,
    initialState = TocActionState()
) {

    private val bookUrlFlow = savedStateHandle.getStateFlow<String?>("bookUrl", null)
    val bookState = bookUrlFlow
        .filterNotNull()
        .flatMapLatest { url ->
            appDb.bookDao.flowGetBook(url)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isSplitLongChapter: Boolean get() = bookState.value?.getSplitLongChapter() ?: false

    private val _collapsedVolumes = MutableStateFlow<Set<Int>>(emptySet())
    val collapsedVolumes = _collapsedVolumes.asStateFlow()
    private val _effects = MutableSharedFlow<TocEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    val downloadSummary: StateFlow<String> =
        CacheBook.downloadSummaryFlow
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                ""
            )

    private val _cacheFileNames: StateFlow<Set<String>> = bookState.filterNotNull()
        .map { it.bookUrl }
        .distinctUntilChanged()
        .flatMapLatest { url ->
            val initialFiles = withContext(Dispatchers.IO) {
                BookHelp.getChapterFiles(bookState.value!!)
            }.toSet()

            CacheBook.cacheSuccessFlow
                .filter { it.bookUrl == url }
                .map { it.getFileName() }
                .scan(initialFiles) { accumulator, newFileName ->
                    accumulator + newFileName
                }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptySet()
        )

    val bookmarkUiList: StateFlow<List<TocBookmarkItemUi>> =
        combine(
            bookState.filterNotNull(),
            _searchKey
        ) { book, query ->
            book to query
        }
            .flatMapLatest { (book, query) ->
                appDb.bookmarkDao
                    .flowByBook(book.name, book.author)
                    .map { list ->
                        list
                            .asSequence()
                            .filter {
                                query.isBlank() ||
                                        it.content.contains(query, ignoreCase = true)
                            }
                            .map { bookmark ->
                                TocBookmarkItemUi(
                                    id = bookmark.time,
                                    chapterIndex = bookmark.chapterIndex,
                                    chapterPos = bookmark.chapterPos,
                                    content = bookmark.content,
                                    chapterName = bookmark.chapterName,
                                    isDur = bookmark.chapterIndex == book.durChapterIndex,
                                    raw = bookmark
                                )
                            }
                            .toList()
                    }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val screenState: StateFlow<TocUiState> by lazy {
        combine(
            uiState,
            bookState,
            collapsedVolumes,
            bookmarkUiList,
        ) { action, book, collapsed, bookmarks ->
            TocUiState(
                action = action,
                book = book,
                collapsedVolumes = collapsed.toImmutableSet(),
                bookmarks = bookmarks.toImmutableList(),
                isSplitLongChapter = book?.getSplitLongChapter() ?: false,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TocUiState(),
        )
    }

    private val reverseFlow =
        bookState.map { it?.getReverseToc() ?: false }
            .distinctUntilChanged()

    private val tocPreferences = readSettingsRepository.preferences
        .map {
            TocPreferences(
                useReplace = it.tocUiUseReplace,
                showWordCount = it.tocCountWords
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TocPreferences(useReplace = false, showWordCount = true)
        )

    private val downloadContextFlow = combine(
        bookState.filterNotNull().map { it.bookUrl }.distinctUntilChanged(),
        CacheBook.downloadStateFlow,
        _cacheFileNames
    ) { bookUrl, state, cached ->
        DownloadContext(state.books[bookUrl], cached)
    }

    private val uiConfigFlow = combine(
        _collapsedVolumes,
        tocPreferences,
        reverseFlow,
        otherSettingsGateway.settings,
        readSettingsRepository.settings,
    ) { collapsed, tocPreferences, isReverse, otherSettings, readSettings ->
        TocUiConfig(
            collapsedVolumes = collapsed,
            useReplace = tocPreferences.useReplace,
            showWordCount = tocPreferences.showWordCount,
            isReverse = isReverse,
            defaultReplaceEnabled = otherSettings.replaceEnableDefault,
            chineseConverterType = readSettings.chineseConverterType,
        )
    }

    private val titleReplaceState = MutableStateFlow(TitleReplaceState())
    private var titleCacheJob: Job? = null
    private var lastTitleCacheKey: TitleCacheKey? = null

    override val rawDataFlow: Flow<List<TocDomainItem>> = combine(
        bookState.filterNotNull().map { it.bookUrl }.distinctUntilChanged()
            .flatMapLatest { appDb.bookChapterDao.getChapterListFlow(it) },
        downloadContextFlow,
        uiConfigFlow,
        titleReplaceState
    ) { originalChapters, downloadCtx, config, titleState ->
        val book = bookState.value ?: return@combine emptyList()

        val processedChapters = if (config.isReverse) {
            originalChapters.reverseTocHierarchy()
        } else {
            originalChapters
        }

        val replaceRules = if (
            config.useReplace && book.getUseReplaceRule(config.defaultReplaceEnabled)
        ) {
            ContentProcessor.get(book.name, book.origin).getTitleReplaceRules()
        } else emptyList()

        updateTitleReplaceCacheIfNeeded(
            book = book,
            chapters = processedChapters,
            replaceRules = replaceRules,
            useReplace = config.useReplace,
            defaultReplaceEnabled = config.defaultReplaceEnabled,
            chineseConverterType = config.chineseConverterType,
        )

        if (book.isLocal) {
            return@combine processedChapters.map { chapter ->
                val baseTitle = chapter.getDisplayTitle(
                    useReplace = false,
                    chineseConverterType = config.chineseConverterType,
                )
                TocDomainItem(
                    chapter = chapter,
                    displayTitle = titleState.titles[chapter.index] ?: baseTitle,
                    downloadState = DownloadState.LOCAL
                )
            }
        }

        val runningIndices = downloadCtx.downloadState?.runningIndices.orEmpty()
        val errorIndices = downloadCtx.downloadState?.failedIndices.orEmpty()
        val cachedFiles = downloadCtx.cachedFiles

        processedChapters.map { chapter ->
            val downloadState = when {
                chapter.index in runningIndices -> DownloadState.DOWNLOADING
                chapter.index in errorIndices -> DownloadState.ERROR
                chapter.getFileName() in cachedFiles -> DownloadState.SUCCESS
                else -> DownloadState.NONE
            }

            val baseTitle = chapter.getDisplayTitle(
                useReplace = false,
                chineseConverterType = config.chineseConverterType,
            )
            TocDomainItem(
                chapter,
                titleState.titles[chapter.index] ?: baseTitle,
                downloadState
            )
        }

    }.flowOn(Dispatchers.Default)

    val useReplace get() = tocPreferences.value.useReplace
    val showWordCount get() = tocPreferences.value.showWordCount

    override fun filterData(data: List<TocDomainItem>, key: String): List<TocDomainItem> {
        val collapsed = _collapsedVolumes.value
        val isSearch = key.isNotBlank()

        val visibleItems = if (isSearch) data else filterCollapsedToc(data, collapsed)
        return visibleItems.filter {
            !isSearch || it.displayTitle.contains(key, true) || it.chapter.isVolume
        }
    }

    override fun composeUiState(
        items: List<TocItemUi>,
        selectedIds: Set<Int>,
        isSearch: Boolean,
        isUploading: Boolean,
        importState: BaseImportUiState<TocDomainItem>
    ): TocActionState {

        val durIndex = bookState.value?.durChapterIndex ?: -1

        val updatedItems = items.map { uiItem ->
            uiItem.copy(
                isSelected = uiItem.id in selectedIds,
                isDur = uiItem.id == durIndex
            )
        }

        return TocActionState(
            items = updatedItems.toImmutableList(),
            selectedIds = selectedIds.toImmutableSet(),
            searchKey = _searchKey.value,
            isSearch = isSearch,
            isLoading = isUploading,
            downloadSummary = downloadSummary.value,
            useReplace = tocPreferences.value.useReplace,
            showWordCount = tocPreferences.value.showWordCount,
            titleReplaceProgress = titleReplaceState.value
                .takeIf { it.isRunning && it.total > 0 }
                ?.let { it.completed.toFloat() / it.total },
        )
    }

    override fun TocDomainItem.toUiItem(): TocItemUi {
        val wordCountText = if (showWordCount) {
            chapter.wordCount
        } else {
            null
        }

        return TocItemUi(
            id = chapter.index,
            title = displayTitle,
            tag = chapter.tag,
            isVolume = chapter.isVolume,
            tocLevel = chapter.tocLevel,
            isVip = chapter.isVip,
            isPay = chapter.isPay,
            isDur = false,
            isSelected = false,
            downloadState = downloadState,
            wordCount = wordCountText
        )
    }

    override fun ruleItemToEntity(item: TocItemUi): TocDomainItem {
        throw NotImplementedError("TOC 不需要向后反转实体")
    }

    override suspend fun generateJson(entities: List<TocDomainItem>) = ""
    override fun parseImportRules(text: String): List<TocDomainItem> = emptyList()
    override fun hasChanged(newRule: TocDomainItem, oldRule: TocDomainItem) = false
    override suspend fun findOldRule(newRule: TocDomainItem) = null
    override fun saveImportedRules() {}

    fun onIntent(intent: TocIntent) {
        when (intent) {
            is TocIntent.SetSearchMode -> setSearchMode(intent.enabled)
            is TocIntent.SetSearchQuery -> setSearchKey(intent.query)
            is TocIntent.ToggleVolume -> toggleVolume(intent.id)
            is TocIntent.ToggleSelection -> toggleSelection(intent.id)
            is TocIntent.SaveTocRegex -> saveTocRegex(intent.regex)
            is TocIntent.ExportBookmarks -> exportCurrentBookBookmarks(intent.uri, intent.isMarkdown)
            is TocIntent.UpdateBookmark -> updateBookmark(intent.bookmark)
            is TocIntent.DeleteBookmark -> deleteBookmark(intent.bookmark)
            is TocIntent.DownloadChapter -> downloadChapter(intent.id)
            TocIntent.DownloadAll -> downloadAll()
            TocIntent.DownloadSelected -> downloadSelected()
            TocIntent.SelectAll -> selectAll()
            TocIntent.InvertSelection -> invertSelection()
            TocIntent.ClearSelection -> clearSelection()
            TocIntent.SelectFromLast -> selectFromLast()
            TocIntent.AddBookmarksForSelected -> addBookmarksForSelected()
            TocIntent.ToggleUseReplace -> toggleUseReplace()
            TocIntent.ToggleShowWordCount -> toggleShowWordCount()
            TocIntent.ReverseToc -> reverseToc()
            TocIntent.ToggleSplitLongChapter -> toggleSplitLongChapter()
            TocIntent.ExpandAllVolumes -> expandAllVolumes()
            TocIntent.CollapseAllVolumes -> collapseAllVolumes()
        }
    }

    fun reverseToc() = execute {
        val currentBook = bookState.value ?: return@execute
        val currentConfig = currentBook.readConfig ?: Book.ReadConfig()
        val newConfig = currentConfig.copy(reverseToc = !currentConfig.reverseToc)
        val newBook = currentBook.copy(readConfig = newConfig)
        appDb.bookDao.update(newBook)
        //bookState.value = newBook
    }

    fun toggleUseReplace() {
        viewModelScope.launch {
            readSettingsRepository.setTocUiUseReplace(!tocPreferences.value.useReplace)
        }
    }

    fun toggleShowWordCount() {
        viewModelScope.launch {
            readSettingsRepository.setTocCountWords(!tocPreferences.value.showWordCount)
        }
    }

    fun toggleVolume(volumeIndex: Int) {
        _collapsedVolumes.update { current ->
            if (current.contains(volumeIndex)) current - volumeIndex else current + volumeIndex
        }
    }

    fun expandAllVolumes() {
        _collapsedVolumes.value = emptySet()
    }

    fun collapseAllVolumes() = execute {
        val bookUrl = bookState.value?.bookUrl ?: return@execute
        val volumes =
            appDb.bookChapterDao.getChapterList(bookUrl).filter { it.isVolume }.map { it.index }
                .toSet()
        _collapsedVolumes.value = volumes
    }

    fun selectAll() {
        setSelection(uiState.value.items.map { it.id }.toSet())
    }

    fun invertSelection() {
        val allIds = uiState.value.items.map { it.id }.toSet()
        setSelection(allIds - _selectedIds.value)
    }

    fun clearSelection() {
        setSelection(emptySet())
    }

    fun selectFromLast() {
        val currentItems = uiState.value.items
        val maxSelectedId = _selectedIds.value.maxOrNull() ?: return
        val maxIndex = currentItems.indexOfFirst { it.id == maxSelectedId }
        if (maxIndex == -1) return
        setSelection(_selectedIds.value + currentItems.drop(maxIndex + 1).map { it.id })
    }

    fun saveTocRegex(newRegex: String) {
        val book = bookState.value ?: return
        book.tocUrl = newRegex
        upBookTocRule(book) { error ->
            if (error != null) {
                showMessage(context.getString(R.string.toc_rule_update_failed, error.localizedMessage))
            }
            else {
                showMessage(R.string.toc_rule_updated)
                if (ReadBook.book?.bookUrl == book.bookUrl) ReadBook.upMsg(null)
            }
        }
    }

    fun toggleSplitLongChapter() {
        val book = bookState.value ?: return
        val newState = !isSplitLongChapter
        book.setSplitLongChapter(newState)
        upBookTocRule(book) { error ->
            if (error != null) {
                showMessage(context.getString(R.string.setting_failed, error.localizedMessage))
            } else {
                showMessage(
                    if (newState) R.string.split_long_chapters_enabled
                    else R.string.split_long_chapters_disabled
                )
            }
        }
    }

    private fun upBookTocRule(book: Book, complete: (Throwable?) -> Unit) {
        _isUploading.value = true
        execute {
            appDb.bookDao.update(book)
            LocalBook.getChapterList(book).let { chapters ->
                appDb.bookChapterDao.delByBook(book.bookUrl)
                appDb.bookChapterDao.insert(*chapters.toTypedArray())
                appDb.bookDao.update(book)
                ReadBook.onChapterListUpdated(book)
                //bookState.value = book
            }
        }.onSuccess {
            _isUploading.value = false
            complete.invoke(null)
        }.onError {
            _isUploading.value = false
            complete.invoke(it)
        }
    }

    fun exportCurrentBookBookmarks(fileUri: Uri, isMd: Boolean) = viewModelScope.launch {
        try {
            val book = bookState.value ?: return@launch
            val bookmarks = appDb.bookmarkDao.getByBook(book.name, book.author)
            if (bookmarks.isEmpty()) {
                showMessage(R.string.no_bookmarks_to_export)
                return@launch
            }
            BookmarkExporter.exportToUri(
                context = getApplication(), fileUri = fileUri, bookmarks = bookmarks,
                isMd = isMd, bookName = book.name, author = book.author
            )
            showMessage(R.string.save_success)
        } catch (e: Exception) {
            showMessage(context.getString(R.string.save_failed_with_error, e.message))
        }
    }

    fun updateBookmark(bookmark: Bookmark) =
        viewModelScope.launch(Dispatchers.IO) { appDb.bookmarkDao.insert(bookmark) }

    fun deleteBookmark(bookmark: Bookmark) =
        viewModelScope.launch(Dispatchers.IO) { appDb.bookmarkDao.delete(bookmark) }

    fun addBookmarksForSelected() = viewModelScope.launch(Dispatchers.IO) {
        val book = bookState.value ?: return@launch
        val selectedItems = uiState.value.items
            .asSequence()
            .filter { it.id in uiState.value.selectedIds }
            .filterNot { it.isVolume }
            .toList()

        if (selectedItems.isEmpty()) {
            showMessage(R.string.select_chapters)
            return@launch
        }

        val bookmarks = selectedItems.map { item ->
            Bookmark(
                bookName = book.name,
                bookAuthor = book.author,
                chapterIndex = item.id,
                chapterPos = 0,
                chapterName = item.title,
                bookText = "",
                content = ""
            )
        }

        appDb.bookmarkDao.insert(*bookmarks.toTypedArray())
        showMessage(context.getString(R.string.bookmarks_added_count, bookmarks.size))
        withContext(Dispatchers.Main) {
            clearSelection()
        }
    }

    fun downloadSelected() {
        val book = bookState.value ?: return
        val indices = uiState.value.selectedIds.toList()
        if (indices.isEmpty()) return
        execute {
            cacheBookChaptersUseCase.execute(book.bookUrl, indices)
        }.onSuccess { count ->
            showMessage(context.getString(R.string.start_downloading_chapters, count))
            clearSelection()
        }
    }

    fun downloadChapter(index: Int) {
        val book = bookState.value ?: return
        execute {
            cacheBookChaptersUseCase.execute(book.bookUrl, listOf(index))
        }.onSuccess {
            showMessage(R.string.start_downloading_chapter)
        }
    }

    fun downloadAll() {
        val book = bookState.value ?: return
        val targetIndices = uiState.value.items
            .filter { !it.isVolume && it.downloadState != DownloadState.SUCCESS }
            .map { it.id }

        if (targetIndices.isEmpty()) {
            showMessage(R.string.all_chapters_cached)
            return
        }

        execute {
            cacheBookChaptersUseCase.execute(book.bookUrl, targetIndices)
        }.onSuccess { count ->
            showMessage(context.getString(R.string.start_downloading_remaining_chapters, count))
        }
    }

    private fun showMessage(resId: Int) = showMessage(context.getString(resId))

    private fun showMessage(message: String) {
        _effects.tryEmit(TocEffect.ShowMessage(message))
    }

    private fun updateTitleReplaceCacheIfNeeded(
        book: Book,
        chapters: List<BookChapter>,
        replaceRules: List<ReplaceRule>,
        useReplace: Boolean,
        defaultReplaceEnabled: Boolean,
        chineseConverterType: Int,
    ) {
        val shouldUseReplace = useReplace &&
                book.getUseReplaceRule(defaultReplaceEnabled) &&
                replaceRules.isNotEmpty()
        if (!shouldUseReplace) {
            titleCacheJob?.cancel()
            titleCacheJob = null
            lastTitleCacheKey = null
            if (titleReplaceState.value != TitleReplaceState()) {
                titleReplaceState.value = TitleReplaceState()
            }
            return
        }

        val rulesFingerprint = replaceRules.fold(1) { acc, rule ->
            var hash = acc
            hash = 31 * hash + rule.id.hashCode()
            hash = 31 * hash + rule.pattern.hashCode()
            hash = 31 * hash + rule.replacement.hashCode()
            hash = 31 * hash + rule.isRegex.hashCode()
            hash = 31 * hash + rule.timeoutMillisecond.hashCode()
            hash
        }

        val key = TitleCacheKey(
            bookUrl = book.bookUrl,
            useReplace = true,
            rulesFingerprint = rulesFingerprint,
            chineseConverterType = chineseConverterType,
            chapterCount = chapters.size,
            chaptersFingerprint = chapters.fold(0L) { fingerprint, chapter ->
                fingerprint + 31L * chapter.index + chapter.title.hashCode()
            }
        )

        val currentTitleState = titleReplaceState.value
        val isJobActive = titleCacheJob?.isActive == true
        val isCurrentCacheReady =
            currentTitleState.cacheKey == key &&
                    currentTitleState.completed == chapters.size
        if (key == lastTitleCacheKey &&
            (isJobActive || currentTitleState.isRunning || isCurrentCacheReady)
        ) {
            return
        }

        TocTitleCache.get(key)?.let { cachedTitles ->
            lastTitleCacheKey = key
            titleCacheJob?.cancel()
            titleCacheJob = null
            titleReplaceState.value = TitleReplaceState(
                cacheKey = key,
                titles = cachedTitles,
                completed = chapters.size,
                total = chapters.size,
                isRunning = false
            )
            return
        }

        lastTitleCacheKey = key
        titleCacheJob?.cancel()
        titleReplaceState.value = TitleReplaceState(
            cacheKey = key,
            total = chapters.size,
            isRunning = chapters.isNotEmpty()
        )
        if (chapters.isEmpty()) {
            titleCacheJob = null
            return
        }

        titleCacheJob = viewModelScope.launch(Dispatchers.Default) {
            val newCache = HashMap<Int, String>(chapters.size)
            val workerCount = minOf(TITLE_REPLACE_WORKER_COUNT, chapters.size)
            val publishBatchSize =
                (chapters.size / MAX_TITLE_REPLACE_PROGRESS_UPDATES).coerceAtLeast(1)
            var completed = 0
            var lastPublishedCompleted = 0
            var lastPublishedAt = System.nanoTime()

            chapters.asFlow()
                .flatMapMerge(concurrency = workerCount) { chapter ->
                    flow {
                        emit(
                            chapter.index to chapter.getDisplayTitle(
                                replaceRules,
                                true,
                                chineseConverterType = chineseConverterType,
                            )
                        )
                    }
                }
                .collect { (chapterIndex, displayTitle) ->
                    newCache[chapterIndex] = displayTitle
                    completed++
                    val now = System.nanoTime()
                    val shouldPublish = completed < chapters.size &&
                            (completed - lastPublishedCompleted >= publishBatchSize ||
                                    now - lastPublishedAt >= TITLE_REPLACE_UPDATE_INTERVAL_NANOS)
                    if (shouldPublish) {
                        titleReplaceState.update { current ->
                            if (current.cacheKey != key) {
                                current
                            } else {
                                TitleReplaceState(
                                    cacheKey = key,
                                    titles = HashMap(newCache),
                                    completed = completed,
                                    total = chapters.size,
                                    isRunning = true
                                )
                            }
                        }
                        lastPublishedCompleted = completed
                        lastPublishedAt = now
                    }
                }

            titleReplaceState.update { current ->
                if (current.cacheKey != key) {
                    current
                } else {
                    TitleReplaceState(
                        cacheKey = key,
                        titles = newCache,
                        completed = chapters.size,
                        total = chapters.size,
                        isRunning = false
                    )
                }
            }
            TocTitleCache.put(key, newCache)
        }
    }

    private companion object {
        const val TITLE_REPLACE_WORKER_COUNT = 4
        const val MAX_TITLE_REPLACE_PROGRESS_UPDATES = 100
        const val TITLE_REPLACE_UPDATE_INTERVAL_NANOS = 100_000_000L
    }
}
