package io.legado.app.ui.replace

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseRuleEvent
import io.legado.app.base.BaseRuleViewModel
import io.legado.app.constant.AppPattern
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.BookContentProcess
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.repository.ReadSettingsRepository
import io.legado.app.data.repository.ReplaceRuleRepository
import io.legado.app.data.repository.UploadRepository
import io.legado.app.domain.gateway.BookContentProcessGateway
import io.legado.app.domain.model.TextProcessAction
import io.legado.app.domain.model.TextProcessAnchor
import io.legado.app.help.ReplaceAnalyzer
import io.legado.app.help.config.AppConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ContentProcessConfigUiState
import io.legado.app.ui.book.read.ContentProcessItemUi
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.list.InteractionState
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getPrefString
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.putPrefString
import io.legado.app.utils.splitNotBlank
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReplaceRuleViewModel(
    application: Application,
    uploadRepository: UploadRepository,
    private val bookContentProcessGateway: BookContentProcessGateway,
    private val readSettingsRepository: ReadSettingsRepository,
    private val repository: ReplaceRuleRepository,
) : BaseRuleViewModel<ReplaceRuleItemUi, ReplaceRule, Long, ReplaceRuleUiState>(
    application,
    ReplaceRuleUiState(interaction = InteractionState(isLoading = true)),
    uploadRepository
) {
    private val _sortMode = MutableStateFlow(context.getPrefString(PreferKey.replaceSortMode, "desc") ?: "desc")
    val sortMode = _sortMode.asStateFlow()
    private val _group = MutableStateFlow<String?>(null)
    val group = _group.asStateFlow()

    private val _effects = MutableSharedFlow<ReplaceRuleEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private data class BookSpecificState(
        val bookUrl: String? = null,
        val replaceEnabled: Boolean = false,
        val effectiveRules: ImmutableList<ReplaceRule> = persistentListOf(),
        val chineseConvertActive: Boolean = false,
        val reSegmentActive: Boolean = false,
        val contentProcessState: ContentProcessConfigUiState = ContentProcessConfigUiState(),
        val showEffectiveReplaces: Boolean = false,
        val showContentProcesses: Boolean = false,
    )

    private val _bookState = MutableStateFlow(BookSpecificState())

    // Must use `by lazy` — super.uiState depends on rawDataFlow (declared below),
    // and eager initialization would access it before construction completes.
    override val uiState: StateFlow<ReplaceRuleUiState> by lazy {
        combine(
            super.uiState,
            _bookState
        ) { baseState, bookState ->
            baseState.copy(
                bookUrl = bookState.bookUrl,
                replaceEnabled = bookState.replaceEnabled,
                effectiveRules = bookState.effectiveRules,
                chineseConvertActive = bookState.chineseConvertActive,
                reSegmentActive = bookState.reSegmentActive,
                contentProcessState = bookState.contentProcessState,
                showEffectiveReplaces = bookState.showEffectiveReplaces,
                showContentProcesses = bookState.showContentProcesses,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = initialState
        )
    }

    val allGroups: StateFlow<List<String>> = repository.flowGroups()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun onIntent(intent: ReplaceRuleIntent) {
        when (intent) {
            is ReplaceRuleIntent.SetSearchMode -> setSearchMode(intent.active)
            is ReplaceRuleIntent.UpdateSearchQuery -> setSearchKey(intent.query)
            ReplaceRuleIntent.ClearSelection -> setSelection(emptySet())
            ReplaceRuleIntent.SelectAll -> selectAll()
            ReplaceRuleIntent.InvertSelection -> invertSelection()
            is ReplaceRuleIntent.SetSelection -> setSelection(intent.ids)
            is ReplaceRuleIntent.ToggleSelection -> toggleSelection(intent.id)
            ReplaceRuleIntent.EnableSelection -> {
                enableSelectionByIds(uiState.value.selectedIds)
                setSelection(emptySet())
            }
            ReplaceRuleIntent.DisableSelection -> {
                disableSelectionByIds(uiState.value.selectedIds)
                setSelection(emptySet())
            }
            ReplaceRuleIntent.DeleteSelection -> {
                delSelectionByIds(uiState.value.selectedIds)
                setSelection(emptySet())
            }
            ReplaceRuleIntent.UploadSelection -> {
                val state = uiState.value
                uploadSelectedRules(state.selectedIds, state.items)
            }
            is ReplaceRuleIntent.ExportSelection -> {
                val state = uiState.value
                exportToUri(intent.uri, state.items, state.selectedIds)
            }
            is ReplaceRuleIntent.MoveItem -> moveItemInList(intent.from, intent.to)
            ReplaceRuleIntent.SaveSortOrder -> saveSortOrder()
            is ReplaceRuleIntent.DeleteRule -> delete(intent.rule)
            is ReplaceRuleIntent.SetRuleEnabled -> setEnabled(intent.id, intent.enabled)
            is ReplaceRuleIntent.CopyRule -> { /* not implemented for ReplaceRule */ }
            is ReplaceRuleIntent.ImportSource -> importSource(intent.text)
            ReplaceRuleIntent.CancelImport -> cancelImport()
            is ReplaceRuleIntent.ToggleImportSelection -> toggleImportSelection(intent.index)
            is ReplaceRuleIntent.ToggleImportAll -> toggleImportAll(intent.isSelected)
            is ReplaceRuleIntent.UpdateImportItem -> updateImportItem(intent.index, intent.rule)
            ReplaceRuleIntent.SaveImportedRules -> saveImportedRules()
            // ReplaceRule-specific
            is ReplaceRuleIntent.SetGroup -> setGroup(intent.groupName)
            is ReplaceRuleIntent.SetSortMode -> setSortMode(intent.mode)
            is ReplaceRuleIntent.ToTop -> toTop(intent.rule)
            is ReplaceRuleIntent.ToBottom -> toBottom(intent.rule)
            is ReplaceRuleIntent.TopSelectByIds -> topSelectByIds(intent.ids)
            is ReplaceRuleIntent.BottomSelectByIds -> bottomSelectByIds(intent.ids)
            is ReplaceRuleIntent.AddGroup -> addGroup(intent.group)
            is ReplaceRuleIntent.DeleteGroup -> delGroup(intent.group)
            is ReplaceRuleIntent.UpGroup -> upGroup(intent.oldGroup, intent.newGroup)
            // Book-specific
            is ReplaceRuleIntent.InitBookData -> initBookData(intent.bookUrl)
            ReplaceRuleIntent.ToggleReplaceEnable -> toggleReplaceEnable()
            ReplaceRuleIntent.ShowEffectiveReplaces -> _bookState.update { it.copy(showEffectiveReplaces = true) }
            ReplaceRuleIntent.ShowContentProcesses -> {
                _bookState.update { it.copy(showContentProcesses = true) }
                loadContentProcesses()
            }
            ReplaceRuleIntent.DismissEffectiveReplaces -> _bookState.update { it.copy(showEffectiveReplaces = false) }
            ReplaceRuleIntent.DismissContentProcesses -> _bookState.update { it.copy(showContentProcesses = false) }
            is ReplaceRuleIntent.DisableEffectiveRule -> viewModelScope.launch {
                repository.insert(intent.rule.copy(isEnabled = false))
            }
            ReplaceRuleIntent.DisableChineseConverter -> {
                viewModelScope.launch { readSettingsRepository.setChineseConverterType(0) }
                _bookState.update { it.copy(chineseConvertActive = false) }
            }
            ReplaceRuleIntent.DisableReSegment -> {
                ReadBook.book?.setReSegment(false)
                ReadBook.loadContent(false)
                _bookState.update { it.copy(reSegmentActive = false) }
            }
            is ReplaceRuleIntent.ToggleContentProcess -> viewModelScope.launch {
                bookContentProcessGateway.setEnabled(intent.id, intent.enabled)
                loadContentProcesses()
            }
            is ReplaceRuleIntent.RequestDeleteContentProcess -> _bookState.update {
                it.copy(contentProcessState = it.contentProcessState.copy(deleteItem = intent.item))
            }
            ReplaceRuleIntent.ConfirmDeleteContentProcess -> {
                val deleteItem = _bookState.value.contentProcessState.deleteItem
                if (deleteItem != null) {
                    viewModelScope.launch { bookContentProcessGateway.delete(deleteItem.id) }
                    _bookState.update {
                        it.copy(contentProcessState = it.contentProcessState.copy(deleteItem = null))
                    }
                }
            }
            ReplaceRuleIntent.DismissDeleteContentProcess -> _bookState.update {
                it.copy(contentProcessState = it.contentProcessState.copy(deleteItem = null))
            }
        }
    }

    private fun setGroup(groupName: String?) {
        _group.value = if (groupName == "全部" || groupName.isNullOrBlank()) {
            null
        } else {
            groupName
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val rawDataFlow: Flow<List<ReplaceRule>> =
        combine(_group, _sortMode) { group, sortMode ->
            group to sortMode
        }.flatMapLatest { (group, sortMode) ->
            val baseFlow = when (group) {
                null -> repository.flowAll()
                "未分组" -> repository.flowNoGroup()
                else -> repository.flowGroupSearch(group)
            }

            baseFlow.map { rules ->
                sortRules(rules, sortMode)
            }
        }

    override fun filterData(
        data: List<ReplaceRule>,
        searchKey: String,
        groupFilter: String
    ): List<ReplaceRule> {
        return if (searchKey.isEmpty() && groupFilter.isEmpty()) data
        else data.filter {
            val key = searchKey.ifEmpty { groupFilter }
            it.name.contains(key, ignoreCase = true)
                    || it.pattern.contains(key, ignoreCase = true)
                    || it.replacement.contains(key, ignoreCase = true)
                    || it.scope?.contains(key, ignoreCase = true) == true
        }
    }


    override fun composeUiState(
        items: List<ReplaceRuleItemUi>,
        selectedIds: Set<Long>,
        isSearch: Boolean,
        isUploading: Boolean,
        importState: BaseImportUiState<ReplaceRule>
    ): ReplaceRuleUiState {
        return ReplaceRuleUiState(
            items = items.toImmutableList(),
            selectedIds = selectedIds.toImmutableSet(),
            searchKey = _searchKey.value,
            sortMode = _sortMode.value,
            selectedGroup = _group.value,
            interaction = InteractionState(
                isSearchMode = isSearch,
                isUploading = isUploading || (importState is BaseImportUiState.Loading),
                isLoading = false
            )
        )
    }

    override fun ReplaceRule.toUiItem() = ReplaceRuleItemUi(
        id = id,
        name = name,
        isEnabled = isEnabled,
        group = group,
        pattern = pattern,
        replacement = replacement,
        scope = scope,
        scopeTitle = scopeTitle,
        scopeContent = scopeContent,
        excludeScope = excludeScope,
        isRegex = isRegex,
        timeoutMillisecond = timeoutMillisecond,
        order = order
    )

    override fun ruleItemToEntity(item: ReplaceRuleItemUi): ReplaceRule = item.toEntity()

    override suspend fun generateJson(entities: List<ReplaceRule>): String = GSON.toJson(entities)

    override fun parseImportRules(text: String): List<ReplaceRule> {
        return when {
            text.isJsonArray() -> ReplaceAnalyzer.jsonToReplaceRules(text).getOrThrow()
            text.isJsonObject() -> listOf(ReplaceAnalyzer.jsonToReplaceRule(text).getOrThrow())
            else -> throw Exception("格式不正确")
        }
    }

    override fun hasChanged(newRule: ReplaceRule, oldRule: ReplaceRule): Boolean {
        return newRule.pattern != oldRule.pattern
                || newRule.replacement != oldRule.replacement
                || newRule.isRegex != oldRule.isRegex
                || newRule.scope != oldRule.scope
    }

    override suspend fun findOldRule(newRule: ReplaceRule): ReplaceRule? {
        return repository.findById(newRule.id)
    }

    override fun saveImportedRules() {
        val state = _importState.value as? BaseImportUiState.Success<ReplaceRule> ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val rulesToSave = state.items
                .filter { it.isSelected }
                .map { wrapper ->
                    val rule = wrapper.data
                    val oldRule = wrapper.oldData

                    if (state.keepOriginalName && oldRule != null) {
                        rule.name = oldRule.name
                    }
                    val targetGroup = state.customGroup?.trim()
                    if (!targetGroup.isNullOrEmpty()) {
                        if (state.isAddGroup) {
                            val groups = linkedSetOf<String>()
                            rule.group?.splitNotBlank(AppPattern.splitGroupRegex)?.let { groups.addAll(it) }
                            groups.add(targetGroup)
                            rule.group = groups.joinToString(",")
                        } else {
                            rule.group = targetGroup
                        }
                    }
                    rule
                }
            if (rulesToSave.isNotEmpty()) {
                rulesToSave.forEach { rule ->
                    repository.insert(rule)
                }
                withContext(Dispatchers.Main) {
                    _importState.value = BaseImportUiState.Idle
                    _eventChannel.send(BaseRuleEvent.ShowSnackbar("成功导入 ${rulesToSave.size} 条规则"))
                }
            }
        }
    }

    private fun sortRules(rules: List<ReplaceRule>, mode: String): List<ReplaceRule> {
        val comparator = when (mode) {
            "asc" -> compareBy<ReplaceRule> { it.order.toLong() }
            "desc" -> compareByDescending<ReplaceRule> { it.order.toLong() }
            "name_asc" -> compareBy<ReplaceRule> { it.name.lowercase() }
            "name_desc" -> compareByDescending<ReplaceRule> { it.name.lowercase() }
            else -> null
        }
        return if (comparator != null) rules.sortedWith(comparator) else rules
    }

    private fun setSortMode(mode: String) {
        _sortMode.value = mode
        context.putPrefString(PreferKey.replaceSortMode, mode)
    }

    private fun saveSortOrder() {
        val currentLocal = _localItems.value ?: return
        viewModelScope.launch {
            repository.moveOrder(currentLocal.map { it.toEntity() }, _sortMode.value == "desc")
            _localItems.value = null
        }
    }


    private fun setEnabled(id: Long, enabled: Boolean) =
        viewModelScope.launch { repository.setEnabled(id, enabled) }

    private fun delete(rule: ReplaceRule) = viewModelScope.launch { repository.delete(rule) }
    fun enableSelectionByIds(ids: Set<Long>) = viewModelScope.launch { repository.enableByIds(ids) }
    fun disableSelectionByIds(ids: Set<Long>) =
        viewModelScope.launch { repository.disableByIds(ids) }

    fun delSelectionByIds(ids: Set<Long>) = viewModelScope.launch {
        repository.deleteByIds(ids)
        _selectedIds.update { it - ids }
    }

    private fun selectAll() {
        setSelection(uiState.value.items.map { it.id }.toSet())
    }

    private fun invertSelection() {
        val state = uiState.value
        setSelection(state.items.map { it.id }.toSet() - state.selectedIds)
    }

    private fun addGroup(group: String) = viewModelScope.launch { repository.addGroup(group) }
    private fun delGroup(group: String) = viewModelScope.launch { repository.delGroup(group) }

    private fun toTop(rule: ReplaceRule) =
        viewModelScope.launch { repository.toTop(rule, _sortMode.value == "desc") }

    private fun toBottom(rule: ReplaceRule) =
        viewModelScope.launch { repository.toBottom(rule, _sortMode.value == "desc") }

    private fun topSelectByIds(ids: Set<Long>) =
        viewModelScope.launch { repository.topByIds(ids, _sortMode.value == "desc") }

    private fun bottomSelectByIds(ids: Set<Long>) =
        viewModelScope.launch { repository.bottomByIds(ids, _sortMode.value == "desc") }

    private fun upGroup(oldGroup: String, newGroup: String?) =
        viewModelScope.launch { repository.upGroup(oldGroup, newGroup) }

    //region Book-specific methods

    fun emitOpenReplaceEditor(id: Long, pattern: String?) {
        _effects.tryEmit(ReplaceRuleEffect.OpenReplaceEditor(id, pattern))
    }

    private fun initBookData(bookUrl: String) {
        val book = ReadBook.book
        val textChapter = ReadBook.curTextChapter
        if (book != null && book.bookUrl == bookUrl) {
            val effectiveRules = textChapter?.effectiveReplaceRules.orEmpty().toImmutableList()
            val replaceEnabled = book.getUseReplaceRule(AppConfig.replaceEnableDefault)
            val chineseConvertActive = readSettingsRepository.currentSettings.chineseConverterType > 0
            val reSegmentActive = book.getReSegment()
            _bookState.update {
                it.copy(
                    bookUrl = bookUrl,
                    replaceEnabled = replaceEnabled,
                    effectiveRules = effectiveRules,
                    chineseConvertActive = chineseConvertActive,
                    reSegmentActive = reSegmentActive,
                )
            }
        }
    }

    private fun toggleReplaceEnable() {
        ReadBook.book?.let { book ->
            val enabled = !book.getUseReplaceRule(AppConfig.replaceEnableDefault)
            book.setUseReplaceRule(enabled)
            ReadBook.saveRead()
            _bookState.update { it.copy(replaceEnabled = enabled) }
        }
    }

    private fun loadContentProcesses() {
        val book = ReadBook.book ?: return
        val chapterIndex = ReadBook.durChapterIndex
        _bookState.update {
            it.copy(contentProcessState = it.contentProcessState.copy(isLoading = true, errorMessage = null))
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                bookContentProcessGateway.getForChapter(book.bookUrl, chapterIndex)
                    .mapNotNull { it.toContentProcessItemUi() }
                    .toImmutableList()
            }.onSuccess { items ->
                _bookState.update {
                    it.copy(contentProcessState = it.contentProcessState.copy(isLoading = false, items = items))
                }
            }.onFailure { error ->
                _bookState.update {
                    it.copy(contentProcessState = it.contentProcessState.copy(
                        isLoading = false,
                        errorMessage = error.localizedMessage
                    ))
                }
            }
        }
    }

    private fun BookContentProcess.toContentProcessItemUi(): ContentProcessItemUi? {
        val anchor = GSON.fromJsonObject<TextProcessAnchor>(anchorJson).getOrNull()
            ?: return null
        val action = GSON.fromJsonObject<TextProcessAction>(actionJson).getOrNull()
            ?: return null
        return ContentProcessItemUi(
            id = id,
            kind = kind,
            actionType = action.type,
            enabled = enabled && status == BookContentProcess.STATUS_ACTIVE,
            chapterIndex = chapterIndex ?: anchor.chapterIndex,
            selectedText = anchor.selectedText,
            replacementText = action.replacement ?: action.text.orEmpty(),
            createdAt = createdAt,
        )
    }

    //endregion
}
