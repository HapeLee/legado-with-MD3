package io.legado.app.ui.book.changecover

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.repository.SearchRepository
import io.legado.app.domain.gateway.BookSearchGateway
import io.legado.app.domain.gateway.ChangeSourceSettingsGateway
import io.legado.app.domain.gateway.DownloadCacheSettingsGateway
import io.legado.app.domain.model.BookSearchScope
import io.legado.app.domain.usecase.CoverAlbumUseCase
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.externalFiles
import io.legado.app.utils.mapParallelSafe
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Collections
import java.util.concurrent.Executors

/** 与 CoilBookCover 的 DefaultCoverPath 一致：选中它表示清除自定义封面、回落随机默认图。 */
private const val DEFAULT_COVER_PATH = "use_default_cover"

class ChangeCoverViewModel(
    application: Application,
    private val searchRepository: SearchRepository,
    private val bookSearchGateway: BookSearchGateway,
    private val coverAlbumUseCase: CoverAlbumUseCase,
    private val downloadCacheSettingsGateway: DownloadCacheSettingsGateway,
    private val changeSourceSettingsGateway: ChangeSourceSettingsGateway,
) : BaseViewModel(application) {
    private val threadCount = downloadCacheSettingsGateway.currentSettings.threadCount.coerceAtLeast(1)
    private var searchPool: ExecutorCoroutineDispatcher? = null
    private var task: Job? = null
    private var allowedOrigins: Set<String>? = null
    private var scopeRaw = changeSourceSettingsGateway.currentSettings.coverSearchScope
    private var name: String = ""
    private var author: String = ""
    private var isDark: Boolean = false
    private var albumsInitialized = false
    private val searchBooks = Collections.synchronizedList(mutableListOf<SearchBook>())

    private val _uiState = MutableStateFlow(
        ChangeCoverUiState(scope = scopeState(scopeRaw))
    )
    val uiState = _uiState.asStateFlow()
    val enabledGroups = searchRepository.enabledGroups
    val enabledSources = searchRepository.enabledSources

    fun onIntent(intent: ChangeCoverIntent) {
        when (intent) {
            is ChangeCoverIntent.Shown -> onShown(intent)
            ChangeCoverIntent.Hidden -> stopSearch()
            ChangeCoverIntent.ToggleSearch ->
                if (task?.isActive == true) stopSearch() else startSearch()

            is ChangeCoverIntent.ApplyScope -> applyScope(intent)
            is ChangeCoverIntent.SelectTab -> {
                val index = intent.index.coerceIn(0, _uiState.value.albums.size)
                _uiState.update { it.copy(selectedTabIndex = index) }
                emitOptions()
            }

            is ChangeCoverIntent.SelectOption -> selectOption(intent.option)
            ChangeCoverIntent.SelectionConsumed ->
                _uiState.update { it.copy(pendingSelection = null) }
        }
    }

    private fun onShown(intent: ChangeCoverIntent.Shown) {
        name = intent.name
        author = intent.author.replace(AppPattern.authorRegex, "")
        isDark = intent.isDark
        refreshAlbums()
        // 只在用户真正打开面板时读缓存并按需补搜；编辑页在 show=false 时同样会组合本 Sheet，
        // 无条件联网会对全部启用书源执行搜索规则/登录脚本（弹“未登录”提示）。
        viewModelScope.launch(Dispatchers.IO) {
            // 持久化的范围要在读取缓存前生效，否则「上次选了某分组 + 其它源有缓存」
            // 时打开面板会列出全部书源封面，和实际生效的筛选范围不一致。
            val scope = BookSearchScope(scopeRaw)
            val parts = bookSearchGateway.getBookSourceParts(scope)
            allowedOrigins = if (scope.isAll) null else parts.mapTo(hashSetOf()) { it.bookSourceUrl }
            val cached = searchRepository.getEnableHasCover(name, author)
            synchronized(searchBooks) {
                searchBooks.clear()
                searchBooks.addAll(cached)
            }
            emitOptions()
            // 只按当前范围内可见的缓存判断是否需要补搜。
            val allowed = allowedOrigins
            val visibleCount = cached.count { allowed == null || it.origin in allowed }
            if (visibleCount <= 1) {
                startSearch(clearResults = false, preparedParts = parts)
            }
        }
    }

    private fun refreshAlbums() {
        val albums = coverAlbumUseCase.albums.value.map { CoverAlbumOption(it.id, it.name) }
        val selectedIndex = if (albumsInitialized) {
            _uiState.value.selectedTabIndex.coerceIn(0, albums.size)
        } else {
            // 首次打开沿用当前配置的图集（tab 0 是默认封面）；无图集时回到默认封面。
            albums.indexOfFirst { it.id == coverAlbumUseCase.selection.value.albumId }
                .let { if (it >= 0) it + 1 else 0 }
        }
        albumsInitialized = true
        _uiState.update {
            it.copy(albums = albums.toImmutableList(), selectedTabIndex = selectedIndex)
        }
    }

    private fun applyScope(intent: ChangeCoverIntent.ApplyScope) {
        val raw = if (intent.isSourceScope) {
            BookSearchScope.encodeSources(intent.sources)
        } else {
            BookSearchScope.encodeGroups(intent.groupNames)
        }
        scopeRaw = raw
        _uiState.update { it.copy(scope = scopeState(raw)) }
        viewModelScope.launch(Dispatchers.IO) {
            changeSourceSettingsGateway.update { it.copy(coverSearchScope = raw) }
            val scope = BookSearchScope(raw)
            val parts = bookSearchGateway.getBookSourceParts(scope)
            allowedOrigins = if (scope.isAll) null else parts.mapTo(hashSetOf()) { it.bookSourceUrl }
            // 先按新范围收窄已有缓存，再只对选中范围补搜；不清空其它书源的缓存结果。
            emitOptions()
            startSearch(clearResults = false, preparedParts = parts)
        }
    }

    private fun selectOption(option: CoverOption) {
        val albumPath = option.albumPath
        if (albumPath == null) {
            _uiState.update { it.copy(pendingSelection = option.coverUrl) }
            return
        }
        // 图集图片属于图集自己的目录，删图/删图集会让已固定的书封失效；
        // 与编辑页“从相册选图”一致，复制一份按内容 MD5 去重的独立封面。
        viewModelScope.launch(Dispatchers.IO) {
            val resolved = runCatching { copyAlbumCover(albumPath) }
                .onFailure { AppLog.put("书籍封面保存失败\n$it", it, true) }
                .getOrNull()
            if (resolved != null) {
                _uiState.update { it.copy(pendingSelection = resolved) }
            }
        }
    }

    private fun copyAlbumCover(sourcePath: String): String {
        val source = File(sourcePath)
        val coversDir = FileUtils.createFolderIfNotExist(context.externalFiles, "covers")
        val suffix = source.extension.takeIf { it.matches(Regex("[a-zA-Z0-9]{1,8}")) } ?: "jpg"
        val md5 = source.inputStream().use { MD5Utils.md5Encode(it) }
        val target = File(coversDir, "$md5.$suffix")
        if (!target.exists()) {
            source.copyTo(target, overwrite = true)
        }
        return target.absolutePath
    }

    private fun emitOptions() {
        val default = CoverOption(
            key = "default",
            label = context.getString(R.string.default_cover),
            coverUrl = DEFAULT_COVER_PATH,
            isDefault = true,
        )
        val online = synchronized(searchBooks) { searchBooks.toList() }.map { book ->
            CoverOption(
                key = "online:${book.origin}:${book.bookUrl}",
                label = book.originName,
                coverUrl = book.coverUrl.orEmpty(),
                origin = book.origin,
                originOrder = book.originOrder,
            )
        }
        val options = buildCoverOptions(default, buildAlbumOptions(), online, allowedOrigins)
        _uiState.update { it.copy(options = options.toImmutableList()) }
    }

    private fun buildAlbumOptions(): List<CoverOption> {
        // tab 0 是默认封面，不列出图集图片。
        val albumIndex = _uiState.value.selectedTabIndex - 1
        if (albumIndex < 0) return emptyList()
        val album = coverAlbumUseCase.albums.value.getOrNull(albumIndex) ?: return emptyList()
        val images = if (isDark) {
            album.darkImages.ifEmpty { album.lightImages }
        } else {
            album.lightImages
        }
        return images.map { image ->
            CoverOption(
                key = "album:${album.id}:${image.id}",
                label = album.name,
                coverUrl = image.path,
                albumPath = image.path,
            )
        }
    }

    private fun initSearchPool() {
        searchPool = Executors.newFixedThreadPool(threadCount).asCoroutineDispatcher()
    }

    private fun startSearch(
        clearResults: Boolean = true,
        preparedParts: List<BookSourcePart>? = null,
    ) {
        stopSearch()
        if (clearResults) {
            synchronized(searchBooks) { searchBooks.clear() }
            emitOptions()
        }
        task = viewModelScope.launch(Dispatchers.IO) {
            val scope = BookSearchScope(scopeRaw)
            val parts = preparedParts ?: bookSearchGateway.getBookSourceParts(scope)
            allowedOrigins = if (scope.isAll) null else parts.mapTo(hashSetOf()) { it.bookSourceUrl }
            initSearchPool()
            withContext(searchPool!!) {
                flow {
                    for (part in parts) {
                        part.getBookSource()?.let { emit(it) }
                    }
                }.onStart {
                    setSearching(true)
                }.mapParallelSafe(threadCount) { source ->
                    withTimeout(60_000L) {
                        search(source)
                    }
                }.onCompletion {
                    setSearching(false)
                }.catch {
                    AppLog.put("封面换源搜索出错\n${it.localizedMessage}", it)
                }.collect()
            }
        }
    }

    private suspend fun search(source: BookSource) {
        if (source.getSearchRule().coverUrl.isNullOrBlank()) {
            return
        }
        val searchBook = WebBook.searchBookAwait(
            source, name,
            shouldBreak = { it > 0 }).firstOrNull() ?: return
        if (searchBook.name == name && searchBook.author == author
            && !searchBook.coverUrl.isNullOrEmpty()
        ) {
            searchRepository.saveSearchBook(searchBook)
            val added = synchronized(searchBooks) {
                if (searchBooks.contains(searchBook)) {
                    false
                } else {
                    searchBooks.add(searchBook)
                    true
                }
            }
            if (added) {
                emitOptions()
            }
        }
    }

    private fun setSearching(value: Boolean) {
        _uiState.update { it.copy(isSearching = value) }
    }

    private fun stopSearch() {
        task?.cancel()
        task = null
        searchPool?.close()
        searchPool = null
        setSearching(false)
    }

    private fun scopeState(raw: String): CoverScopeUiState {
        val scope = BookSearchScope(raw)
        return CoverScopeUiState(
            isAll = scope.isAll,
            isSource = scope.isSource,
            displayNames = (if (scope.isSource) scope.sourceNames else scope.groupNames)
                .toImmutableList(),
            sourceUrls = scope.sourceUrls.toImmutableList(),
        )
    }

    override fun onCleared() {
        super.onCleared()
        stopSearch()
    }
}
