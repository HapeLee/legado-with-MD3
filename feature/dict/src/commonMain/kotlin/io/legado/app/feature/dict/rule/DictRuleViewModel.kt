package io.legado.app.feature.dict.rule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.core.platform.Clipboard
import io.legado.app.core.platform.JsonCodec
import io.legado.app.core.rules.RuleEntitySpec
import io.legado.app.core.rules.RuleTransferPlatform
import io.legado.app.core.rules.RuleTransferUseCase
import io.legado.app.data.entities.DictRule
import io.legado.app.data.repository.DictRuleRepository
import io.legado.app.data.repository.UploadRepository
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.list.InteractionState
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.isJsonObject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 词典规则的列表 / 导入 / 导出编排（M1-3z，本模块转 CMP）。
 *
 * **不再继承 `BaseRuleViewModel`**（那是个吃 `android.app.Application` 的 `AndroidViewModel`，
 * 也是本模块进不了 `commonMain` 的最后一道墙）。导入/导出/上传的无 UI 编排下沉到
 * [RuleTransferUseCase]，列表/搜索/选择这些纯 UI 状态留在本类。本类因此只剩
 * `androidx.lifecycle.ViewModel` 一个 Android 依赖，也不再持有 `Context` / `Uri`。
 *
 * 与迁移前逐字一致的部分：查询流（`repository.flowAll()`）、搜索匹配字段（`name`）、
 * `sortedBy { sortNumber }`、`InteractionState` 的 `isUploading || importState is Loading`
 * 合并、排序落库（`moveOrder`）、以及所有提示文案（含两处**硬编码中文**——迁移前就是字面量，
 * 不借迁移顺手改成资源，那会是可见行为变更）。
 *
 * 两处**有意**的收窄，写在这里免得下一个人以为是漏搬：
 *   1. `exportToUri(android.net.Uri, …)` → `transfer.export(String, …)`：`RuleTransferPlatform`
 *      的契约本来就是 `String`（`writeExport(targetUri: String, …)`），迁移前是把 `Uri`
 *      `parse` 出来又立刻 `toString()` 送进去绕了一圈。
 *   2. `GSON`（`:core:data` 的 JVM 门面，住 androidMain）→ [JsonCodec]。两者配置严格对齐
 *      （与 app 侧 `INITIAL_GSON` 逐字相同：`MapDeserializerDoubleAsIntFix` +
 *      `IntJsonDeserializer` + `StringJsonDeserializer` + `LONG_OR_DOUBLE` + prettyPrinting +
 *      disableHtmlEscaping），而 `GSON` 相对 `INITIAL_GSON` 只多注册 6 个 rule 类型的
 *      `JsonDeserializer` 加 `TxtTocRule` 一个，**`DictRule` 不在其中** ⇒ 解析/序列化行为不变。
 *      （这是本模块与 `txttocrules` 的关键差异：那边 `TxtTocRule` 在门面上有自定义
 *      deserializer，因此必须另立平台契约；本模块不需要。）
 */
