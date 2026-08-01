package io.legado.app.ui.book.source.manage

import android.app.Application
import android.text.TextUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonParser
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppPattern
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.repository.BookSourceRepository
import io.legado.app.domain.gateway.BookSourceCheckGateway
import io.legado.app.domain.gateway.CheckSourceSettings
import io.legado.app.domain.gateway.CheckSourceSettingsGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.usecase.StartBookSourceCheckUseCase
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.http.decompressed
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.help.source.SourceHelp
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.importComponents.ImportItemWrapper
import io.legado.app.ui.widget.components.importComponents.ImportStatus
import io.legado.app.utils.GSON
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.splitNotBlank
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BookSourceViewModel(
    private val application: Application,
    private val repository: BookSourceRepository,
    private val otherSettingsGateway: OtherSettingsGateway,
    private val checkGateway: BookSourceCheckGateway,
    private val checkSettingsGateway: CheckSourceSettingsGateway,
    private val startBookSourceCheck: StartBookSourceCheckUseCase,
) : ViewModel() {
    companion object {
        const val FILTER_ENABLED = "@enabled"
        const val FILTER_DISABLED = "@disabled"
        const val FILTER_LOGIN = "@login"
        const val FILTER_NO_GROUP = "@noGroup"
        const val FILTER_ENABLED_EXPLORE = "@enabledExplore"
        const val FILTER_DISABLED_EXPLORE = "@disabledExplore"
        const val PREFIX_GROUP = "group:"
    }

    private val searchKey = MutableStateFlow("")
    private val isSearchMode = MutableStateFlow(false)
    private val filter = MutableStateFlow<String?>(null)
    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val sort = MutableStateFlow(BookSourceSort.Default)
    private val sortAscending = MutableStateFlow(true)
    private val groupByDomain = MutableStateFlow(false)
    private val localItems = MutableStateFlow<List<BookSourcePart>?>(null)
    private val enabledOverrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val importState =
        MutableStateFlow<BaseImportUiState<BookSource>>(BaseImportUiState.Idle)
    private val _effects = MutableSharedFlow<BookSourceEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()
    private var checkJob: Job? = null

    init {
        viewModelScope.launch {
            var wasRunning = false
            checkGateway.state.collect { state ->
                if (wasRunning && !state.isRunning) {
                    _effects.tryEmit(BookSourceEffect.ShowSnackbar("书源校验完成"))
                }
                wasRunning = state.isRunning
            }
        }
    }

    private val listState = combine(
        repository.flowAll(),
        repository.flowGroups(),
        searchKey,
        isSearchMode,
        filter,
        selectedIds,
        sort,
        sortAscending,
        groupByDomain,
        localItems,
        enabledOverrides,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val sourceItems = (values[0] as List<BookSourcePart>)
        val groups = values[1] as List<String>
        val query = values[2] as String
        val searchMode = values[3] as Boolean
        val activeFilter = values[4] as String?
        val selected = values[5] as Set<String>
        val activeSort = values[6] as BookSourceSort
        val ascending = values[7] as Boolean
        val byDomain = values[8] as Boolean
        val local = values[9] as List<BookSourcePart>?
        val pendingEnabled = values[10] as Map<String, Boolean>
        val visible = if (local == null) {
            sourceItems.filterFor(activeFilter, query).sortFor(activeSort, ascending, byDomain)
        } else {
            val latestById = sourceItems.associateBy { it.bookSourceUrl }
            local.mapNotNull { latestById[it.bookSourceUrl] }
        }
        BookSourceUiState(
            items = visible.map { source ->
                BookSourceItemUi(
                    id = source.bookSourceUrl,
                    domain = NetworkUtils.getSubDomainOrNull(source.bookSourceUrl) ?: "#",
                    name = source.bookSourceName,
                    group = source.bookSourceGroup,
                    enabled = pendingEnabled[source.bookSourceUrl] ?: source.enabled,
                    enabledExplore = source.enabledExplore,
                    hasLoginUrl = source.hasLoginUrl,
                    hasExploreUrl = source.hasExploreUrl,
                    customOrder = source.customOrder,
                )
            }.toImmutableList(),
            selectedIds = selected.intersect(visible.map { it.bookSourceUrl }.toSet())
                .toImmutableSet(),
            searchKey = query,
            groupFilterName = activeFilter?.displayName(application),
            activeFilter = activeFilter,
            groups = groups.toImmutableList(),
            sort = activeSort,
            sortAscending = ascending,
            groupByDomain = byDomain,
            interaction = io.legado.app.ui.widget.components.list.InteractionState(isSearchMode = searchMode),
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookSourceUiState())

    val uiState = combine(
        listState,
        importState,
        checkGateway.state,
        checkSettingsGateway.settings,
    ) { state, importing, check, settings ->
        state.copy(
            items = state.items.map { it.copy(checkMessage = check.results[it.id]) }
                .toImmutableList(),
            importState = importing,
            checkProgress = if (check.isRunning) application.getString(
                io.legado.app.R.string.progress_show,
                check.currentSourceName,
                check.completed,
                check.total,
            ) else null,
            checkOptions = BookSourceCheckOptionsUi(
                timeoutSeconds = settings.timeoutMillis / 1000,
                checkSearch = settings.checkSearch,
                checkDiscovery = settings.checkDiscovery,
                checkInfo = settings.checkInfo,
                checkCategory = settings.checkCategory,
                checkContent = settings.checkContent,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookSourceUiState())

    fun onIntent(intent: BookSourceIntent) {
        when (intent) {
            is BookSourceIntent.SetSearchMode -> {
                isSearchMode.value = intent.enabled
                if (!intent.enabled) searchKey.value = ""
            }

            is BookSourceIntent.SetSearchQuery -> {
                localItems.value = null; searchKey.value = intent.query
            }

            is BookSourceIntent.SetSelection -> selectedIds.value = intent.ids
            is BookSourceIntent.ToggleSelection -> selectedIds.update { if (intent.id in it) it - intent.id else it + intent.id }
            is BookSourceIntent.SetFilter -> {
                localItems.value = null; filter.value = intent.filter
            }

            is BookSourceIntent.SetSort -> {
                localItems.value = null; sort.value = intent.sort
            }

            BookSourceIntent.ToggleSortDirection -> {
                localItems.value = null; sortAscending.update { !it }
            }

            BookSourceIntent.ToggleGroupByDomain -> {
                localItems.value = null; groupByDomain.update { !it }
            }

            is BookSourceIntent.SetEnabled -> setEnabled(intent.id, intent.enabled)
            is BookSourceIntent.SetEnabledForSelection -> launch {
                repository.setEnabled(
                    intent.enabled,
                    parts(intent.ids)
                )
            }

            is BookSourceIntent.SetExploreEnabled -> updateExplore(intent.ids, intent.enabled)
            is BookSourceIntent.Delete -> launch { repository.deleteSourceParts(parts(intent.ids)); selectedIds.update { it - intent.ids } }
            is BookSourceIntent.MoveToEdge -> moveToEdge(intent.ids, intent.toTop)
            is BookSourceIntent.MoveItem -> moveItem(intent.from, intent.to)
            BookSourceIntent.SaveSortOrder -> saveSortOrder()
            is BookSourceIntent.CommitSortOrder -> commitSortOrder(intent.ids, intent.ascending)
            is BookSourceIntent.AddToGroup -> updateGroups(intent.ids, intent.group, true)
            is BookSourceIntent.RemoveFromGroup -> updateGroups(intent.ids, intent.group, false)
            is BookSourceIntent.UpdateGroup -> updateGroup(intent.old, intent.new)
            is BookSourceIntent.DeleteGroup -> updateGroup(intent.group, "")
            is BookSourceIntent.CheckSelectedInterval -> checkInterval(intent.ids)
            is BookSourceIntent.StartCheck -> {
                if (checkJob?.isActive != true) {
                    checkJob = viewModelScope.launch {
                        checkSettingsGateway.update(intent.options.toSettings())
                        startBookSourceCheck(intent.ids, intent.keyword)
                    }
                }
            }

            is BookSourceIntent.UpdateCheckOptions -> viewModelScope.launch {
                checkSettingsGateway.update(intent.options.toSettings())
            }

            BookSourceIntent.CancelCheck -> checkJob?.cancel()
            is BookSourceIntent.Import -> importSources(intent.text)
            is BookSourceIntent.Export -> exportSources(intent.uri, intent.ids)
            is BookSourceIntent.ToggleImportItem -> updateImportItems { items ->
                items.mapIndexed { index, item -> if (index == intent.index) item.copy(isSelected = !item.isSelected) else item }
            }

            is BookSourceIntent.ToggleImportAll -> updateImportItems { items ->
                items.map {
                    it.copy(
                        isSelected = intent.selected
                    )
                }
            }

            is BookSourceIntent.UpdateImportItem -> updateImportItems { items ->
                items.mapIndexed { index, item -> if (index == intent.index) item.copy(data = intent.source) else item }
            }

            is BookSourceIntent.SelectImportStatus -> selectImportStatus(intent.status)
            is BookSourceIntent.SetImportKeepName -> updateImportOptions { it.copy(keepOriginalName = intent.enabled) }
            is BookSourceIntent.SetImportKeepGroup -> updateImportOptions {
                it.copy(
                    keepOriginalGroup = intent.enabled
                )
            }

            is BookSourceIntent.SetImportKeepEnable -> updateImportOptions {
                it.copy(
                    keepOriginalEnable = intent.enabled
                )
            }

            is BookSourceIntent.SetImportCustomGroup -> updateImportOptions {
                it.copy(
                    customGroup = intent.group?.trim()?.takeIf(String::isNotEmpty),
                    isAddGroup = intent.add
                )
            }

            BookSourceIntent.CancelImport -> importState.value = BaseImportUiState.Idle
            BookSourceIntent.SaveImportedSources -> saveImportedSources()
        }
    }

    private fun launch(block: suspend () -> Unit) =
        viewModelScope.launch(Dispatchers.IO) { block() }

    private fun setEnabled(id: String, enabled: Boolean) {
        enabledOverrides.update { it + (id to enabled) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.setEnabled(id, enabled)
                repository.flowAll().first { sources ->
                    sources.firstOrNull { it.bookSourceUrl == id }?.enabled == enabled
                }
            }
            enabledOverrides.update { it - id }
        }
    }

    private suspend fun parts(ids: Set<String>) =
        repository.getAllPart().filter { it.bookSourceUrl in ids }
    private fun updateExplore(ids: Set<String>, enabled: Boolean) =
        launch { repository.setExploreEnabled(enabled, parts(ids)) }

    private fun updateGroups(ids: Set<String>, group: String, add: Boolean) = launch {
        val changed = parts(ids).map { part ->
            part.copy().apply { if (add) addGroup(group) else removeGroup(group) }
        }
        repository.updateGroups(changed)
    }

    private fun updateGroup(old: String, new: String) = launch {
        val sources = repository.getByGroup(old)
        sources.forEach { source ->
            source.bookSourceGroup?.splitNotBlank(",")?.toHashSet()
                ?.apply { remove(old); if (new.isNotBlank()) add(new) }
                ?.let { source.bookSourceGroup = TextUtils.join(",", it) }
        }
        repository.updateSources(*sources.toTypedArray())
    }

    private fun moveToEdge(ids: Set<String>, toTop: Boolean) = launch {
        repository.moveToEdge(parts(ids), toTop)
    }

    private fun moveItem(from: Int, to: Int) {
        if (sort.value != BookSourceSort.Default || groupByDomain.value) return
        val moved = (localItems.value ?: uiState.value.items.map { item ->
            BookSourcePart(bookSourceUrl = item.id, customOrder = item.customOrder)
        }).toMutableList()
        if (from !in moved.indices || to !in moved.indices) return
        moved.add(to, moved.removeAt(from)); localItems.value = moved
    }

    private fun saveSortOrder() {
        val items = localItems.value ?: return
        launch {
            repository.updateOrder(items.mapIndexed { index, item -> item.copy(customOrder = index + 1) }); localItems.value =
            null
        }
    }

    private fun commitSortOrder(ids: List<String>, ascending: Boolean) = launch {
        val sourcesById = repository.getAllPart().associateBy { it.bookSourceUrl }
        val orderSlots = ids.mapNotNull { sourcesById[it]?.customOrder }
            .let { if (ascending) it.sorted() else it.sortedDescending() }
        val ordered = ids.mapIndexedNotNull { index, id ->
            sourcesById[id]?.copy(
                customOrder = orderSlots.getOrNull(index) ?: return@mapIndexedNotNull null
            )
        }
        repository.updateOrder(ordered)
    }

    private fun checkInterval(ids: Set<String>) {
        val items = uiState.value.items
        val positions = items.mapIndexedNotNull { index, item -> index.takeIf { item.id in ids } }
        if (positions.isNotEmpty()) selectedIds.value =
            items.subList(positions.min(), positions.max() + 1).map { it.id }.toSet()
    }

    private fun importSources(input: String) {
        importState.value = BaseImportUiState.Loading
        launch {
            runCatching {
                val text = if (input.isAbsUrl()) {
                    okHttpClient.newCallResponseBody {
                        if (input.endsWith("#requestWithoutUA")) {
                            url(input.substringBeforeLast("#requestWithoutUA")); header(
                                AppConst.UA_NAME,
                                "null"
                            )
                        } else url(input)
                    }.decompressed().text("utf-8")
                } else input
                val sources = parseImportSources(text)
                val settings = otherSettingsGateway.currentSettings
                BaseImportUiState.Success(
                    source = input,
                    items = sources.map { source ->
                        val old = repository.getBookSource(source.bookSourceUrl)
                        val status = when {
                            old == null -> ImportStatus.New
                            source.lastUpdateTime > old.lastUpdateTime -> ImportStatus.Update
                            else -> ImportStatus.Existing
                        }
                        ImportItemWrapper(
                            data = source,
                            oldData = old,
                            isSelected = status != ImportStatus.Existing,
                            status = status,
                        )
                    },
                    keepOriginalName = settings.importKeepName,
                    keepOriginalGroup = settings.importKeepGroup,
                    keepOriginalEnable = settings.importKeepEnable,
                )
            }.onSuccess { importState.value = it }
                .onFailure {
                    importState.value = BaseImportUiState.Error(it.localizedMessage ?: "导入失败")
                }
        }
    }

    private suspend fun parseImportSources(text: String): List<BookSource> {
        val sources = when {
            text.isJsonArray() -> GSON.fromJsonArray<BookSource>(text).getOrThrow()
            text.isJsonObject() -> {
                val objectValue = JsonParser.parseString(text).asJsonObject
                val sourceUrls = objectValue.getAsJsonArray("sourceUrls")
                if (sourceUrls != null) {
                    sourceUrls.flatMap { element ->
                        val url = element.asString
                        val sourceText = okHttpClient.newCallResponseBody {
                            if (url.endsWith("#requestWithoutUA")) {
                                url(url.substringBeforeLast("#requestWithoutUA")); header(
                                    AppConst.UA_NAME,
                                    "null"
                                )
                            } else url(url)
                        }.decompressed().text("utf-8")
                        parseImportSources(sourceText)
                    }
                } else listOf(GSON.fromJsonObject<BookSource>(text).getOrThrow())
            }

            else -> error("格式不正确")
        }
        require(sources.all { it.bookSourceUrl.isNotBlank() }) { "不是书源" }
        return sources
    }

    private fun updateImportItems(transform: (List<ImportItemWrapper<BookSource>>) -> List<ImportItemWrapper<BookSource>>) {
        val state = importState.value as? BaseImportUiState.Success<BookSource> ?: return
        importState.value = state.copy(items = transform(state.items), version = state.version + 1)
    }

    private fun updateImportOptions(
        transform: (BaseImportUiState.Success<BookSource>) -> BaseImportUiState.Success<BookSource>
    ) {
        val state = importState.value as? BaseImportUiState.Success<BookSource> ?: return
        val updated = transform(state)
        importState.value = updated
        viewModelScope.launch {
            otherSettingsGateway.update { settings ->
                settings.copy(
                    importKeepName = updated.keepOriginalName,
                    importKeepGroup = updated.keepOriginalGroup,
                    importKeepEnable = updated.keepOriginalEnable,
                )
            }
        }
    }

    private fun selectImportStatus(status: ImportStatus) {
        val state = importState.value as? BaseImportUiState.Success<BookSource> ?: return
        val matching = state.items.filter { it.status == status }
        val select = matching.any { !it.isSelected }
        importState.value = state.copy(
            items = state.items.map { if (it.status == status) it.copy(isSelected = select) else it }
        )
    }

    private fun saveImportedSources() {
        val state = importState.value as? BaseImportUiState.Success<BookSource> ?: return
        launch {
            val sources = state.items.filter { it.isSelected }.map { wrapper ->
                wrapper.data.copy().apply {
                    wrapper.oldData?.let { old ->
                        if (state.keepOriginalName) bookSourceName = old.bookSourceName
                        if (state.keepOriginalGroup) bookSourceGroup = old.bookSourceGroup
                        if (state.keepOriginalEnable) {
                            enabled = old.enabled
                            enabledExplore = old.enabledExplore
                        }
                        customOrder = old.customOrder
                    }
                    state.customGroup?.let { group ->
                        bookSourceGroup = if (state.isAddGroup) {
                            linkedSetOf<String>().apply {
                                bookSourceGroup?.splitNotBlank(AppPattern.splitGroupRegex)
                                    ?.let(::addAll)
                                add(group)
                            }.joinToString(",")
                        } else group
                    }
                }
            }
            SourceHelp.insertBookSource(*sources.toTypedArray())
            ContentProcessor.upReplaceRules()
            importState.value = BaseImportUiState.Idle
            _effects.tryEmit(BookSourceEffect.ShowSnackbar("导入完成"))
        }
    }

    private fun exportSources(uri: android.net.Uri, ids: Set<String>) = launch {
        runCatching {
            val selected = repository.getAll().filter { ids.isEmpty() || it.bookSourceUrl in ids }
            application.contentResolver.openOutputStream(uri)?.bufferedWriter()
                ?.use { it.write(GSON.toJson(selected)) }
                ?: error("无法打开导出文件")
        }.onSuccess { _effects.tryEmit(BookSourceEffect.ShowSnackbar("导出成功")) }
            .onFailure { _effects.tryEmit(BookSourceEffect.ShowSnackbar("导出失败: ${it.localizedMessage}")) }
    }

}

private fun BookSourceCheckOptionsUi.toSettings() = CheckSourceSettings(
    timeoutMillis = timeoutSeconds * 1000,
    checkSearch = checkSearch,
    checkDiscovery = checkDiscovery,
    checkInfo = checkInfo,
    checkCategory = checkCategory,
    checkContent = checkContent,
)

private fun List<BookSourcePart>.filterFor(filter: String?, query: String): List<BookSourcePart> =
    filter { source ->
        val filterMatch = when (filter) {
            null -> true; BookSourceViewModel.FILTER_ENABLED -> source.enabled; BookSourceViewModel.FILTER_DISABLED -> !source.enabled
            BookSourceViewModel.FILTER_LOGIN -> source.hasLoginUrl; BookSourceViewModel.FILTER_NO_GROUP -> source.bookSourceGroup.isNullOrBlank()
            BookSourceViewModel.FILTER_ENABLED_EXPLORE -> source.enabledExplore; BookSourceViewModel.FILTER_DISABLED_EXPLORE -> !source.enabledExplore
            else -> filter.startsWith(BookSourceViewModel.PREFIX_GROUP) && source.bookSourceGroup?.split(
                ","
            )?.contains(filter.removePrefix(BookSourceViewModel.PREFIX_GROUP)) == true
        }
        filterMatch && (query.isBlank() || listOf(
            source.bookSourceName,
            source.bookSourceUrl,
            source.bookSourceGroup
        ).any { it?.contains(query, true) == true })
    }

private fun List<BookSourcePart>.sortFor(
    sort: BookSourceSort,
    ascending: Boolean,
    byDomain: Boolean
): List<BookSourcePart> {
    if (byDomain) {
        val domains = associateWith { NetworkUtils.getSubDomainOrNull(it.bookSourceUrl) ?: "#" }
        return sortedWith(compareBy<BookSourcePart> { domains.getValue(it) == "#" }
            .thenBy { domains.getValue(it) }
            .thenByDescending { it.lastUpdateTime })
    }
    val comparator = when (sort) {
        BookSourceSort.Name -> compareBy<BookSourcePart> { it.bookSourceName }
        BookSourceSort.Url -> compareBy { it.bookSourceUrl }; BookSourceSort.Weight -> compareBy { it.weight }
        BookSourceSort.Update -> compareByDescending<BookSourcePart> { it.lastUpdateTime }; BookSourceSort.Respond -> compareBy { it.respondTime }
        BookSourceSort.Enable -> compareByDescending<BookSourcePart> { it.enabled }.thenBy { it.bookSourceName }
        BookSourceSort.Default -> compareBy { it.customOrder }
    }
    return if (ascending) sortedWith(comparator) else sortedWith(comparator.reversed())
}

private fun String.displayName(application: Application) = when (this) {
    BookSourceViewModel.FILTER_ENABLED -> application.getString(io.legado.app.R.string.enabled)
    BookSourceViewModel.FILTER_DISABLED -> application.getString(io.legado.app.R.string.disabled)
    BookSourceViewModel.FILTER_LOGIN -> application.getString(io.legado.app.R.string.need_login)
    BookSourceViewModel.FILTER_NO_GROUP -> application.getString(io.legado.app.R.string.no_group)
    BookSourceViewModel.FILTER_ENABLED_EXPLORE -> application.getString(io.legado.app.R.string.enabled_explore)
    BookSourceViewModel.FILTER_DISABLED_EXPLORE -> application.getString(io.legado.app.R.string.disabled_explore)
    else -> removePrefix(BookSourceViewModel.PREFIX_GROUP)
}
