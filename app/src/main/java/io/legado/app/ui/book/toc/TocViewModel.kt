package io.legado.app.ui.book.toc

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseRuleViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.domain.usecase.CacheBookChaptersUseCase
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.isLocal
import io.legado.app.help.bookmark.BookmarkExporter
import io.legado.app.help.config.AppConfig
import io.legado.app.model.CacheBook
import io.legado.app.model.ReadBook
import io.legado.app.model.cache.CacheBookDownloadState
import io.legado.app.model.localBook.LocalBook
import io.legado.app.ui.config.readConfig.ReadConfig
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.list.ListUiState
import io.legado.app.ui.widget.components.list.SelectableItem
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
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

data class TocActionState(
    override val items: List<TocItemUi> = emptyList(),
    override val selectedIds: Set<Int> = emptySet(),
    override val searchKey: String = "",
    override val isSearch: Boolean = false,
    override val isLoading: Boolean = false,
    val downloadSummary: String = ""
) : ListUiState<TocItemUi>

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
    val isReverse: Boolean
)

private data class TitleCacheKey(
    val bookUrl: String,
    val useReplace: Boolean,
    val rulesFingerprint: Int,
    val chineseConverterType: Int,
    val chapterCount: Int
)

data class FabAction(val icon: ImageVector, val label: String, val action: () -> Unit)