class DictRuleViewModel(
    uploadRepository: UploadRepository,
    transferPlatform: RuleTransferPlatform,
    private val repository: DictRuleRepository,
    private val clipboard: Clipboard,
) : ViewModel() {

    private val _effects = MutableSharedFlow<DictRuleEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private val transfer = RuleTransferUseCase(
        scope = viewModelScope,
        spec = DictRuleTransferSpec(repository),
        transferPlatform = transferPlatform,
        uploadRepository = uploadRepository,
    )

    val importState = transfer.importState
    val events = transfer.events

    // ---------- 列表状态（原 BaseRuleViewModel 的职责，现在只服务本 Feature） ----------

    private val _searchKey = MutableStateFlow("")
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val _isSearchMode = MutableStateFlow(false)
    private val _localItems = MutableStateFlow<List<DictRuleItemUi>?>(null)

    private val rawDataFlow: Flow<List<DictRule>> = repository.flowAll()

    // `by lazy` 是必需的：`rawDataFlow` 在下面才初始化，急切初始化会读到未完成的构造。
    private val itemsFlow: Flow<List<DictRuleItemUi>> by lazy {
        combine(rawDataFlow, _searchKey, _localItems) { data, searchKey, local ->
            if (local != null && searchKey.isEmpty()) {
                local
            } else {
                filterRules(data, searchKey).map { it.toUiItem() }
            }
        }
    }

    /**
     * 迁移前 `uiState` 由 `BaseRuleViewModel` 用 5 流 `combine` 合成；这里**保持 5 流**，
     * 没有像 `replacerules` 那样出现第 6 个流，因此可以直接沿用单次类型化 `combine`
     * （kotlinx-coroutines 的类型化重载最多到 5 个流，超出会静默退到 `(Array<T>) -> R`）。
     */
    val uiState: StateFlow<DictRuleUiState> by lazy {
        combine(
            itemsFlow,
            _selectedIds,
            _isSearchMode,
            transfer.isUploading,
            transfer.importState,
        ) { items, selectedIds, isSearch, isUploading, importState ->
            composeUiState(items, selectedIds, isSearch, isUploading, importState)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DictRuleUiState(interaction = InteractionState(isLoading = true))
        )
    }

    fun onIntent(intent: DictRuleIntent) {
        when (intent) {
            is DictRuleIntent.SetSearchMode -> setSearchMode(intent.active)
            is DictRuleIntent.UpdateSearchQuery -> setSearchKey(intent.query)
            DictRuleIntent.ClearSelection -> setSelection(emptySet())
            DictRuleIntent.SelectAll -> selectAll()
            DictRuleIntent.InvertSelection -> invertSelection()
            is DictRuleIntent.SetSelection -> setSelection(intent.ids)
            is DictRuleIntent.ToggleSelection -> toggleSelection(intent.id)
            DictRuleIntent.EnableSelection -> {
                enableSelectionByIds(uiState.value.selectedIds)
                setSelection(emptySet())
            }
            DictRuleIntent.DisableSelection -> {
                disableSelectionByIds(uiState.value.selectedIds)
                setSelection(emptySet())
            }
            DictRuleIntent.DeleteSelection -> {
                delSelectionByIds(uiState.value.selectedIds)
                setSelection(emptySet())
            }
            DictRuleIntent.UploadSelection -> transfer.upload(selectedRules())
            is DictRuleIntent.ExportSelection -> transfer.export(intent.uri, selectedRules())
            is DictRuleIntent.MoveItem -> moveItemInList(intent.from, intent.to)
            DictRuleIntent.SaveSortOrder -> saveSortOrder()
            is DictRuleIntent.SaveRule -> {
                if (intent.isNew) {
                    insert(intent.rule)
                } else if (intent.originalName != null &&
                    intent.originalName != intent.rule.name
                ) {
                    replacePrimaryKey(intent.originalName, intent.rule)
                } else {
                    update(intent.rule)
                }
            }
            is DictRuleIntent.DeleteRule -> delete(intent.rule)
            is DictRuleIntent.SetRuleEnabled -> update(intent.rule.copy(enabled = intent.enabled))
            is DictRuleIntent.CopyRule -> copyRule(intent.rule)
            is DictRuleIntent.ImportSource -> transfer.importSource(intent.text)
            DictRuleIntent.CancelImport -> transfer.cancelImport()
            is DictRuleIntent.ToggleImportSelection -> transfer.toggleImportSelection(intent.index)
            is DictRuleIntent.ToggleImportAll -> transfer.toggleImportAll(intent.isSelected)
            is DictRuleIntent.UpdateImportItem -> transfer.updateImportItem(intent.index, intent.rule)
            DictRuleIntent.SaveImportedRules -> transfer.saveImportedRules()
        }
    }

    /**
     * 迁移前签名里的 `groupFilter` 来自 `BaseRuleViewModel._groupFilter`，本 VM 从不写它
     * （词典规则没有分组），故这里只留 `searchKey` 一个维度——与原实现
     * `groupFilter.ifEmpty { searchKey }` 在 `groupFilter == ""` 时逐字等价。
     */
    private fun filterRules(data: List<DictRule>, searchKey: String): List<DictRule> {
        val filtered = if (searchKey.isEmpty()) data
        else data.filter { it.name.contains(searchKey, ignoreCase = true) }
        return filtered.sortedBy { it.sortNumber }
    }

    private fun composeUiState(
        items: List<DictRuleItemUi>,
        selectedIds: Set<String>,
        isSearch: Boolean,
        isUploading: Boolean,
        importState: BaseImportUiState<DictRule>
    ): DictRuleUiState {
        return DictRuleUiState(
            items = items.toImmutableList(),
            selectedIds = selectedIds.toImmutableSet(),
            searchKey = _searchKey.value,
            interaction = InteractionState(
                isSearchMode = isSearch,
                isUploading = isUploading || (importState is BaseImportUiState.Loading),
                isLoading = false
            )
        )
    }

    private fun DictRule.toUiItem() = DictRuleItemUi(name, urlRule, showRule, enabled, this)

    /** 当前选中项对应的实体；`UploadSelection` / `ExportSelection` 共用。 */
    private fun selectedRules(): List<DictRule> {
        val state = uiState.value
        return state.items
            .filter { state.selectedIds.contains(it.id) }
            .map { it.rule }
    }

    private fun setSearchKey(key: String?) {
        _localItems.value = null
        _searchKey.value = key ?: ""
    }

    private fun setSearchMode(active: Boolean) {
        _isSearchMode.value = active
        if (!active) setSearchKey("")
    }

    private fun setSelection(ids: Set<String>) {
        _selectedIds.value = ids
    }

    private fun toggleSelection(id: String) {
        _selectedIds.update { if (it.contains(id)) it - id else it + id }
    }

    private fun moveItemInList(from: Int, to: Int) {
        val currentList = uiState.value.items.toMutableList()
        if (from !in currentList.indices || to !in currentList.indices) return
        val item = currentList.removeAt(from)
        currentList.add(to, item)
        _localItems.value = currentList
    }

    private fun saveSortOrder() {
        val currentLocal = _localItems.value ?: return
        viewModelScope.launch {
            repository.moveOrder(currentLocal.map { it.rule })
            _localItems.value = null
        }
    }

    private fun enableSelectionByIds(ids: Set<String>) {
        viewModelScope.launch { repository.enableByIds(ids) }
    }

    private fun disableSelectionByIds(ids: Set<String>) {
        viewModelScope.launch { repository.disableByIds(ids) }
    }

    private fun delSelectionByIds(ids: Set<String>) {
        viewModelScope.launch {
            repository.deleteByIds(ids)
            _selectedIds.update { it - ids }
        }
    }

    private fun selectAll() {
        setSelection(uiState.value.items.map { it.id }.toSet())
    }

    private fun invertSelection() {
        val state = uiState.value
        setSelection(state.items.map { it.id }.toSet() - state.selectedIds)
    }

    private fun update(vararg rule: DictRule) = viewModelScope.launch { repository.update(*rule) }
    private fun insert(vararg rule: DictRule) = viewModelScope.launch { repository.insert(*rule) }
    private fun replacePrimaryKey(oldName: String, rule: DictRule) =
        viewModelScope.launch { repository.replacePrimaryKey(oldName, rule) }

    private fun delete(vararg dictRule: DictRule) =
        viewModelScope.launch { repository.delete(*dictRule) }

    private fun copyRule(dictRule: DictRule) {
        clipboard.setText(JsonCodec.toJson(dictRule))
    }

    fun pasteRule(): DictRule? {
        val text = clipboard.getText()
        if (text.isNullOrBlank()) {
            _effects.tryEmit(DictRuleEffect.ShowMessage("剪贴板没有内容"))
            return null
        }
        return try {
            JsonCodec.fromJsonObject(text, DictRule::class) ?: throw Exception("格式不对")
        } catch (e: Exception) {
            _effects.tryEmit(DictRuleEffect.ShowMessage("格式不对"))
            null
        }
    }
}

