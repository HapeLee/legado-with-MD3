package io.legado.app.feature.replacerules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.core.platform.JsonCodec
import io.legado.app.core.rules.RuleEntitySpec
import io.legado.app.core.rules.RuleTransferPlatform
import io.legado.app.core.rules.RuleTransferUseCase
import io.legado.app.domain.contentprocess.BookContentProcess
import io.legado.app.data.repository.ReadSettingsRepository
import io.legado.app.data.repository.UploadRepository
import io.legado.app.domain.contentprocess.BookContentProcessGateway
import io.legado.app.domain.gateway.ReplaceRuleChangeNotifier
import io.legado.app.domain.gateway.ReplaceRuleSettingsGateway
import io.legado.app.domain.model.TextProcessAction
import io.legado.app.domain.model.TextProcessAnchor
import io.legado.app.domain.model.json.isJsonArray
import io.legado.app.domain.model.json.isJsonObject
import io.legado.app.domain.rules.ReadBookReplaceSessionGateway
import io.legado.app.domain.rules.ReplaceRule
import io.legado.app.domain.rules.ReplaceRuleRepository
import io.legado.app.ui.widget.components.contentProcess.ContentProcessConfigUiState
import io.legado.app.ui.widget.components.contentProcess.ContentProcessItemUi
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.list.InteractionState
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

/**
 * 替换规则的列表/导入/导出编排 + 阅读页「本书替换状态」面板。
 *
 * M1-3x（本模块转 CMP）：**不再继承 `BaseRuleViewModel`**（那是个吃 `android.app.Application`
 * 的 `AndroidViewModel`，也是本模块进不了 `commonMain` 的最后一道墙）。导入/导出/上传的无 UI
 * 编排下沉到 [RuleTransferUseCase]，列表/搜索/选择这些纯 UI 状态留在本类。本类因此只剩
 * `androidx.lifecycle.ViewModel` 一个 Android 依赖，也不再持有 `Context` / `Uri`。
 *
 * 与迁移前逐字一致的部分：分组/排序查询、搜索匹配字段、`InteractionState` 的
 * `isUploading || importState is Loading` 合并、分组与排序的写库动作、以及所有提示文案。
 *
 * 三处**有意**的收窄，写在这里免得下一个人以为是漏搬：
 *   1. `exportToUri(android.net.Uri, …)` → `transfer.export(String, …)`：`RuleTransferPlatform`
 *      的契约本来就是 `String`（`writeExport(targetUri: String, …)`），迁移前是把 `Uri`
 *      `parse` 出来又立刻 `toString()` 送进去绕了一圈。
 *   2. `GSON`（`:core:data` 的 JVM 门面，住 androidMain）→ [JsonCodec]。两者配置严格对齐
 *      （`MapDeserializerDoubleAsIntFix` + `LONG_OR_DOUBLE` + prettyPrinting），且 `ReplaceRule`
 *      在 app 侧 `GSON` 里**没有**注册自定义 deserializer ⇒ 解析/序列化行为不变。
 *   3. 旧格式（`$.regex` / `$.useTo` 那套键名）导入文本仍由 `ReplaceAnalyzer` 解析，但那依赖
 *      `com.jayway.jsonpath`（JVM 三方库），只能由平台提供 ⇒ 抽成 [ReplaceRuleImportCompat]，
 *      Android 实现直接委托 `ReplaceAnalyzer`（见 [ReplaceRuleTransferSpec]）。
 */
