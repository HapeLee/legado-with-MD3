package io.legado.app.ui.main.homepage

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.repository.BookSourceRepository
import io.legado.app.data.repository.SearchRepository
import io.legado.app.domain.gateway.HomepageModulesGateway
import io.legado.app.domain.gateway.HomepageSettingsGateway
import io.legado.app.domain.model.BookShelfState
import io.legado.app.domain.model.CustomSetItem
import io.legado.app.domain.model.HomepageModuleType
import io.legado.app.domain.model.ModuleDef
import io.legado.app.domain.model.ModuleItem
import io.legado.app.domain.usecase.AddToBookshelfUseCase
import io.legado.app.domain.usecase.BookShelfKey
import io.legado.app.domain.usecase.ExploreBooksUseCase
import io.legado.app.domain.usecase.ResolveBookShelfStateUseCase
import io.legado.app.domain.usecase.SaveSearchBooksUseCase
import io.legado.app.help.source.exploreKinds
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.stackTraceStr
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class HomepageViewModel(
    application: Application,
    private val bookSourceRepository: BookSourceRepository,
    private val gateway: HomepageModulesGateway,
    private val homepageSettingsGateway: HomepageSettingsGateway,
    private val searchRepository: SearchRepository,
    private val exploreBooksUseCase: ExploreBooksUseCase,
    private val saveSearchBooksUseCase: SaveSearchBooksUseCase,
    private val resolveBookShelfStateUseCase: ResolveBookShelfStateUseCase,
    private val addToBookshelfUseCase: AddToBookshelfUseCase,
) : BaseViewModel(application) {

    private val _bookshelf = MutableStateFlow<Set<BookShelfKey>>(emptySet())

    companion object {
        private const val CUSTOM_SET_URL_PREFIX = "custom://"
        private const val HOMEPAGE_DEFAULT_GRID_ROWS = 2
        private const val HOMEPAGE_MAX_BUTTON_GROUP_KINDS = 5

        fun customSetUrl(id: String) = "$CUSTOM_SET_URL_PREFIX$id"
        fun isCustomSetUrl(url: String) = url.startsWith(CUSTOM_SET_URL_PREFIX)
        fun customSetIdFromUrl(url: String): String = url.removePrefix(CUSTOM_SET_URL_PREFIX)

        fun isInfinite(type: String?, layoutConfig: String?): Boolean {
            return type == HomepageModuleType.Waterfall.key
                    || type == HomepageModuleType.InfiniteGrid.key
        }

        private fun parseModuleDefs(source: BookSource, json: String): List<ModuleDef> =
            GSON.fromJsonArray<ModuleDef>(json).getOrDefault(emptyList())
                .map { it.copy(sourceUrl = source.bookSourceUrl) }

        private fun jsonHash(json: String): String {
            val digest = MessageDigest.getInstance("MD5").digest(json.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        private fun List<ModuleItem>.groupBySourceOrdered(): Map<String, List<ModuleItem>> {
            val result = linkedMapOf<String, MutableList<ModuleItem>>()
            for (module in this) {
                val key = module.customSetId?.let { customSetUrl(it) } ?: module.sourceUrl
                result.getOrPut(key) { mutableListOf() }.add(module)
            }
            return result
        }
    }

    private val _effects = MutableSharedFlow<HomepageEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    private val loadJobs = ConcurrentHashMap<String, Job>()
    private val exploreSourcePartsFlow = bookSourceRepository.flowExploreSourceParts()

    // 1. 基础原始状态
    private val _isRefreshing = MutableStateFlow(false)
    private val _isManageMode = MutableStateFlow(false)
    private val _configVersion = MutableStateFlow(0L)
    private val _moduleContentStates = MutableStateFlow<Map<String, ModuleLoadState>>(emptyMap())
    private val _bookSourcesCache = MutableStateFlow<Map<String, BookSource>>(emptyMap())
    private val _bookSourcePartsCache = MutableStateFlow<Map<String, BookSourcePart>>(emptyMap())
    private val _layoutConfigCache = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())
    private val _pendingEnabled = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val _pendingUserModules = MutableStateFlow<List<ModuleItem>>(emptyList())
    // 2. 数据库同步流
    private val localModulesFlow = gateway.flowEnabled()
    val allModulesCache =
        gateway.flowAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val customSetsFlow = gateway.flowCustomSets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val customSetsWithSettingsFlow = combine(
        customSetsFlow,
        homepageSettingsGateway.settings,
    ) { customSets, settings -> customSets to settings }

    // 3. 业务加工流
    private val orderedModuleDefsFlow = combine(localModulesFlow, _configVersion) { modules, _ ->
        modules.groupBySourceOrdered()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val setsFlow = combine(
        localModulesFlow,
        allModulesCache,
        customSetsWithSettingsFlow,
        _configVersion
    ) { _, allModules, (customSets, settings), _ ->
        val hiddenSourceUrls = GSON.fromJsonArray<String>(settings.hiddenSourceUrlsJson)
            .getOrDefault(emptyList()).toSet()
        val moduleCountsBySet =
            allModules.mapNotNull { it.customSetId }.groupBy { it }.mapValues { it.value.size }

        customSets.sortedBy { it.sortOrder }.map { set ->
            HomepageSourceManageUi(
                sourceUrl = customSetUrl(set.id),
                sourceName = set.name,
                sourceGroup = null,
                isSelected = customSetUrl(set.id) !in hiddenSourceUrls,
                moduleCount = moduleCountsBySet[set.id] ?: 0,
                isCustomSet = true,
            )
        }.toImmutableList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), persistentListOf())

    val browseSourcesFlow = exploreSourcePartsFlow.map { sources ->
        sources.map { source ->
            HomepageSourceManageUi(
                sourceUrl = source.bookSourceUrl,
                sourceName = source.bookSourceName,
                sourceGroup = source.bookSourceGroup,
            )
        }.toImmutableList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), persistentListOf())

    // 4. 聚合层
    private val uiFlagsFlow =
        combine(_isRefreshing, _isManageMode) { refreshing, manage ->
            HomepageUiFlags(refreshing, manage)
        }

    private val manageStateFlow = combine(
        setsFlow,
        browseSourcesFlow,
        allModulesCache,
        _bookSourcePartsCache,
        _pendingEnabled
    ) { sets, browseSources, allModules, sourcePartsCache, pendingEnabled ->
        HomepageManageUiState(
            sets = sets,
            browseSources = browseSources,
            allJoinedModules = allModules.map { module ->
                HomepageModuleManageUi(
                    id = module.id,
                    sourceUrl = module.sourceUrl,
                    moduleKey = module.moduleKey,
                    title = module.displayTitle,
                    customSetTitle = module.customSetTitle,
                    customSetId = module.customSetId,
                    isVisible = pendingEnabled[module.id] ?: module.isEnabled,
                    type = module.type,
                    url = module.url,
                    args = module.args,
                    layoutConfig = module.layoutConfig,
                    originalTitle = module.title,
                )
            }.toImmutableList(),
            sourceNames = sourcePartsCache.mapValues { it.value.bookSourceName }
        )
    }

    private val sourceCachesFlow = combine(
        _bookSourcePartsCache,
        _bookSourcesCache
    ) { sourceParts, sources ->
        sourceParts to sources
    }

    private val rawModulesFlow = combine(
        orderedModuleDefsFlow,
        _moduleContentStates,
        sourceCachesFlow,
        customSetsFlow,
        _layoutConfigCache
    ) { grouped, contentStates, sourceCaches, customSets, configCache ->
        val (sourcePartsCache, sourcesCache) = sourceCaches
        val setNames = customSets.associate { it.id to it.name }
        val sortedSetIds = customSets.sortedBy { it.sortOrder }.map { it.id }

        sortedSetIds.flatMap { setId ->
            val setUrl = customSetUrl(setId)
            val mods = grouped[setUrl] ?: emptyList()
            mods.map { module ->
                val source = sourcesCache[module.sourceUrl]
                val sourcePart = sourcePartsCache[module.sourceUrl]
                val sourceName =
                    source?.bookSourceName ?: sourcePart?.bookSourceName ?: module.sourceUrl
                val setName = module.customSetId?.let { setNames[it] } ?: sourceName
                val exploreUrl = module.url ?: source?.exploreUrl
                val configMap = configCache[module.id] ?: emptyMap()

                HomepageModuleUi(
                    sourceUrl = module.sourceUrl,
                    setName = setName,
                    globalId = module.id,
                    type = HomepageModuleType.fromKey(module.type),
                    title = module.displayTitle,
                    exploreUrl = exploreUrl,
                    customSetId = module.customSetId,
                    layoutConfig = module.layoutConfig,
                    state = contentStates[module.id] ?: ModuleLoadState.Loading,
                    config = configMap
                )
            }
        }.toImmutableList()
    }

    private val displayModulesFlow = combine(
        rawModulesFlow,
        _bookshelf
    ) { modules, bookshelf ->
        if (bookshelf.isEmpty()) {
            modules.map { module ->
                val state = module.state.mapBooks { item ->
                    if (item.shelfState == BookShelfState.NOT_IN_SHELF) item
                    else item.copy(shelfState = BookShelfState.NOT_IN_SHELF)
                }
                if (state === module.state) module else module.copy(state = state)
            }.toImmutableList()
        } else {
            val exactKeys = HashSet<Triple<String, String, String?>>(bookshelf.size)
            val nameAuthorKeys = HashSet<Pair<String, String>>(bookshelf.size)
            for (key in bookshelf) {
                exactKeys.add(Triple(key.name, key.author, key.url))
                nameAuthorKeys.add(key.name to key.author)
            }
            modules.map { module ->
                val state = module.state.mapBooks { item ->
                    val bookTriple = Triple(item.book.name, item.book.author, item.book.bookUrl)
                    val newShelfState = when {
                        bookTriple in exactKeys -> BookShelfState.IN_SHELF
                        (item.book.name to item.book.author) in nameAuthorKeys ->
                            BookShelfState.SAME_NAME_AUTHOR

                        else -> BookShelfState.NOT_IN_SHELF
                    }
                    if (item.shelfState == newShelfState) item
                    else item.copy(shelfState = newShelfState)
                }
                if (state === module.state) module else module.copy(state = state)
            }.toImmutableList()
        }
    }

    // 5. 最终 UI 状态
    val uiState: StateFlow<HomepageUiState> = combine(
        displayModulesFlow,
        uiFlagsFlow,
        manageStateFlow
    ) { modules, flags, manageState ->
        HomepageUiState(
            modules = modules,
            isRefreshing = flags.isRefreshing,
            isManageMode = flags.isManageMode,
            manageState = manageState
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomepageUiState())

    init {
        // 解析并缓存模块 layoutConfig
        viewModelScope.launch {
            localModulesFlow.collect { modules ->
                val cache = mutableMapOf<String, Map<String, String>>()
                for (module in modules) {
                    val configStr = module.layoutConfig ?: continue
                    try {
                        val json = GSON.fromJson(configStr, Map::class.java)
                        if (json != null) {
                            val map = mutableMapOf<String, String>()
                            json.forEach { (k, v) ->
                                map["layout_$k"] = when (v) {
                                    is Double -> if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
                                    else -> v.toString()
                                }
                            }
                            cache[module.id] = map
                        }
                    } catch (_: Exception) {
                    }
                }
                _layoutConfigCache.value = cache
            }
        }

        viewModelScope.launch {
            exploreSourcePartsFlow.collect { sources ->
                _bookSourcePartsCache.value = sources.associateBy { it.bookSourceUrl }
            }
        }

        viewModelScope.launch {
            bookSourceRepository.flowHomepageModules().collect { sources ->
                _bookSourcesCache.value = sources.associateBy { it.bookSourceUrl }
            }
        }

        viewModelScope.launch {
            uiState.map { it.modules }.collect { modules ->
                modules.forEach { ui ->
                    if (ui.state is ModuleLoadState.Loading && loadJobs[ui.globalId]?.isActive != true) {
                        val module = gateway.getById(ui.globalId)
                        if (module != null) loadModule(module)
                    }
                }
            }
        }

        viewModelScope.launch {
            allModulesCache.collect { modules ->
                val dbIds = modules.map { it.id }.toSet()
                _pendingUserModules.update { pending -> pending.filter { it.id !in dbIds } }
                _moduleContentStates.update { states ->
                    if (states.keys.any { it !in dbIds }) states.filterKeys { it in dbIds }
                    else states
                }
            }
        }

        viewModelScope.launch {
            val allModules = allModulesCache.first()
            val orphans = allModules.filter { it.customSetId == null }
            if (orphans.isNotEmpty()) {
                orphans.groupBy { it.sourceUrl }.forEach { (sourceUrl, modules) ->
                    val source = bookSourceRepository.getBookSource(sourceUrl) ?: return@forEach
                    ensureSetForSource(sourceUrl, source.bookSourceName)
                    modules.forEach { m -> gateway.setCustomSetId(m.id, "src_$sourceUrl") }
                }
            }
            allModules.mapNotNull { it.customSetId }.distinct().forEach { setId ->
                val isSrcSet = setId.startsWith("src_")
                if (isSrcSet && gateway.getCustomSetById(setId) == null) {
                    val sourceUrl = setId.removePrefix("src_")
                    val source = bookSourceRepository.getBookSource(sourceUrl)
                    if (source != null) ensureSetForSource(sourceUrl, source.bookSourceName)
                }
            }
        }

        viewModelScope.launch {
            searchRepository.bookshelfKeys.collect { keys ->
                _bookshelf.value = keys
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        loadJobs.values.forEach { it.cancel() }
        loadJobs.clear()
    }

    private suspend fun syncModulesFromSource(source: BookSource) {
        val json = source.homepageModules ?: return
        ensureSetForSource(source.bookSourceUrl, source.bookSourceName)
        val parsedDefs = parseModuleDefs(source, json)
        val newHash = jsonHash(json)

        val existingModules = gateway.flowBySource(source.bookSourceUrl).first()
        val existingById = existingModules.associateBy { it.id }
        val parsedIds = parsedDefs.map { it.globalId }.toSet()

        val toUpsert = mutableListOf<ModuleItem>()
        for (i in parsedDefs.indices) {
            val def = parsedDefs[i]
            val existing = existingById[def.globalId]
            if (existing != null) {
                if (existing.isUserCreated) continue
                if (existing.sourceJsonHash == newHash) continue
                toUpsert.add(
                    existing.copy(
                        type = def.type, title = def.title, args = def.args, url = def.url,
                        sourceJsonHash = newHash, syncedAt = System.currentTimeMillis()
                    )
                )
            } else {
                toUpsert.add(
                    ModuleItem(
                        id = def.globalId,
                        sourceUrl = source.bookSourceUrl,
                        moduleKey = def.key,
                        type = def.type,
                        title = def.title,
                        args = def.args,
                        url = def.url,
                        isEnabled = true,
                        customSetId = "src_${source.bookSourceUrl}",
                        sortOrder = i,
                        sourceJsonHash = newHash,
                        syncedAt = System.currentTimeMillis()
                    )
                )
            }
        }
        if (toUpsert.isNotEmpty()) gateway.upsertAll(toUpsert)
        if (parsedIds.isNotEmpty()) gateway.deleteStale(source.bookSourceUrl, parsedIds.toList())
    }

    private fun loadModule(module: ModuleItem) {
        loadJobs[module.id]?.cancel()
        val rankingKindTitles = module.rankingKindTitles()
        if (rankingKindTitles != null) {
            loadJobs[module.id] = viewModelScope.launch {
                kotlin.runCatching {
                    val source = bookSourceRepository.getBookSource(module.sourceUrl)
                        ?: throw Exception("Source not found")
                    val allKinds = withContext(Dispatchers.IO) { source.exploreKinds() }
                    val selectedKinds = rankingKindTitles.mapNotNull { title ->
                        allKinds.find { it.title == title }
                    }
                    if (selectedKinds.isEmpty()) throw Exception("Ranking kinds not found")

                    val shelf = _bookshelf.value
                    coroutineScope {
                        selectedKinds.map { kind ->
                            async {
                                val state = kotlin.runCatching {
                                    exploreBooksUseCase.executeForRanking(
                                        module.sourceUrl,
                                        kind.url,
                                        null
                                    )
                                }.fold(
                                    onSuccess = { books ->
                                        ModuleLoadState.Loaded(
                                            books = books.map { book ->
                                                HomepageBookItemUi(
                                                    book = book,
                                                    shelfState = resolveBookShelfStateUseCase.execute(
                                                        name = book.name,
                                                        author = book.author,
                                                        url = book.bookUrl,
                                                        shelf = shelf
                                                    )
                                                )
                                            }.toImmutableList()
                                        )
                                    },
                                    onFailure = { ModuleLoadState.Error(it.stackTraceStr) }
                                )
                                HomepageRankingSourceUi(
                                    title = kind.title,
                                    url = kind.url,
                                    state = state,
                                )
                            }
                        }.awaitAll().toImmutableList()
                    }
                }.onSuccess { sources ->
                    _moduleContentStates.update {
                        it + (module.id to ModuleLoadState.Rankings(sources))
                    }
                }.onFailure { e ->
                    _moduleContentStates.update {
                        it + (module.id to ModuleLoadState.Error(e.stackTraceStr))
                    }
                }
            }.also { it.invokeOnCompletion { loadJobs.remove(module.id) } }
            return
        }
        if (module.type == HomepageModuleType.ButtonGroup.key) {
            loadJobs[module.id] = viewModelScope.launch {
                kotlin.runCatching {
                    val source = bookSourceRepository.getBookSource(module.sourceUrl)
                        ?: throw Exception("Source not found")
                    val allKinds = withContext(Dispatchers.IO) { source.exploreKinds() }
                    val selectedTitles =
                        module.args?.let { GSON.fromJsonArray<String>(it).getOrNull() }
                    if (selectedTitles.isNullOrEmpty()) allKinds.take(
                        HOMEPAGE_MAX_BUTTON_GROUP_KINDS
                    )
                    else selectedTitles.mapNotNull { t -> allKinds.find { it.title == t } }
                }.onSuccess { kinds ->
                    _moduleContentStates.update { it + (module.id to ModuleLoadState.Buttons(kinds.toImmutableList())) }
                }.onFailure { e ->
                    _moduleContentStates.update { it + (module.id to ModuleLoadState.Error(e.stackTraceStr)) }
                }
            }.also { it.invokeOnCompletion { loadJobs.remove(module.id) } }
            return
        }
        loadJobs[module.id] = viewModelScope.launch {
            kotlin.runCatching {
                val isRanking =
                    module.type == HomepageModuleType.Ranking.key || module.type == HomepageModuleType.GridRanking.key
                val books = if (isRanking) exploreBooksUseCase.executeForRanking(
                    module.sourceUrl,
                    module.url,
                    module.args
                )
                else exploreBooksUseCase.execute(module.sourceUrl, module.url, module.args).books
                val hasMore = isInfinite(module.type, module.layoutConfig) && books.isNotEmpty()
                books to hasMore
            }.onSuccess { (books, hasMore) ->
                val shelf = _bookshelf.value
                _moduleContentStates.update {
                    it + (module.id to ModuleLoadState.Loaded(
                        books.map { book ->
                            HomepageBookItemUi(
                                book = book,
                                shelfState = resolveBookShelfStateUseCase.execute(
                                    name = book.name,
                                    author = book.author,
                                    url = book.bookUrl,
                                    shelf = shelf
                                )
                            )
                        }.toImmutableList(),
                        hasMore = hasMore
                    ))
                }
            }.onFailure { e ->
                _moduleContentStates.update { it + (module.id to ModuleLoadState.Error(e.stackTraceStr)) }
            }
        }.also { it.invokeOnCompletion { loadJobs.remove(module.id) } }
    }

    fun loadMoreModule(globalId: String) {
        val currentState = _moduleContentStates.value[globalId] as? ModuleLoadState.Loaded ?: return
        if (currentState.isLoadingMore || !currentState.hasMore) return
        val nextPage = currentState.page + 1
        _moduleContentStates.update { it + (globalId to currentState.copy(isLoadingMore = true)) }
        viewModelScope.launch {
            kotlin.runCatching {
                val module = gateway.getById(globalId) ?: throw Exception("Module not found")
                exploreBooksUseCase.execute(
                    module.sourceUrl,
                    module.url,
                    module.args,
                    page = nextPage
                )
            }.onSuccess { result ->
                _moduleContentStates.update { states ->
                    val lastState =
                        states[globalId] as? ModuleLoadState.Loaded ?: return@update states
                    val existingUrls = lastState.books.map { it.book.bookUrl }.toSet()
                    val shelf = _bookshelf.value
                    val deduped = result.books.filter { it.bookUrl !in existingUrls }.map { book ->
                        HomepageBookItemUi(
                            book = book,
                            shelfState = resolveBookShelfStateUseCase.execute(
                                name = book.name,
                                author = book.author,
                                url = book.bookUrl,
                                shelf = shelf
                            )
                        )
                    }
                    states + (globalId to ModuleLoadState.Loaded(
                        books = (lastState.books + deduped).toImmutableList(),
                        hasMore = deduped.isNotEmpty(), isLoadingMore = false, page = nextPage
                    ))
                }
            }.onFailure { e ->
                _moduleContentStates.update { states ->
                    val lastState =
                        states[globalId] as? ModuleLoadState.Loaded ?: return@update states
                    states + (globalId to lastState.copy(isLoadingMore = false))
                }
                _effects.tryEmit(
                    HomepageEffect.ShowSnackbar(
                        getApplication<Application>().getString(
                            R.string.homepage_load_more_failed,
                            e.stackTraceStr
                        )
                    )
                )
            }
        }
    }

    fun refreshButtonGroup(globalId: String) {
        viewModelScope.launch {
            val module = gateway.getById(globalId) ?: return@launch
            loadModule(module)
        }
    }

    fun onKindUrlClick(sourceUrl: String, url: String, title: String) =
        _effects.tryEmit(HomepageEffect.NavigateToExploreShow(title, sourceUrl, url))

    fun onRefresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            loadJobs.values.forEach { it.cancel() }
            loadJobs.clear()
            uiState.value.modules.map { it.sourceUrl }.distinct().forEach { url ->
                resolveBookSource(url)?.let { syncModulesFromSource(it) }
            }
            _moduleContentStates.value = emptyMap()
            uiState.map { it.modules }
                .first { modules -> modules.all { it.state !is ModuleLoadState.Loading } }
            _isRefreshing.value = false
        }
    }

    fun retryModule(globalId: String) {
        _moduleContentStates.update { it + (globalId to ModuleLoadState.Loading) }
    }

    fun toggleManageMode() = _isManageMode.update { !it }

    fun setModuleVisible(id: String, visible: Boolean) {
        _pendingEnabled.update { it + (id to visible) }
        viewModelScope.launch {
            if (gateway.getById(id) != null) gateway.setEnabled(id, visible)
            else {
                val parts = id.split("::")
                if (parts.size >= 3) {
                    val setId = parts[0];
                    val sourceUrl = parts[1]
                    val key = parts.subList(2, parts.size).joinToString("::")
                    ensureModuleInDb(sourceUrl, key, id, setId)
                    gateway.setEnabled(id, visible)
                }
            }
            _pendingEnabled.update { it - id }
            notifyConfigChanged()
        }
    }

    fun toggleSourceFilter(sourceUrl: String, isEnabled: Boolean) {
        val hidden = GSON.fromJsonArray<String>(
            homepageSettingsGateway.currentSettings.hiddenSourceUrlsJson
        )
            .getOrDefault(emptyList()).toMutableSet()
        if (isEnabled) hidden.remove(sourceUrl) else hidden.add(sourceUrl)
        viewModelScope.launch {
            homepageSettingsGateway.setHiddenSourceUrlsJson(GSON.toJson(hidden.toList()))
            notifyConfigChanged()
        }
    }

    private suspend fun ensureSetForSource(sourceUrl: String, sourceName: String): String {
        val setId = "src_$sourceUrl"
        if (gateway.getCustomSetById(setId) == null) gateway.upsertCustomSet(
            CustomSetItem(
                id = setId,
                name = sourceName
            )
        )
        return setId
    }

    fun addCustomModule(sourceUrl: String, targetSetId: String?, def: ModuleDef) {
        val key = def.key.ifBlank { def.title };
        val setId = targetSetId ?: "src_$sourceUrl"
        if (isInfinite(def.type, def.layoutConfig)) {
            if (allModulesCache.value.any {
                    it.customSetId == setId && isInfinite(
                        it.type,
                        it.layoutConfig
                    )
                }) {
                viewModelScope.launch {
                    _effects.emit(
                        HomepageEffect.ShowSnackbar(
                            getApplication<Application>().getString(
                                R.string.homepage_module_duplicate_infinite
                            )
                        )
                    )
                }
                return
            }
        }
        val id = ModuleDef.globalIdOf(sourceUrl, key, setId)
        val module = ModuleItem(
            id = id, sourceUrl = sourceUrl, moduleKey = key, type = def.type, title = def.title,
            args = def.args, layoutConfig = def.layoutConfig, url = def.url, isEnabled = true,
            isUserCreated = true, customSetId = setId, syncedAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            val source = bookSourceRepository.getBookSource(sourceUrl)
            if (source != null) ensureSetForSource(sourceUrl, source.bookSourceName)
            gateway.upsertAll(listOf(module))
            _pendingUserModules.update { list -> if (list.any { it.id == id }) list else list + module }
            notifyConfigChanged()
        }
    }

    fun getSourceExploreKinds(sourceUrl: String): List<Pair<String, String>> = emptyList()

    fun updateModule(globalId: String, def: ModuleDef) {
        viewModelScope.launch {
            val existing = gateway.getById(globalId) ?: return@launch
            // 校验：如果是变更为无限流，检查集内是否已有其他无限流模块
            if (isInfinite(def.type, def.layoutConfig)) {
                val hasOtherInfinite = allModulesCache.value.any {
                    it.customSetId == existing.customSetId && it.id != globalId && isInfinite(
                        it.type,
                        it.layoutConfig
                    )
                }
                if (hasOtherInfinite) {
                    _effects.emit(
                        HomepageEffect.ShowSnackbar(
                            getApplication<Application>().getString(
                                R.string.homepage_module_duplicate_infinite
                            )
                        )
                    )
                    return@launch
                }
            }
            gateway.upsertAll(
                listOf(
                    existing.copy(
                        customTitle = def.title.takeIf { it != existing.title }, type = def.type,
                        url = def.url, args = def.args, layoutConfig = def.layoutConfig,
                        isUserCreated = true, syncedAt = System.currentTimeMillis()
                    )
                )
            )
            notifyConfigChanged()
        }
    }

    fun setModuleCustomSetTitle(globalId: String, customSetTitle: String?) {
        viewModelScope.launch {
            gateway.setCustomSetTitle(
                globalId,
                customSetTitle
            ); notifyConfigChanged()
        }
    }

    fun deleteModule(globalId: String) {
        viewModelScope.launch {
            gateway.delete(globalId); _moduleContentStates.update { it - globalId }
            loadJobs.remove(globalId)?.cancel(); _pendingEnabled.update { it - globalId }
            _pendingUserModules.update { it.filter { m -> m.id != globalId } }
            notifyConfigChanged()
        }
    }

    fun reorderJoinedModules(orderedIds: List<String>) {
        viewModelScope.launch {
            val orders = orderedIds.mapIndexed { index, id -> id to index }.toMap()
            gateway.batchSetSortOrders(orders); notifyConfigChanged()
        }
    }

    fun reorderCustomSets(orderedUrls: List<String>) {
        viewModelScope.launch {
            val orders =
                orderedUrls.mapIndexed { index, url -> customSetIdFromUrl(url) to index }.toMap()
            gateway.batchSetCustomSetSortOrders(orders); notifyConfigChanged()
        }
    }

    fun getJoinedModules(sourceUrl: String): List<HomepageModuleManageUi> {
        val isSet = isCustomSetUrl(sourceUrl);
        val setId = if (isSet) customSetIdFromUrl(sourceUrl) else null
        val dbModules =
            if (isSet) allModulesCache.value.filter { it.customSetId == setId } else allModulesCache.value.filter { it.sourceUrl == sourceUrl }
        val dbIds = dbModules.map { it.id }.toSet()
        val pendingModules = _pendingUserModules.value.filter { pending ->
            val matches =
                if (isSet) pending.customSetId == setId else pending.sourceUrl == sourceUrl
            matches && pending.id !in dbIds
        }
        return (dbModules + pendingModules).map { uiFromModule(it) }
    }

    fun getAllModulesGroupedBySource(): Map<String, List<HomepageModuleManageUi>> =
        allModulesCache.value.distinctBy { it.sourceUrl to it.moduleKey }.map { uiFromModule(it) }
            .groupBy { it.sourceUrl }

    fun getSourceName(sourceUrl: String): String =
        _bookSourcePartsCache.value[sourceUrl]?.bookSourceName
            ?: _bookSourcesCache.value[sourceUrl]?.bookSourceName
            ?: sourceUrl

    fun assignModuleToCustomSet(moduleId: String, customSetId: String?) {
        viewModelScope.launch {
            val existing = gateway.getById(moduleId) ?: return@launch
            if (customSetId == null) {
                if (existing.customSetId != "src_${existing.sourceUrl}") gateway.delete(moduleId)
            } else {
                // 校验：目标集是否已存在无限模块
                if (isInfinite(existing.type, existing.layoutConfig)) {
                    val hasInfinite = allModulesCache.value.any {
                        it.customSetId == customSetId && isInfinite(it.type, it.layoutConfig)
                    }
                    if (hasInfinite) {
                        _effects.emit(
                            HomepageEffect.ShowSnackbar(
                                getApplication<Application>().getString(
                                    R.string.homepage_module_duplicate_infinite
                                )
                            )
                        )
                        return@launch
                    }
                }
                val newId =
                    ModuleDef.globalIdOf(existing.sourceUrl, existing.moduleKey, customSetId)
                gateway.upsertAll(
                    listOf(
                        existing.copy(
                            id = newId,
                            customSetId = customSetId,
                            isEnabled = true,
                            isUserCreated = true
                        )
                    )
                )
            }
            notifyConfigChanged()
        }
    }

    fun syncSourceModules(sourceUrl: String) {
        viewModelScope.launch { resolveBookSource(sourceUrl)?.let { syncModulesFromSource(it) } }
    }

    fun getSourceModules(
        sourceUrl: String,
        targetSetId: String? = null
    ): List<HomepageModuleManageUi> {
        val source = resolveBookSource(sourceUrl) ?: return emptyList()
        val json = source.homepageModules ?: return emptyList()
        val jsonDefs = parseModuleDefs(source, json)
        val effectiveSetId = targetSetId ?: "src_$sourceUrl"
        val joinedKeys =
            allModulesCache.value.filter { it.sourceUrl == sourceUrl && it.customSetId == effectiveSetId }
                .map { it.moduleKey }.toSet()
        return jsonDefs.map { def ->
            HomepageModuleManageUi(
                id = ModuleDef.globalIdOf(sourceUrl, def.key, effectiveSetId),
                sourceUrl = def.sourceUrl,
                moduleKey = def.key,
                title = def.title,
                isVisible = joinedKeys.contains(def.key),
                customSetId = if (joinedKeys.contains(def.key)) effectiveSetId else null,
                originalTitle = def.title,
                type = def.type,
                url = def.url,
                args = def.args,
            )
        }
    }

    fun addButtonGroupFromKinds(
        sourceUrl: String,
        targetSetId: String?,
        title: String,
        kindTitles: List<String>
    ) {
        val key = kindTitles.firstOrNull() ?: title;
        val setId = targetSetId ?: "src_$sourceUrl"
        val id = ModuleDef.globalIdOf(sourceUrl, key, setId)
        val module = ModuleItem(
            id = id,
            sourceUrl = sourceUrl,
            moduleKey = key,
            type = "buttonGroup",
            title = title,
            args = GSON.toJson(kindTitles),
            isEnabled = true,
            isUserCreated = true,
            customSetId = setId,
            syncedAt = System.currentTimeMillis(),
        )
        viewModelScope.launch {
            val source = bookSourceRepository.getBookSource(sourceUrl)
            if (source != null) ensureSetForSource(sourceUrl, source.bookSourceName)
            gateway.upsertAll(listOf(module))
            _pendingUserModules.update { list -> if (list.any { it.id == id }) list else list + module }
            notifyConfigChanged()
        }
    }

    fun addRankingFromKinds(
        sourceUrl: String,
        targetSetId: String?,
        title: String,
        type: String,
        kindTitles: List<String>
    ) {
        if (
            kindTitles.isEmpty() ||
            (type != HomepageModuleType.Ranking.key &&
                    type != HomepageModuleType.GridRanking.key)
        ) {
            return
        }
        viewModelScope.launch {
            val source = bookSourceRepository.getBookSource(sourceUrl) ?: return@launch
            val allKinds = withContext(Dispatchers.IO) { source.exploreKinds() }
            val selectedKinds = kindTitles.mapNotNull { selectedTitle ->
                allKinds.find { it.title == selectedTitle }
            }
            if (selectedKinds.isEmpty()) return@launch

            val setId = targetSetId ?: ensureSetForSource(sourceUrl, source.bookSourceName)
            val isGroup = selectedKinds.size > 1
            val key = if (isGroup) {
                "${type}_${jsonHash(GSON.toJson(kindTitles)).take(12)}"
            } else {
                selectedKinds.first().title
            }
            val id = ModuleDef.globalIdOf(sourceUrl, key, setId)
            val module = ModuleItem(
                id = id,
                sourceUrl = sourceUrl,
                moduleKey = key,
                type = type,
                title = title,
                args = if (isGroup) {
                    GSON.toJson(
                        RankingKindsArgs(
                            isHomepageRankingGroup = true,
                            kindTitles = selectedKinds.map { it.title }
                        )
                    )
                } else {
                    null
                },
                url = if (isGroup) null else selectedKinds.first().url,
                isEnabled = true,
                isUserCreated = true,
                customSetId = setId,
                syncedAt = System.currentTimeMillis(),
            )
            gateway.upsertAll(listOf(module))
            _pendingUserModules.update { pending ->
                if (pending.any { it.id == id }) pending else pending + module
            }
            notifyConfigChanged()
        }
    }

    fun joinModule(sourceUrl: String, targetSetId: String?, def: ModuleDef) =
        addCustomModule(sourceUrl, targetSetId, def)

    private fun uiFromModule(module: ModuleItem) = HomepageModuleManageUi(
        id = module.id,
        sourceUrl = module.sourceUrl,
        moduleKey = module.moduleKey,
        title = module.displayTitle,
        customSetTitle = module.customSetTitle,
        customSetId = module.customSetId,
        isVisible = _pendingEnabled.value[module.id] ?: module.isEnabled,
        type = module.type,
        url = module.url,
        args = module.args,
        layoutConfig = module.layoutConfig,
        originalTitle = module.title,
    )

    fun createCustomSet(name: String) {
        viewModelScope.launch { gateway.createCustomSet(name); notifyConfigChanged() }
    }

    fun renameCustomSet(id: String, name: String) {
        viewModelScope.launch { gateway.renameCustomSet(id, name); notifyConfigChanged() }
    }
    fun deleteCustomSet(id: String) {
        viewModelScope.launch {
            val ids = allModulesCache.value.filter { it.customSetId == id }.map { it.id }
            gateway.deleteCustomSet(id)
            ids.forEach { mid ->
                _moduleContentStates.update { it - mid }; loadJobs.remove(mid)
                ?.cancel(); _pendingEnabled.update { it - mid }
            }
            notifyConfigChanged()
        }
    }

    fun getCurrentBookShelfState(book: SearchBook): BookShelfState {
        return resolveBookShelfStateUseCase.execute(
            name = book.name,
            author = book.author,
            url = book.bookUrl,
            shelf = _bookshelf.value
        )
    }

    fun onAddToShelf(book: SearchBook) {
        execute {
            addToBookshelfUseCase.execute(book)
        }
    }

    fun saveSearchBook(book: SearchBook) {
        viewModelScope.launch {
            saveSearchBooksUseCase.save(book)
        }
    }

    fun onBookClick(book: SearchBook, sharedCoverKey: String?) {
        viewModelScope.launch {
            saveSearchBooksUseCase.save(book)
            _effects.emit(
                HomepageEffect.NavigateToBookInfo(
                    book.name,
                    book.author,
                    book.bookUrl,
                    book.origin,
                    book.coverUrl,
                    sharedCoverKey
                )
            )
        }
    }

    fun onModuleHeaderClick(sourceUrl: String, exploreUrl: String?, title: String?) {
        viewModelScope.launch {
            _effects.emit(
                HomepageEffect.NavigateToExploreShow(
                    title,
                    sourceUrl,
                    exploreUrl
                )
            )
        }
    }

    private fun resolveBookSource(sourceUrl: String): BookSource? =
        _bookSourcesCache.value[sourceUrl] ?: bookSourceRepository.getBookSourceSync(sourceUrl)

    private suspend fun ensureModuleInDb(
        sourceUrl: String,
        moduleKey: String,
        id: String,
        setId: String
    ) {
        if (gateway.getById(id) != null) return
        val source = resolveBookSource(sourceUrl) ?: return
        val defs = parseModuleDefs(source, source.homepageModules ?: return)
        val def = defs.find { it.key == moduleKey } ?: return
        gateway.upsertAll(
            listOf(
                ModuleItem(
                    id = id,
                    sourceUrl = sourceUrl,
                    moduleKey = moduleKey,
                    type = def.type,
                    title = def.title,
                    args = def.args,
                    url = def.url,
                    isEnabled = true,
                    customSetId = setId,
                )
            )
        )
    }

    private fun notifyConfigChanged() {
        _configVersion.update { it + 1 }
    }
}

private data class HomepageUiFlags(
    val isRefreshing: Boolean,
    val isManageMode: Boolean
)

private data class RankingKindsArgs(
    val isHomepageRankingGroup: Boolean = false,
    val kindTitles: List<String> = emptyList()
)

private fun ModuleItem.rankingKindTitles(): List<String>? {
    if (
        type != HomepageModuleType.Ranking.key &&
        type != HomepageModuleType.GridRanking.key
    ) {
        return null
    }
    val rankingArgs = args ?: return null
    return runCatching {
        GSON.fromJson(rankingArgs, RankingKindsArgs::class.java)
            ?.takeIf { it.isHomepageRankingGroup }
            ?.kindTitles
            ?.takeIf { it.size > 1 }
    }.getOrNull()
}

private fun ModuleLoadState.mapBooks(
    transform: (HomepageBookItemUi) -> HomepageBookItemUi
): ModuleLoadState = when (this) {
    is ModuleLoadState.Loaded -> copy(
        books = books.map(transform).toImmutableList()
    )

    is ModuleLoadState.Rankings -> copy(
        sources = sources.map { source ->
            source.copy(state = source.state.mapBooks(transform))
        }.toImmutableList()
    )

    else -> this
}