@OptIn(ExperimentalCoroutinesApi::class)
class TocViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val cacheBookChaptersUseCase: CacheBookChaptersUseCase
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

    private val reverseFlow =
        bookState.map { it?.getReverseToc() ?: false }
            .distinctUntilChanged()

    private val downloadContextFlow = combine(
        bookState.filterNotNull().map { it.bookUrl }.distinctUntilChanged(),
        CacheBook.downloadStateFlow,
        _cacheFileNames
    ) { bookUrl, state, cached ->
        DownloadContext(state.books[bookUrl], cached)
    }

    private val uiConfigFlow = combine(
        _collapsedVolumes,
        snapshotFlow { ReadConfig.tocUiUseReplace },
        snapshotFlow { ReadConfig.tocCountWords },
        reverseFlow
    ) { collapsed, useReplace, showWordCount, isReverse ->
        TocUiConfig(collapsed, useReplace, showWordCount, isReverse)
    }

    private val titleReplaceCache = MutableStateFlow<Map<Int, String>>(emptyMap())
    private var titleCacheJob: Job? = null
    private var lastTitleCacheKey: TitleCacheKey? = null

    override val rawDataFlow: Flow<List<TocDomainItem>> = combine(
        bookState.filterNotNull().map { it.bookUrl }.distinctUntilChanged()
            .flatMapLatest { appDb.bookChapterDao.getChapterListFlow(it) },
        downloadContextFlow,
        uiConfigFlow,
        titleReplaceCache
    ) { originalChapters, downloadCtx, config, cachedTitles ->
        val book = bookState.value ?: return@combine emptyList()

        val processedChapters = if (config.isReverse) {
            originalChapters.groupAndReverseVolumes()
        } else {
            originalChapters
        }

        val replaceRules = if (config.useReplace && book.getUseReplaceRule()) {
            ContentProcessor.get(book.name, book.origin).getTitleReplaceRules()
        } else emptyList()

        updateTitleReplaceCacheIfNeeded(
            book = book,
            chapters = processedChapters,
            replaceRules = replaceRules,
            useReplace = config.useReplace
        )

        if (book.isLocal) {
            return@combine processedChapters.map { chapter ->
                val baseTitle = chapter.getDisplayTitle(useReplace = false)
                TocDomainItem(
                    chapter = chapter,
                    displayTitle = cachedTitles[chapter.index] ?: baseTitle,
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

            val baseTitle = chapter.getDisplayTitle(useReplace = false)
            TocDomainItem(
                chapter,
                cachedTitles[chapter.index] ?: baseTitle,
                downloadState
            )
        }

    }.flowOn(Dispatchers.Default)

    val useReplace get() = ReadConfig.tocUiUseReplace
    val showWordCount get() = ReadConfig.tocCountWords

    override fun filterData(data: List<TocDomainItem>, key: String): List<TocDomainItem> {
        val collapsed = _collapsedVolumes.value
        val isSearch = key.isNotBlank()

        return buildList {
            var isCurrentVolumeCollapsed = false
            for (item in data) {
                if (item.chapter.isVolume) {
                    isCurrentVolumeCollapsed = collapsed.contains(item.chapter.index)
                } else if (isCurrentVolumeCollapsed && !isSearch) {
                    continue
                }

                if (!isSearch || item.displayTitle.contains(key, true) || item.chapter.isVolume) {
                    add(item)
                }
            }
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
            items = updatedItems,
            selectedIds = selectedIds,
            searchKey = _searchKey.value,
            isSearch = isSearch,
            isLoading = isUploading,
            downloadSummary = downloadSummary.value
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

    fun reverseToc() = execute {
        val currentBook = bookState.value ?: return@execute
        val currentConfig = currentBook.readConfig ?: Book.ReadConfig()
        val newConfig = currentConfig.copy(reverseToc = !currentConfig.reverseToc)
        val newBook = currentBook.copy(readConfig = newConfig)
        appDb.bookDao.update(newBook)
        //bookState.value = newBook
    }

    fun toggleUseReplace() {
        ReadConfig.tocUiUseReplace = !ReadConfig.tocUiUseReplace
    }

    fun toggleShowWordCount() {
        ReadConfig.tocCountWords = !ReadConfig.tocCountWords
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
            if (error != null) context.toastOnUi("更新目录规则失败: ${error.localizedMessage}")
            else {
                context.toastOnUi("目录规则已更新")
                if (ReadBook.book?.bookUrl == book.bookUrl) ReadBook.upMsg(null)
            }
        }
    }

    fun toggleSplitLongChapter() {
        val book = bookState.value ?: return
        val newState = !isSplitLongChapter
        book.setSplitLongChapter(newState)
        upBookTocRule(book) { error ->
            if (error != null) context.toastOnUi("设置失败: ${error.localizedMessage}")
            else context.toastOnUi(if (newState) "已开启长章节拆分" else "已关闭长章节拆分")
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
                context.toastOnUi("没有可导出的书签")
                return@launch
            }
            BookmarkExporter.exportToUri(
                context = getApplication(), fileUri = fileUri, bookmarks = bookmarks,
                isMd = isMd, bookName = book.name, author = book.author
            )
            context.toastOnUi("保存成功")
        } catch (e: Exception) {
            context.toastOnUi("保存失败: ${e.message}")
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
            context.toastOnUi("请选择章节")
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
        context.toastOnUi("已添加 ${bookmarks.size} 个书签")
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
            getApplication<Application>().toastOnUi("开始下载 $count 个章节")
            clearSelection()
        }
    }

    fun downloadChapter(index: Int) {
        val book = bookState.value ?: return
        execute {
            cacheBookChaptersUseCase.execute(book.bookUrl, listOf(index))
        }.onSuccess {
            getApplication<Application>().toastOnUi("开始下载章节")
        }
    }

    fun downloadAll() {
        val book = bookState.value ?: return
        val targetIndices = uiState.value.items
            .filter { !it.isVolume && it.downloadState != DownloadState.SUCCESS }
            .map { it.id }

        if (targetIndices.isEmpty()) {
            getApplication<Application>().toastOnUi("所有章节已缓存")
            return
        }

        execute {
            cacheBookChaptersUseCase.execute(book.bookUrl, targetIndices)
        }.onSuccess { count ->
            getApplication<Application>().toastOnUi("开始下载剩余 $count 个章节")
        }
    }

    private fun List<BookChapter>.groupAndReverseVolumes(): List<BookChapter> {
        return this.fold(mutableListOf<MutableList<BookChapter>>()) { acc, chapter ->
            if (chapter.isVolume || acc.isEmpty()) acc.add(mutableListOf(chapter))
            else acc.last().add(chapter)
            acc
        }.asReversed().flatMap { group ->
            if (group.firstOrNull()?.isVolume == true) {
                listOf(group.first()) + group.drop(1).asReversed()
            } else {
                group.asReversed()
            }
        }
    }

    private fun updateTitleReplaceCacheIfNeeded(
        book: Book,
        chapters: List<BookChapter>,
        replaceRules: List<ReplaceRule>,
        useReplace: Boolean
    ) {
        val shouldUseReplace = useReplace && book.getUseReplaceRule() && replaceRules.isNotEmpty()
        if (!shouldUseReplace) {
            titleCacheJob?.cancel()
            titleCacheJob = null
            lastTitleCacheKey = null
            if (titleReplaceCache.value.isNotEmpty()) {
                titleReplaceCache.value = emptyMap()
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
            chineseConverterType = AppConfig.chineseConverterType,
            chapterCount = chapters.size
        )

        val isJobActive = titleCacheJob?.isActive == true
        if (key == lastTitleCacheKey && (isJobActive || titleReplaceCache.value.isNotEmpty())) {
            return
        }

        lastTitleCacheKey = key
        titleCacheJob?.cancel()
        titleReplaceCache.value = emptyMap()
        titleCacheJob = viewModelScope.launch(Dispatchers.Default) {
            val newCache = HashMap<Int, String>(chapters.size)
            chapters.forEach { chapter ->
                newCache[chapter.index] = chapter.getDisplayTitle(replaceRules, true)
            }
            titleReplaceCache.value = newCache
        }
    }
}