class ReplaceRuleViewModel(
    uploadRepository: UploadRepository,
    transferPlatform: RuleTransferPlatform,
    legacyImportCompat: ReplaceRuleImportCompat,
    private val bookContentProcessGateway: BookContentProcessGateway,
    private val readSettingsRepository: ReadSettingsRepository,
    private val repository: ReplaceRuleRepository,
    private val readBookSession: ReadBookReplaceSessionGateway,
    private val replaceRuleSettings: ReplaceRuleSettingsGateway,
    private val changeNotifier: ReplaceRuleChangeNotifier,
) : ViewModel() {

    private val _sortMode = MutableStateFlow(replaceRuleSettings.getSortMode())
    val sortMode = _sortMode.asStateFlow()
    private val _group = MutableStateFlow<String?>(null)
    val group = _group.asStateFlow()

    private val _effects = MutableSharedFlow<ReplaceRuleEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private val transfer = RuleTransferUseCase(
        scope = viewModelScope,
        spec = ReplaceRuleTransferSpec(repository, legacyImportCompat) { count ->
            // 迁移前这条提示由 `BaseRuleViewModel` 的 `_eventChannel` 在落库后发出（文案逐字不变）。
            // 现在落库流程归 `RuleTransferUseCase`，它不产出业务文案 ⇒ 由本类转成 effect，
            // 界面自己弹 snackbar（与 tagrules 的 `SyncGroupsCompleted` 同一形态）。
            _effects.tryEmit(ReplaceRuleEffect.ShowMessage("成功导入 $count 条规则"))
        },
        transferPlatform = transferPlatform,
        uploadRepository = uploadRepository,
    )

    val importState = transfer.importState
    val events = transfer.events

    // ---------- 列表状态（原 BaseRuleViewModel 的职责，现在只服务本 Feature） ----------

    private val _searchKey = MutableStateFlow("")
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _isSearchMode = MutableStateFlow(false)
    private val _localItems = MutableStateFlow<List<ReplaceRuleItemUi>?>(null)

    // `by lazy` 是必需的：`rawDataFlow` 在下面才初始化，急切初始化会读到未完成的构造。
    private val itemsFlow: Flow<List<ReplaceRuleItemUi>> by lazy {
        combine(rawDataFlow, _searchKey, _localItems) { data, searchKey, local ->
            if (local != null && searchKey.isEmpty()) {
                local
            } else {
                filterRules(data, searchKey).map { it.toUiItem() }
            }
        }
    }

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

    /**
     * 两级 `combine`，与迁移前**同构**：`BaseRuleViewModel.uiState` 原本是 5 流合并
     * （items / selectedIds / isSearch / isUploading / importState），本类再与 `_bookState`
     * 合并成最终状态。这里只是把那两层并回了本类。
     *
     * ⚠️ 不能改成单次 6 参 `combine`：kotlinx-coroutines（1.11.0）的**类型化** `combine`
     * 重载最多只到 **5 个流**，第 6 个会落到 `(Array<T>) -> R` 的 vararg 重载。本处 6 个流
     * 类型互异（`List` / `Set` / `Boolean` / `Boolean` / `BaseImportUiState` / `BookSpecificState`），
     * `T` 只能被推成 `Any`，于是 6 参 transformer lambda 与 `Array<Any>` 对不上而编译失败。
     * 嵌套合并在保住逐流强类型的同时，发射语义与单次全量合并等价：仍是「所有源都至少发射
     * 过一次后才产出，之后任一源变化即重发最新组合」。
     */
    val uiState: StateFlow<ReplaceRuleUiState> by lazy {
        val baseState = combine(
            itemsFlow,
            _selectedIds,
            _isSearchMode,
            transfer.isUploading,
            transfer.importState,
        ) { items, selectedIds, isSearch, isUploading, importState ->
            ReplaceRuleUiState(
                items = items.toImmutableList(),
                selectedIds = selectedIds.toImmutableSet(),
                searchKey = _searchKey.value,
                sortMode = _sortMode.value,
                selectedGroup = _group.value,
                interaction = InteractionState(
                    isSearchMode = isSearch,
                    isUploading = isUploading || (importState is BaseImportUiState.Loading),
                    isLoading = false
                ),
            )
        }
        combine(baseState, _bookState) { state, bookState ->
            state.copy(
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
            initialValue = ReplaceRuleUiState(interaction = InteractionState(isLoading = true))
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
            ReplaceRuleIntent.UploadSelection -> transfer.upload(selectedRules())
            is ReplaceRuleIntent.ExportSelection -> transfer.export(intent.uri, selectedRules())
            is ReplaceRuleIntent.MoveItem -> moveItemInList(intent.from, intent.to)
            ReplaceRuleIntent.SaveSortOrder -> saveSortOrder()
            is ReplaceRuleIntent.DeleteRule -> delete(intent.rule)
            is ReplaceRuleIntent.SetRuleEnabled -> setEnabled(intent.id, intent.enabled)
            is ReplaceRuleIntent.CopyRule -> { /* not implemented for ReplaceRule */ }
            is ReplaceRuleIntent.ImportSource -> transfer.importSource(intent.text)
            ReplaceRuleIntent.CancelImport -> transfer.cancelImport()
            is ReplaceRuleIntent.ToggleImportSelection -> transfer.toggleImportSelection(intent.index)
            is ReplaceRuleIntent.ToggleImportAll -> transfer.toggleImportAll(intent.isSelected)
            is ReplaceRuleIntent.UpdateImportItem -> transfer.updateImportItem(intent.index, intent.rule)
            ReplaceRuleIntent.SaveImportedRules -> transfer.saveImportedRules()
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
                notifyRuleChanged()
            }
            ReplaceRuleIntent.DisableChineseConverter -> {
                viewModelScope.launch { readSettingsRepository.setChineseConverterType(0) }
                _bookState.update { it.copy(chineseConvertActive = false) }
            }
            ReplaceRuleIntent.DisableReSegment -> {
                readBookSession.setReSegment(false)
                readBookSession.loadContent(false)
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
    private val rawDataFlow: Flow<List<ReplaceRule>> =
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

    /**
     * 迁移前签名里的 `groupFilter` 来自 `BaseRuleViewModel._groupFilter`，本 VM 从不写它
     * （分组是在查询层用 `flowGroupSearch` 过滤的），故这里只留 `searchKey` 一个维度——
     * 与原实现 `searchKey.ifEmpty { groupFilter }` 在 `groupFilter == ""` 时逐字等价。
     */
    private fun filterRules(data: List<ReplaceRule>, searchKey: String): List<ReplaceRule> {
        return if (searchKey.isEmpty()) data
        else data.filter {
            it.name.contains(searchKey, ignoreCase = true)
                    || it.pattern.contains(searchKey, ignoreCase = true)
                    || it.replacement.contains(searchKey, ignoreCase = true)
                    || it.scope?.contains(searchKey, ignoreCase = true) == true
        }
    }

    private fun ReplaceRule.toUiItem() = ReplaceRuleItemUi(
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

    /** 当前选中项对应的领域模型；`UploadSelection` / `ExportSelection` 共用。 */
    private fun selectedRules(): List<ReplaceRule> {
        val state = uiState.value
        return state.items
            .filter { state.selectedIds.contains(it.id) }
            .map { it.toDomain() }
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
        viewModelScope.launch { replaceRuleSettings.setSortMode(mode) }
    }

    private fun saveSortOrder() {
        val currentLocal = _localItems.value ?: return
        viewModelScope.launch {
            repository.moveOrder(currentLocal.map { it.toDomain() }, _sortMode.value == "desc")
            _localItems.value = null
            notifyRuleChanged()
        }
    }

    private fun setEnabled(id: Long, enabled: Boolean) =
        viewModelScope.launch {
            repository.setEnabled(id, enabled)
            notifyRuleChanged()
        }

    private fun delete(rule: ReplaceRule) = viewModelScope.launch {
        repository.delete(rule)
        notifyRuleChanged()
    }

    fun enableSelectionByIds(ids: Set<Long>) = viewModelScope.launch {
        repository.enableByIds(ids)
        notifyRuleChanged()
    }

    fun disableSelectionByIds(ids: Set<Long>) =
        viewModelScope.launch {
            repository.disableByIds(ids)
            notifyRuleChanged()
        }

    fun delSelectionByIds(ids: Set<Long>) = viewModelScope.launch {
        repository.deleteByIds(ids)
        _selectedIds.update { it - ids }
        notifyRuleChanged()
    }

    private fun notifyRuleChanged() {
        changeNotifier.notifyChanged()
    }

    private fun selectAll() {
        setSelection(uiState.value.items.map { it.id }.toSet())
    }

    private fun invertSelection() {
        val state = uiState.value
        setSelection(state.items.map { it.id }.toSet() - state.selectedIds)
    }

    private fun addGroup(group: String) = viewModelScope.launch { repository.addGroup(group) }
    private fun delGroup(group: String) = viewModelScope.launch {
        repository.delGroup(group)
        notifyRuleChanged()
    }

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

    // ---------- 列表状态：搜索 / 选择 / 拖拽（原 BaseRuleViewModel 的职责） ----------

    private fun moveItemInList(from: Int, to: Int) {
        val currentList = uiState.value.items.toMutableList()
        if (from !in currentList.indices || to !in currentList.indices) return
        val item = currentList.removeAt(from)
        currentList.add(to, item)
        _localItems.value = currentList
    }

    private fun setSearchKey(key: String?) {
        _localItems.value = null
        _searchKey.value = key ?: ""
    }

    private fun setSearchMode(active: Boolean) {
        _isSearchMode.value = active
        if (!active) setSearchKey("")
    }

    private fun toggleSelection(id: Long) {
        _selectedIds.update { if (it.contains(id)) it - id else it + id }
    }

    private fun setSelection(ids: Set<Long>) {
        _selectedIds.value = ids
    }

    //region Book-specific methods

    fun emitOpenReplaceEditor(id: Long, pattern: String?) {
        _effects.tryEmit(ReplaceRuleEffect.OpenReplaceEditor(id, pattern))
    }

    private fun initBookData(bookUrl: String) {
        val snapshot = readBookSession.snapshot()
        if (snapshot != null && snapshot.bookUrl == bookUrl) {
            val chineseConvertActive = readSettingsRepository.currentSettings.chineseConverterType > 0
            _bookState.update {
                it.copy(
                    bookUrl = bookUrl,
                    replaceEnabled = snapshot.useReplaceRule,
                    effectiveRules = snapshot.effectiveReplaceRules.toImmutableList(),
                    chineseConvertActive = chineseConvertActive,
                    reSegmentActive = snapshot.reSegment,
                )
            }
        }
    }

    private fun toggleReplaceEnable() {
        val snapshot = readBookSession.snapshot() ?: return
        val enabled = !snapshot.useReplaceRule
        readBookSession.setUseReplaceRule(enabled)
        readBookSession.saveRead()
        _bookState.update { it.copy(replaceEnabled = enabled) }
    }

    private fun loadContentProcesses() {
        val snapshot = readBookSession.snapshot() ?: return
        val chapterIndex = snapshot.chapterIndex
        _bookState.update {
            it.copy(contentProcessState = it.contentProcessState.copy(isLoading = true, errorMessage = null))
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                bookContentProcessGateway.getForChapter(snapshot.bookUrl, chapterIndex)
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
        val anchor = JsonCodec.fromJsonObject(anchorJson, TextProcessAnchor::class)
            ?: return null
        val action = JsonCodec.fromJsonObject(actionJson, TextProcessAction::class)
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

/**
 * `ReplaceRule` 在导入/导出流程里的实体语义。
 *
 * **只搬了迁移前 `saveImportedRules` 里可达的那部分**：原实现还读取
 * `BaseImportUiState.Success` 的 `keepOriginalName` / `customGroup` / `isAddGroup` 三个批处理
 * 选项，但本 Feature 的界面上**没有任何入口能设置它们**（`BatchImportDialog` 未暴露，
 * 也从未有过对应 Intent；全仓只有 `BookSourceViewModel` 走自己的 `updateImportOptions`），
 * 因此那三段分支在迁移前就是死代码。若将来要给替换规则加这三个开关，需要同时扩展
 * `RuleEntitySpec`（让 `persist` 拿到导入选项）——那是一次可见的契约变更，不是这里的遗漏。
 */
private class ReplaceRuleTransferSpec(
    private val repository: ReplaceRuleRepository,
    private val legacyImportCompat: ReplaceRuleImportCompat,
    private val onImported: (Int) -> Unit,
) : RuleEntitySpec<ReplaceRule> {

    override suspend fun generateJson(entities: List<ReplaceRule>): String = JsonCodec.toJson(entities)

    /**
     * 标准格式优先在**共享层**解（[JsonCodec]），解不出才回落到平台侧的 [ReplaceRuleImportCompat]。
     *
     * 为什么要分两步：旧格式分支依赖 `com.jayway.jsonpath`（JVM 三方库），只能由平台提供；
     * 而标准格式（`ReplaceRule` 的 JSON）在共享层就能解。这样 desktop 侧即使没有兼容实现，
     * **标准 JSON 的导入仍然可用**，只有旧格式才会走不通。
     *
     * 与迁移前 `ReplaceAnalyzer` 的等价性：
     *   - 单对象：`jsonToReplaceRule` 也是「先 Gson 解，`pattern` 为空才走旧格式」，本方法与它
     *     逐步对应；标准分支返回的就是同一个 Gson 结果。
     *   - 数组：`jsonToReplaceRules` 是「逐条走单对象逻辑 + 按 `isValid()` 过滤 + 任一条失败即
     *     整体失败」。本方法的快路径只在**每条 `pattern` 都非空**时才返回（此时旧格式分支对任
     *     何一条都不会被触发），随后做同样的 `isValid()` 过滤；否则整体交回 `ReplaceAnalyzer`，
     *     连「全有或全无」的失败语义都不变。
     *
     * ⚠️ 已知差异（仅旧格式的畸形输入）：迁移前坏 JSON 抛的是 jsonpath 的英文解析异常，
     * 现在统一成中文「格式不正确」。失败语义（异常 → 导入状态 `Error`）不变。
     *
     * ⚠️ 已知差异（数组里含 JSON `null` 元素，如 `[{"pattern":"a"}, null]`）：迁移前 jsonpath
     * 对 `null` 条目直接抛错 ⇒ 整个导入失败；现在 [JsonCodec.decodeList] 的 `filterNotNull`
     * 会把它丢掉、导入成功。这是**宽松化**，只影响「数组里混进 null」这种畸形备份；
     * 其余数组输入两种实现等价（含「某条 `pattern` 为空 ⇒ 整体交回平台侧、失败语义全有或全无」）。
     */
    override fun parseImportRules(text: String): List<ReplaceRule> {
        return when {
            text.isJsonArray() -> parseRulesArray(text)
            text.isJsonObject() -> listOf(parseSingleRule(text))
            else -> throw Exception("格式不正确")
        }
    }

    private fun parseSingleRule(text: String): ReplaceRule {
        val standard = JsonCodec.fromJsonObject(text.trim(), ReplaceRule::class)
        if (standard != null && standard.pattern.isNotEmpty()) return standard
        return legacyImportCompat.parseRule(text)
    }

    private fun parseRulesArray(text: String): List<ReplaceRule> {
        val standard = JsonCodec.decodeList(text, ReplaceRule::class)
        if (standard != null && standard.all { it.pattern.isNotEmpty() }) {
            return standard.filter { it.isValid() }
        }
        return legacyImportCompat.parseRules(text)
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

    override suspend fun persist(entities: List<ReplaceRule>) {
        // 迁移前是 `if (rulesToSave.isNotEmpty()) { 落库; 置 Idle; 发提示 }`：空选择时既不落库
        // 也不重置导入状态。本方法同样只在非空时落库与提示；唯一的差别是
        // `RuleTransferUseCase.saveImportedRules` 无论如何都会把导入状态置回 `Idle`
        // ——而对话框的确认按钮只有勾选了条目才可用，用户可见行为不变。
        if (entities.isEmpty()) return
        entities.forEach { rule ->
            repository.insert(rule)
        }
        onImported(entities.size)
    }
}