/**
 * `DictRule` 的导入/导出语义（M1-3z）。
 *
 * 与 `TxtTocRule` 不同，本类**不需要平台契约**：`DictRule` 在 app 侧 `GSON` 门面上没有注册
 * 自定义 `JsonDeserializer`，实体本身也没有 `@SerializedName(alternate = …)` 的旧键名兼容
 * ⇒ [JsonCodec]（配置等于 `INITIAL_GSON`）与 `GSON` 对本类型逐字等价。
 *
 * 失败语义与迁移前 `DictRuleViewModel.parseImportRules` 一致：**抛异常**（`RuleTransferUseCase`
 * 依赖它把导入状态置成 `Error`）。`decodeList` / `fromJsonObject` 在解析失败时返回 `null`，
 * 故显式转成异常。
 */
private class DictRuleTransferSpec(
    private val repository: DictRuleRepository,
) : RuleEntitySpec<DictRule> {

    override suspend fun generateJson(entities: List<DictRule>): String = JsonCodec.toJson(entities)

    override fun parseImportRules(text: String): List<DictRule> {
        return when {
            text.isJsonArray() -> JsonCodec.decodeList(text, DictRule::class)
                ?: throw Exception("格式不正确")

            text.isJsonObject() -> listOf(
                JsonCodec.fromJsonObject(text, DictRule::class)
                    ?: throw Exception("格式不正确")
            )

            else -> throw Exception("格式不正确")
        }
    }

    override fun hasChanged(newRule: DictRule, oldRule: DictRule): Boolean {
        return newRule.name != oldRule.name
                || newRule.urlRule != oldRule.urlRule
                || newRule.showRule != oldRule.showRule
                || newRule.enabled != oldRule.enabled
    }

    override suspend fun findOldRule(newRule: DictRule): DictRule? =
        repository.findById(newRule.name)

    override suspend fun persist(entities: List<DictRule>) {
        repository.insert(*entities.toTypedArray())
    }
}
