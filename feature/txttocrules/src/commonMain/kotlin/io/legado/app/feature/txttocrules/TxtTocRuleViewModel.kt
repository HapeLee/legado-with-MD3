package io.legado.app.feature.txttocrules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.core.platform.Clipboard
import io.legado.app.core.platform.JsonCodec
import io.legado.app.core.rules.BuiltInRulesImporter
import io.legado.app.core.rules.RuleEntitySpec
import io.legado.app.core.rules.RuleTransferPlatform
import io.legado.app.core.rules.RuleTransferUseCase
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.data.repository.TxtTocRuleRepository
import io.legado.app.data.repository.UploadRepository
import io.legado.app.feature.txttocrules.res.Res
import io.legado.app.feature.txttocrules.res.clipboard_empty
import io.legado.app.feature.txttocrules.res.import_built_in_rules
import io.legado.app.feature.txttocrules.res.invalid_format
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.list.InteractionState
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.isJsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * TXT 目录规则的列表 / 导入 / 导出 / 排序编排。
 *
 * M1-3y（本模块转 CMP）：**不再继承 `BaseRuleViewModel`**（那是个吃 `android.app.Application`
 * 的 `AndroidViewModel`，也是本模块进不了 `commonMain` 的最后一道墙）。导入/导出/上传的无 UI
 * 编排下沉到 [RuleTransferUseCase]，列表/搜索/选择/排序这些纯 UI 状态留在本类。本类因此只剩
 * `androidx.lifecycle.ViewModel` 一个 Android 依赖，也不再持有 `Context` / `Uri`。
 *
 * 与迁移前逐字一致的部分：过滤（`name.contains(key, ignoreCase = true)` +
 * `sortedBy(serialNumber)`）、拖拽排序的本地暂存与 `saveOrder` 落库、选择集操作、
 * `InteractionState` 的 `isUploading || importState is Loading` 合并、存在性判重条件
 * （`name/chapterRule/volumeRule/enable`）、`findOldRule` 恒为 null（本 Feature 没有「同 id
 * 已存在」概念，与迁移前一致），以及所有提示文案。
 *
 * 三处**有意**的收窄，写在这里免得下一个人以为是漏搬：
 *   1. `exportToUri(android.net.Uri, …)` → `transfer.export(String, …)`：`RuleTransferPlatform`
 *      的契约本来就是 `String`（`writeExport(targetUri: String, …)`），迁移前是把 `Uri`
 *      `parse` 出来又立刻 `toString()` 送进去绕了一圈。
 *   2. `GSON`（`:core:data` 的 JVM 门面，住 androidMain）→ 导入解析走平台契约
 *      [TxtTocRuleImportCompat]（`TxtTocRule` 有旧键名 `rule` 的兼容 deserializer，共享层
 *      的 [JsonCodec] 不含它）；序列化仍用 [JsonCodec]，两者输出配置严格对齐。
 *   3. `context.getString(R.string.x)` → CMP 的 `getString(Res.string.x)`：VM 里不能再持有
 *      `Context`，但**文案仍需本地化**（原来是 `context.getString`，直接写字面量会让非简体
 *      用户看到中文，属可见回退）。`getString` 是 suspend，故这几处 effect 改在协程内发出。
 */
