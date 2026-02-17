package io.legado.app.ui.book.toc

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseRuleViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.Bookmark
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.bookmark.BookmarkExporter
import io.legado.app.model.CacheBook
import io.legado.app.model.ReadBook
import io.legado.app.model.localBook.LocalBook
import io.legado.app.ui.config.readConfig.ReadConfig
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.rules.RuleActionState
import io.legado.app.ui.widget.components.rules.SelectableItem
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TocUiItem(
    val chapter: BookChapter,
    val displayTitle: String,
    override val id: Int = chapter.index
) : SelectableItem<Int>

data class TocActionState(
    override val items: List<TocUiItem> = emptyList(),
    override val selectedIds: Set<Int> = emptySet(),
    override val searchKey: String = "",
    override val isSearch: Boolean = false,
    override val isUploading: Boolean = false,
    val downloadingIndices: Set<Int> = emptySet(),
    val errorIndices: Set<Int> = emptySet(),
    val cachedFiles: Set<String> = emptySet(),
    val downloadSummary: String = ""
) : RuleActionState<TocUiItem>

@OptIn(ExperimentalCoroutinesApi::class)
class TocViewModel(application: Application) :
    BaseRuleViewModel<TocUiItem, Pair<BookChapter, String>, Int, TocActionState>(
        application,
        initialState = TocActionState()
    ) {

    private val _book = MutableStateFlow<Book?>(null)
    val bookState = _book.asStateFlow()
    val isSplitLongChapter: Boolean
        get() = _book.value?.getSplitLongChapter() ?: false

    private val _collapsedVolumes = MutableStateFlow<Set<String>>(emptySet())
    val collapsedVolumes = _collapsedVolumes.asStateFlow()

    private val _cacheFileNames = MutableStateFlow<Set<String>>(emptySet())
    val cacheFileNames = _cacheFileNames.asStateFlow()

    private val _isReverse = MutableStateFlow(false)

    private val _downloadingIndices = MutableStateFlow<Set<Int>>(emptySet())
    private val _downloadSummary = MutableStateFlow("")

    private val _errorIndices = MutableStateFlow<Set<Int>>(emptySet())
    val errorIndices = _errorIndices.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks = _bookmarks.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dbChapterListFlow = _book
        .filterNotNull()
        .map { it.bookUrl }
        .distinctUntilChanged()
        .flatMapLatest { url ->
            appDb.bookChapterDao.getChapterListFlow(url)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val processedChaptersFlow: Flow<List<Pair<BookChapter, String>>> = combine(
        dbChapterListFlow,
        _isReverse,
        _book.filterNotNull(),
        snapshotFlow { ReadConfig.tocUiUseReplace },
        snapshotFlow { ReadConfig.tocCountWords }
    ) { originalList, isReverse, book, useReplace, showWordCount ->

        val processedList = if (isReverse) {
            originalList.fold(mutableListOf<MutableList<BookChapter>>()) { acc, chapter ->
                if (chapter.isVolume || acc.isEmpty()) acc.add(mutableListOf(chapter))
                else acc.last().add(chapter)
                acc
            }.asReversed().flatMap { group ->
                if (group.firstOrNull()?.isVolume == true) {
                    listOf(group.first()) + group.drop(1).asReversed()
                } else group.asReversed()
            }
        } else {
            originalList
        }

        val replaceRules = if (useReplace && book.getUseReplaceRule()) {
            ContentProcessor.get(book.name, book.origin).getTitleReplaceRules()
        } else emptyList()

        processedList.map { chapter ->
            chapter to chapter.getDisplayTitle(replaceRules, true)
        }
    }
        .flowOn(Dispatchers.IO)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    private val downloadStateTrigger =
        combine(
            _downloadingIndices,
            _errorIndices,
            _cacheFileNames,
            _downloadSummary
        ) { _, _, _, _ -> Unit }

    override val rawDataFlow =
        combine(processedChaptersFlow, _collapsedVolumes, downloadStateTrigger) { list, _, _ ->
            list
        }

    val useReplace get() = ReadConfig.tocUiUseReplace
    val showWordCount get() = ReadConfig.tocCountWords

    fun toggleUseReplace() {
        ReadConfig.tocUiUseReplace = !ReadConfig.tocUiUseReplace
    }

    fun toggleShowWordCount() {
        ReadConfig.tocCountWords = !ReadConfig.tocCountWords
    }

    override fun filterData(
        data: List<Pair<BookChapter, String>>,
        key: String
    ): List<Pair<BookChapter, String>> {
        val collapsed = _collapsedVolumes.value
        val isSearch = key.isNotBlank()

        return buildList {
            var isCurrentVolumeCollapsed = false

            for (item in data) {
                val (chapter, displayTitle) = item

                if (chapter.isVolume) {
                    isCurrentVolumeCollapsed = collapsed.contains(chapter.title)
                } else if (isCurrentVolumeCollapsed && !isSearch) {
                    continue
                }

                if (!isSearch || displayTitle.contains(
                        key,
                        ignoreCase = true
                    ) || chapter.isVolume
                ) {
                    add(item)
                }
            }
        }
    }

    override fun composeUiState(
        items: List<TocUiItem>,
        selectedIds: Set<Int>,
        isSearch: Boolean,
        isUploading: Boolean,
        importState: BaseImportUiState<Pair<BookChapter, String>>
    ): TocActionState {

        return TocActionState(
            items = items,
            selectedIds = selectedIds,
            searchKey = _searchKey.value,
            isSearch = isSearch,
            isUploading = isUploading,
            downloadingIndices = _downloadingIndices.value,
            errorIndices = _errorIndices.value,
            cachedFiles = _cacheFileNames.value,
            downloadSummary = _downloadSummary.value
        )
    }

    override fun Pair<BookChapter, String>.toUiItem() = TocUiItem(first, second)

    override fun ruleItemToEntity(item: TocUiItem) = item.chapter to item.displayTitle

    override suspend fun generateJson(entities: List<Pair<BookChapter, String>>) = ""
    override fun parseImportRules(text: String): List<Pair<BookChapter, String>> = emptyList()
    override fun hasChanged(
        newRule: Pair<BookChapter, String>,
        oldRule: Pair<BookChapter, String>
    ) = false

    override suspend fun findOldRule(newRule: Pair<BookChapter, String>) = null
    override fun saveImportedRules() {}

    init {

        viewModelScope.launch {
            combine(
                _book.filterNotNull(),
                snapshotFlow { uiState.value.searchKey }
            ) { book, searchKey ->
                if (searchKey.isBlank()) {
                    appDb.bookmarkDao.flowByBook(book.name, book.author)
                } else {
                    appDb.bookmarkDao.flowSearch(book.name, book.author, searchKey)
                }
            }
                .flatMapLatest { it }
                .catch { e ->
                    // AppLog.put("目录界面获取书签数据失败\n${e.localizedMessage}", e)
                }
                .flowOn(Dispatchers.IO)
                .collect { list ->
                    _bookmarks.value = list
                }
        }

        viewModelScope.launch {

            launch {
                CacheBook.downloadingIndicesFlow.collect { (url, set) ->
                    if (url == _book.value?.bookUrl) {
                        _downloadingIndices.value = set
                    }
                }
            }

            launch {
                CacheBook.downloadErrorFlow.collect { (url, set) ->
                    if (url == _book.value?.bookUrl) {
                        _errorIndices.value = set
                    }
                }
            }

            launch {
                CacheBook.downloadSummaryFlow.collect {
                    _downloadSummary.value = it
                }
            }

            launch {
                CacheBook.cacheSuccessFlow.collect { chapter ->
                    if (chapter.bookUrl == _book.value?.bookUrl) {
                        _cacheFileNames.update {
                            it + chapter.getFileName()
                        }
                    }
                }
            }
        }
    }

    fun initBook(bookUrl: String) = execute {
        appDb.bookDao.getBook(bookUrl)?.let { book ->
            _book.value = book
            _isReverse.value = book.getReverseToc()

            viewModelScope.launch(Dispatchers.IO) {
                val files = BookHelp.getChapterFiles(book).toSet()
                _cacheFileNames.value = files
            }
        }
    }

    fun saveTocRegex(newRegex: String) {
        val book = _book.value ?: return
        book.tocUrl = newRegex
        upBookTocRule(book) { error ->
            if (error != null) {
                context.toastOnUi("更新目录规则失败: ${error.localizedMessage}")
            } else {
                context.toastOnUi("目录规则已更新")
                if (ReadBook.book?.bookUrl == book.bookUrl) {
                    ReadBook.upMsg(null)
                }
            }
        }
    }

    fun toggleSplitLongChapter() {
        val book = _book.value ?: return
        val newState = !isSplitLongChapter
        book.setSplitLongChapter(newState)

        upBookTocRule(book) { error ->
            if (error != null) {
                context.toastOnUi("设置失败: ${error.localizedMessage}")
            } else {
                context.toastOnUi(if (newState) "已开启长章节拆分" else "已关闭长章节拆分")
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

                _book.value = book
            }
        }.onSuccess {
            _isUploading.value = false
            complete.invoke(null)
        }.onError {
            _isUploading.value = false
            complete.invoke(it)
        }
    }

    fun reverseToc() = execute {
        val currentBook = _book.value ?: return@execute

        val newReverseState = !_isReverse.value
        _isReverse.value = newReverseState

        val newBook = currentBook.copy().apply {
            setReverseToc(newReverseState)
        }
        appDb.bookDao.update(newBook)
        _book.value = newBook
    }

    fun toggleVolume(volumeName: String) {
        _collapsedVolumes.update { current ->
            if (current.contains(volumeName)) current - volumeName else current + volumeName
        }
    }

    fun expandAllVolumes() {
        _collapsedVolumes.value = emptySet()
    }

    fun collapseAllVolumes() = execute {
        val bookUrl = _book.value?.bookUrl ?: return@execute
        val volumes = appDb.bookChapterDao.getChapterList(bookUrl)
            .filter { it.isVolume }
            .map { it.title }
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

    fun exportCurrentBookBookmarks(fileUri: Uri, isMd: Boolean) {
        viewModelScope.launch {
            try {
                val book = _book.value ?: return@launch
                val bookmarks = appDb.bookmarkDao.getByBook(book.name, book.author)

                if (bookmarks.isEmpty()) {
                    context.toastOnUi("没有可导出的书签")
                    return@launch
                }

                BookmarkExporter.exportToUri(
                    context = getApplication(),
                    fileUri = fileUri,
                    bookmarks = bookmarks,
                    isMd = isMd,
                    bookName = book.name,
                    author = book.author
                )

                context.toastOnUi("保存成功")
            } catch (e: Exception) {
                context.toastOnUi("保存失败: ${e.message}")
            }
        }
    }

    fun updateBookmark(bookmark: Bookmark) {
        viewModelScope.launch(Dispatchers.IO) {
            appDb.bookmarkDao.insert(bookmark)
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch(Dispatchers.IO) {
            appDb.bookmarkDao.delete(bookmark)
        }
    }

    /**
     * 下载选中的章节
     */
    fun downloadSelected() {
        val book = _book.value ?: return
        val indices = uiState.value.selectedIds.toList()
        if (indices.isEmpty()) return
        CacheBook.start(getApplication(), book, indices)
        getApplication<Application>().toastOnUi("开始下载 ${indices.size} 个章节")
        clearSelection()
    }

    /**
     * 下载单个章节
     */
    fun downloadChapter(index: Int) {
        val book = _book.value ?: return
        CacheBook.start(getApplication(), book, listOf(index))
        getApplication<Application>().toastOnUi("开始下载章节")
    }

    /**
     * 下载所有章节
     */
    fun downloadAll() {
        val book = _book.value ?: return
        val cachedFiles = _cacheFileNames.value

        val targetIndices = uiState.value.items
            .filter { !it.chapter.isVolume && it.chapter.getFileName() !in cachedFiles }
            .map { it.id }

        if (targetIndices.isEmpty()) {
            getApplication<Application>().toastOnUi("所有章节已缓存")
            return
        }

        CacheBook.start(getApplication(), book, targetIndices)
        getApplication<Application>().toastOnUi("开始下载剩余 ${targetIndices.size} 个章节")
    }

    fun selectFromLast() {
        val currentItems = uiState.value.items
        val maxSelectedId = _selectedIds.value.maxOrNull() ?: return

        val maxIndex = currentItems.indexOfFirst { it.id == maxSelectedId }
        if (maxIndex == -1) return

        val idsToAppend = currentItems.drop(maxIndex + 1).map { it.id }
        setSelection(_selectedIds.value + idsToAppend)
    }
}