class TxtTocRuleViewModel(
    uploadRepository: UploadRepository,
    transferPlatform: RuleTransferPlatform,
    private val builtInRulesImporter: BuiltInRulesImporter,
    importCompat: TxtTocRuleImportCompat,
    private val repository: TxtTocRuleRepository,
    private val clipboard: Clipboard,
) : ViewModel() {

    private val _effects = MutableSharedFlow<TxtTocRuleEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private val transfer = RuleTransferUseCase(
        scope = viewModelScope,
        spec = TxtTocRuleTransferSpec(repository, importCompat),
        transferPlatform = transferPlatform,
        uploadRepository = uploadRepository,
    )

    val importState = transfer.importState
    val events = transfer.events

    // ---------- 列表状态（原 BaseRuleViewModel 的职责，现在只服务本 Feature） ----------

    private val _searchKey = MutableStateFlow("")
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _isSearchMode = MutableStateFlow(false)
    private val _localItems = MutableStateFlow<List<TxtTocRuleItemUi>?>(null)

    private val rawDataFlow: Flow<List<TxtTocRule>> = repository.flowAll()

    // `by lazy` 是必需的：`rawDataFlow` 在上面才初始化，急切初始化会读到未完成的构造。
    private val itemsFlow: Flow<List<TxtTocRuleItemUi>> by lazy {
        combine(rawDataFlow, _searchKey, _localItems) { data, searchKey, local ->
            if (local != null && searchKey.isEmpty()) {
                local
            } else {
                filterData(data, searchKey).map { it.toUiItem() }
            }
        }
    }

    /**
     * 与迁移前**同构**的单层合并：原 `BaseRuleViewModel.uiState` 就是 5 流合并
     * （items / selectedIds / isSearch / isUploading / importState），本 Feature 没有额外的
     * 维度（replacerules 才有「本书」那一层），所以保持单层。
     *
     * ⚠️ kotlinx-coroutines（1.11.0）的**类型化** `combine` 重载最多只到 **5 个流**，这里正好
     * 5 个——不要再加第 6 个，否则会静默落到 `(Array<T>) -> R` 重载并把 `T` 推成 `Any`。
     */
    val uiState: StateFlow<TxtTocRuleUiState> by lazy {
        combine(
            itemsFlow,
            _selectedIds,
            _isSearchMode,
            transfer.isUploading,
            transfer.importState,
        ) { items, selectedIds, isSearch, isUploading, importState ->
            TxtTocRuleUiState(
                items = items,
                selectedIds = selectedIds,
                searchKey = _searchKey.value,
                interaction = InteractionState(
                    isSearchMode = isSearch,
                    isUploading = isUploading || (importState is BaseImportUiState.Loading),
                    isLoading = false
                )
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TxtTocRuleUiState(interaction = InteractionState(isLoading = true))
        )
    }

    fun onIntent(intent: TxtTocRuleIntent) {
        when (intent) {
            is TxtTocRuleIntent.SetSearchMode -> setSearchMode(intent.active)
            is TxtTocRuleIntent.UpdateSearchQuery -> setSearchKey(intent.query)
            TxtTocRuleIntent.ClearSelection -> setSelection(emptySet())
            TxtTocRuleIntent.SelectAll -> selectAll()
            TxtTocRuleIntent.InvertSelection -> invertSelection()
            is TxtTocRuleIntent.SetSelection -> setSelection(intent.ids)
            is TxtTocRuleIntent.ToggleSelection -> toggleSelection(intent.id)
            TxtTocRuleIntent.EnableSelection -> {
                enableSelectionByIds(uiState.value.selectedIds)
                setSelection(emptySet())
            }
            TxtTocRuleIntent.DisableSelection -> {
                disableSelectionByIds(uiState.value.selectedIds)
                setSelection(emptySet())
            }
            TxtTocRuleIntent.DeleteSelection -> {
                delSelectionByIds(uiState.value.selectedIds)
                setSelection(emptySet())
            }
            TxtTocRuleIntent.UploadSelection -> transfer.upload(selectedRules())
            is TxtTocRuleIntent.ExportSelection -> transfer.export(intent.uri, selectedRules())
            is TxtTocRuleIntent.MoveItem -> moveItemInList(intent.from, intent.to)
            TxtTocRuleIntent.SaveSortOrder -> saveSortOrder()
            is TxtTocRuleIntent.SaveRule -> save(intent.rule, intent.isNew)
            is TxtTocRuleIntent.DeleteRule -> delete(intent.rule)
            is TxtTocRuleIntent.SetRuleEnabled -> update(intent.rule.copy(enable = intent.enabled))
            is TxtTocRuleIntent.CopyRule -> clipboard.setText(JsonCodec.toJson(intent.rule))
            is TxtTocRuleIntent.ImportSource -> transfer.importSource(intent.text)
            TxtTocRuleIntent.CancelImport -> transfer.cancelImport()
            is TxtTocRuleIntent.ToggleImportSelection -> transfer.toggleImportSelection(intent.index)
            is TxtTocRuleIntent.ToggleImportAll -> transfer.toggleImportAll(intent.isSelected)
            is TxtTocRuleIntent.UpdateImportItem ->
                transfer.updateImportItem(intent.index, intent.rule)

            TxtTocRuleIntent.SaveImportedRules -> transfer.saveImportedRules()
            TxtTocRuleIntent.ImportBuiltInRules -> importBuiltInRules()
        }
    }

    fun toggleSelection(id: Long) {
        _selectedIds.value = if (id in _selectedIds.value) {
            _selectedIds.value - id
        } else {
            _selectedIds.value + id
        }
    }

    fun setSelection(ids: Set<Long>) {
        _selectedIds.value = ids
    }

    fun setSearchMode(active: Boolean) {
        _isSearchMode.value = active
        if (!active) setSearchKey("")
    }

    fun setSearchKey(key: String?) {
        _localItems.value = null
        _searchKey.value = key ?: ""
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
            repository.saveOrder(currentLocal.map { it.rule })
            _localItems.value = null
        }
    }

    private fun save(rule: TxtTocRule, isNew: Boolean) {
        viewModelScope.launch {
            if (isNew) {
                repository.insert(rule)
            } else {
                repository.update(rule)
            }
        }
    }

    private fun update(vararg rules: TxtTocRule) = viewModelScope.launch { repository.update(*rules) }
    private fun delete(vararg rules: TxtTocRule) = viewModelScope.launch { repository.delete(*rules) }

    private fun enableSelectionByIds(ids: Set<Long>) =
        viewModelScope.launch { repository.enableByIds(ids, true) }

    private fun disableSelectionByIds(ids: Set<Long>) =
        viewModelScope.launch { repository.enableByIds(ids, false) }

    private fun delSelectionByIds(ids: Set<Long>) = viewModelScope.launch { repository.deleteByIds(ids) }

    private fun selectAll() {
        setSelection(uiState.value.items.map { it.id }.toSet())
    }

    private fun invertSelection() {
        val state = uiState.value
        setSelection(state.items.map { it.id }.toSet() - state.selectedIds)
    }

    /** 当前勾选的实体列表（导出/上传用；顺序与界面一致）。 */
    private fun selectedRules(): List<TxtTocRule> {
        val state = uiState.value
        return state.items.filter { state.selectedIds.contains(it.id) }.map { it.rule }
    }

    private fun filterData(data: List<TxtTocRule>, key: String): List<TxtTocRule> {
        val filtered = if (key.isEmpty()) data
        else data.filter { it.name.contains(key, ignoreCase = true) }
        return filtered.sortedBy { it.serialNumber }
    }

    private fun TxtTocRule.toUiItem() =
        TxtTocRuleItemUi(id, name, enable, this, example = example ?: "")

    private fun importBuiltInRules() {
        viewModelScope.launch(Dispatchers.IO) {
            builtInRulesImporter.importTxtTocRules()
            _effects.emit(TxtTocRuleEffect.ShowMessage(getString(Res.string.import_built_in_rules)))
        }
    }

    fun pasteRule(): TxtTocRule? {
        val text = clipboard.getText()
        if (text.isNullOrBlank()) {
            emitMessage(Res.string.clipboard_empty)
            return null
        }
        return try {
            // `JsonCodec.fromJsonObject` 解不出时返回 `null`（不是 `Result`）：这里显式转成异常，
            // 与迁移前 `GSON.fromJsonObject<TxtTocRule>(text).getOrThrow()` 的失败语义一致。
            JsonCodec.fromJsonObject(text, TxtTocRule::class) ?: throw Exception("格式不正确")
        } catch (e: Exception) {
            emitMessage(Res.string.invalid_format)
            null
        }
    }

    /**
     * 发出一次提示文案（[getString] 是 suspend，故在协程内解析；原 `context.getString` 是同步的，
     * 这里只是把「取文案」挪进协程，用户可见时机不变）。
     */
    private fun emitMessage(res: StringResource) {
        viewModelScope.launch {
            _effects.emit(TxtTocRuleEffect.ShowMessage(getString(res)))
        }
    }
}

/**
 * [TxtTocRule] 的 [RuleEntitySpec] 实现——只剩「怎么序列化 / 怎么判重 / 怎么找旧值 / 怎么落库」。
 *
 * `findOldRule` 恒为 `null`：迁移前 `TxtTocRuleViewModel.findOldRule` 就是这么写的，导入条目
 * 因此恒为 `ImportStatus.New`（没有「同 id 已存在」的分类）。这不是漏实现，是既有语义。
 *
 * `persist` 只在非空时落库（迁移前 `if (rulesToSave.isNotEmpty())` 的等价形态）；
 * 唯一的差别是 `RuleTransferUseCase.saveImportedRules` 无论如何都会把导入状态置回 `Idle`
 * ——而对话框的确认按钮只有勾选了条目才可用，用户可见行为不变。
 */
private class TxtTocRuleTransferSpec(
    private val repository: TxtTocRuleRepository,
    private val importCompat: TxtTocRuleImportCompat,
) : RuleEntitySpec<TxtTocRule> {

    override suspend fun generateJson(entities: List<TxtTocRule>): String = JsonCodec.toJson(entities)

    override fun parseImportRules(text: String): List<TxtTocRule> {
        return when {
            text.isJsonArray() -> importCompat.parseRules(text)
            text.isJsonObject() -> listOf(importCompat.parseRule(text))
            else -> throw Exception("格式不正确")
        }
    }

    override fun hasChanged(newRule: TxtTocRule, oldRule: TxtTocRule): Boolean {
        return newRule.name != oldRule.name ||
            newRule.chapterRule != oldRule.chapterRule ||
            newRule.volumeRule != oldRule.volumeRule ||
            newRule.enable != oldRule.enable
    }

    override suspend fun findOldRule(newRule: TxtTocRule): TxtTocRule? = null

    override suspend fun persist(entities: List<TxtTocRule>) {
        if (entities.isEmpty()) return
        repository.insert(*entities.toTypedArray())
    }
}
